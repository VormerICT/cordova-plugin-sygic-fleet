package com.vormer.sygicfleet;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.sygic.aura.ResourceManager;
import com.sygic.aura.embedded.IApiCallback;
import com.sygic.aura.utils.PermissionsUtils;
import com.sygic.sdk.api.Api;
import com.sygic.sdk.api.ApiNavigation;
import com.sygic.sdk.api.events.ApiEvents;
import com.sygic.sdk.api.model.GpsPosition;
import com.sygic.sdk.api.model.NaviVersion;
import com.sygic.sdk.api.model.RouteInfo;
import com.sygic.sdk.api.model.WayPoint;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SygicFleetPlugin extends CordovaPlugin
        implements IApiCallback, SygicFleetFragment.IApiCallbackProvider {

    private static final String TAG = "SygicFleetPlugin";
    private static final int PERMISSION_REQUEST = 6207;
    private static final int SYGIC_TIMEOUT_MS = 5000;
    private static final String FRAGMENT_TAG = "SygicFleetEmbeddedFragment";

    private final ExecutorService sygicExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout container;
    private SygicFleetFragment sygicFragment;
    private CallbackContext eventCallback;
    private CallbackContext pendingInitializeCallback;

    private volatile boolean appStarted = false;
    private volatile boolean serviceConnected = false;
    private volatile boolean initialized = false;
    private volatile boolean resourcesPrepared = false;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        Log.i(TAG, "pluginInitialize()");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        switch (action) {
            case "initialize":
                initialize(callbackContext);
                return true;
            case "show":
                show(args, callbackContext);
                return true;
            case "hide":
                hide(callbackContext);
                return true;
            case "updatePosition":
                updatePosition(args, callbackContext);
                return true;
            case "navigateToAddress":
                navigateToAddress(args.getString(0), callbackContext);
                return true;
            case "navigateToCoordinates":
                navigateToCoordinates(
                        args.getDouble(0),
                        args.getDouble(1),
                        args.optString(2, "Destination"),
                        callbackContext
                );
                return true;
            case "stopNavigation":
                stopNavigation(callbackContext);
                return true;
            case "getRouteInfo":
                getRouteInfo(callbackContext);
                return true;
            case "getActualGpsPosition":
                getActualGpsPosition(callbackContext);
                return true;
            case "getDeviceId":
                getDeviceId(callbackContext);
                return true;
            case "getApplicationVersion":
                getApplicationVersion(callbackContext);
                return true;
            case "isReady":
                callbackContext.success(appStarted ? 1 : 0);
                return true;
            case "registerEventListener":
                registerEventListener(callbackContext);
                return true;
            case "removeEventListener":
                removeEventListener(callbackContext);
                return true;
            default:
                return false;
        }
    }

    private void initialize(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                Log.i(TAG, "initialize()");

                if (resourcesPrepared) {
                    Log.i(TAG, "Sygic resources already prepared. fragmentCreated=" + initialized + ", ready=" + appStarted);
                    callbackContext.success(statusJson(initialized ? "already_initialized" : "resources_ready"));
                    return;
                }

                List<String> requiredPermissions =
                        PermissionsUtils.INSTANCE.getAllPermissions(activity);

                Log.i(TAG, "Sygic required permissions: " + requiredPermissions);

                List<String> missingPermissions = new ArrayList<>();

                for (String permission : requiredPermissions) {
                    if (ContextCompat.checkSelfPermission(activity, permission)
                            != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(permission);
                    }
                }

                Log.i(TAG, "Missing Sygic permissions: " + missingPermissions);

                if (!missingPermissions.isEmpty()) {
                    pendingInitializeCallback = callbackContext;

                    cordova.requestPermissions(
                            this,
                            PERMISSION_REQUEST,
                            missingPermissions.toArray(new String[0])
                    );
                    return;
                }

                initializeAfterPermission(callbackContext);

            } catch (Exception e) {
                Log.e(TAG, "Sygic initialize failed", e);
                callbackContext.error(
                        "Sygic initialize failed: "
                                + e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage()
                );
            }
        });
    }

    @Override
    public void onRequestPermissionResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) throws JSONException {

        if (requestCode != PERMISSION_REQUEST) {
            return;
        }

        boolean granted = grantResults.length > 0;

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        if (!granted) {
            Log.w(TAG, "One or more Sygic permissions denied");
            sendEvent(-1002, "SYGIC_PERMISSION_DENIED");

            if (pendingInitializeCallback != null) {
                pendingInitializeCallback.error(
                        "One or more required Sygic permissions were denied"
                );
                pendingInitializeCallback = null;
            }
            return;
        }

        Log.i(TAG, "All requested Sygic permissions granted");
        sendEvent(-1003, "SYGIC_PERMISSIONS_GRANTED");

        if (pendingInitializeCallback != null) {
            CallbackContext callback = pendingInitializeCallback;
            pendingInitializeCallback = null;
            initializeAfterPermission(callback);
        }
    }

    private void initializeAfterPermission(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                Log.i(TAG, "Checking Sygic resources");

                ResourceManager resourceManager = new ResourceManager(activity, null);

                if (resourceManager.shouldUpdateResources()) {
                    Log.i(TAG, "Sygic resources require update");

                    resourceManager.updateResources(new ResourceManager.OnResultListener() {
                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "Sygic resources updated");
                            resourcesPrepared = true;
                            callbackContext.success(statusJson("resources_ready"));
                        }

                        @Override
                        public void onError(int errorCode, String message) {
                            Log.e(TAG, "Sygic resource update failed: "
                                    + errorCode + " / " + message);

                            callbackContext.error(
                                    "Sygic resource update failed ("
                                            + errorCode
                                            + "): "
                                            + message
                            );
                        }
                    });
                } else {
                    Log.i(TAG, "Sygic resources already current");
                    resourcesPrepared = true;
                    callbackContext.success(statusJson("resources_ready"));
                }

            } catch (Exception e) {
                Log.e(TAG, "ResourceManager failed", e);
                callbackContext.error(
                        "Sygic ResourceManager failed: "
                                + e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage()
                );
            }
        });
    }

    private void createFragmentAtBounds(
            final int left,
            final int top,
            final int width,
            final int height,
            final CallbackContext callbackContext) {

        final Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                if (!resourcesPrepared) {
                    callbackContext.error("Sygic resources are not ready. Call Initialize first.");
                    return;
                }

                if (width < 32 || height < 32) {
                    callbackContext.error("Sygic element is too small to initialize safely: "
                            + width + "x" + height + " px. Use at least 32x32 px.");
                    return;
                }

                Log.i(TAG, "Creating Sygic fragment at " + left + "," + top
                        + " " + width + "x" + height);

                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root == null) {
                    callbackContext.error("Could not find Activity content view");
                    return;
                }

                if (container == null) {
                    container = new FrameLayout(activity);
                    container.setId(View.generateViewId());
                    container.setBackgroundColor(Color.BLACK);

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
                    lp.leftMargin = left;
                    lp.topMargin = top;

                    root.addView(container, lp);
                    container.setVisibility(View.VISIBLE);
                    container.bringToFront();
                    Log.i(TAG, "Sygic container created. id=" + container.getId());
                } else {
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
                    lp.leftMargin = left;
                    lp.topMargin = top;
                    container.setLayoutParams(lp);
                    container.setVisibility(View.VISIBLE);
                    container.bringToFront();
                }

                FragmentManager fm = activity.getFragmentManager();
                android.app.Fragment existing = fm.findFragmentByTag(FRAGMENT_TAG);

                if (existing instanceof SygicFleetFragment) {
                    Log.i(TAG, "Reusing existing Sygic fragment");
                    sygicFragment = (SygicFleetFragment) existing;
                } else {
                    Log.i(TAG, "Creating new SygicFleetFragment");
                    sygicFragment = new SygicFleetFragment();
                    FragmentTransaction tx = fm.beginTransaction();
                    tx.replace(container.getId(), sygicFragment, FRAGMENT_TAG);
                    tx.commitAllowingStateLoss();
                }

                sygicFragment.setCallbackProvider(this);
                sygicFragment.setAutoShutdownNavigation(false);
                initialized = true;

                Log.i(TAG, "Sygic fragment initialized at valid bounds; waiting for EVENT_APP_STARTED");
                callbackContext.success(statusJson("initializing_sygic"));

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Sygic fragment", e);
                callbackContext.error("Failed to initialize Sygic fragment: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public IApiCallback getSygicCallback() {
        return this;
    }

    private void show(JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        final int left = Math.max(0, args.getInt(0));
        final int top = Math.max(0, args.getInt(1));
        final int width = Math.max(1, args.getInt(2));
        final int height = Math.max(1, args.getInt(3));

        if (!resourcesPrepared) {
            callbackContext.error("Sygic resources are not ready. Call Initialize first.");
            return;
        }

        if (!initialized || container == null || sygicFragment == null) {
            createFragmentAtBounds(left, top, width, height, callbackContext);
            return;
        }

        cordova.getActivity().runOnUiThread(() -> {
            if (width < 32 || height < 32) {
                callbackContext.error("Sygic element is too small: " + width + "x" + height + " px");
                return;
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            lp.leftMargin = left;
            lp.topMargin = top;
            container.setLayoutParams(lp);
            container.setVisibility(View.VISIBLE);
            container.bringToFront();

            Log.i(TAG, "Showing Sygic: " + left + "," + top + " " + width + "x" + height);
            callbackContext.success();
        });
    }


    private void updatePosition(JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        final int left = args.getInt(0);
        final int top = args.getInt(1);
        final int width = Math.max(1, args.getInt(2));
        final int height = Math.max(1, args.getInt(3));

        if (container == null || !initialized) {
            callbackContext.success();
            return;
        }

        cordova.getActivity().runOnUiThread(() -> {
            if (width < 32 || height < 32) {
                callbackContext.success();
                return;
            }

            // left/top may legitimately be negative while the DOM element scrolls
            // partly outside the WebView. The Activity root clips the native view.
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            lp.leftMargin = left;
            lp.topMargin = top;
            container.setLayoutParams(lp);

            if (container.getVisibility() != View.VISIBLE) {
                container.setVisibility(View.VISIBLE);
            }
            callbackContext.success();
        });
    }

    private void hide(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (container != null) {
                // Keep the last valid dimensions. Resizing Aura to 1x1/10x10 can crash
                // its native font/resource sizing path (CResources::ResetSize).
                container.setVisibility(View.INVISIBLE);
                Log.i(TAG, "Sygic container hidden; valid dimensions preserved");
            }
            callbackContext.success();
        });
    }

    private void navigateToAddress(
            final String address,
            final CallbackContext callbackContext) {

        runSygicApi(callbackContext, () -> {
            ApiNavigation.navigateToAddress(
                    address,
                    false,
                    0,
                    SYGIC_TIMEOUT_MS
            );

            return new JSONObject().put("ok", true);
        });
    }

    private void navigateToCoordinates(
            final double latitude,
            final double longitude,
            final String name,
            final CallbackContext callbackContext) {

        runSygicApi(callbackContext, () -> {
            int lon = (int) Math.round(longitude * 100000.0d);
            int lat = (int) Math.round(latitude * 100000.0d);

            WayPoint destination =
                    new WayPoint(name, lon, lat);

            ApiNavigation.startNavigation(
                    destination,
                    0,
                    false,
                    SYGIC_TIMEOUT_MS
            );

            return new JSONObject()
                    .put("ok", true)
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                    .put("sygicLat", lat)
                    .put("sygicLon", lon);
        });
    }

    private void stopNavigation(final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            ApiNavigation.stopNavigation(0);
            return new JSONObject().put("ok", true);
        });
    }

    private void getRouteInfo(final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            RouteInfo info =
                    ApiNavigation.getRouteInfo(
                            false,
                            SYGIC_TIMEOUT_MS
                    );

            return new JSONObject()
                    .put("totalDistance", info.getTotalDistance())
                    .put("remainingDistance", info.getRemainingDistance())
                    .put("totalTime", info.getTotalTime())
                    .put("remainingTime", info.getRemainingTime())
                    .put("status", info.getStatus());
        });
    }

    private void getActualGpsPosition(final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            GpsPosition p =
                    ApiNavigation.getActualGpsPosition(
                            false,
                            SYGIC_TIMEOUT_MS
                    );

            return new JSONObject()
                    .put("latitude", p.getLatitude() / 100000.0d)
                    .put("longitude", p.getLongitude() / 100000.0d)
                    .put("sygicLatitude", p.getLatitude())
                    .put("sygicLongitude", p.getLongitude())
                    .put("altitude", p.getAltitude())
                    .put("speed", p.getSpeed())
                    .put("course", p.getCourse())
                    .put("satellites", p.getSatellites())
                    .put("mapIso", p.getMapIso());
        });
    }

    private void getDeviceId(final CallbackContext callbackContext) {
        runSygicApi(
                callbackContext,
                () -> new JSONObject().put(
                        "deviceId",
                        Api.getUniqueDeviceId(SYGIC_TIMEOUT_MS)
                )
        );
    }

    private void getApplicationVersion(final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            NaviVersion version =
                    Api.getApplicationVersion(SYGIC_TIMEOUT_MS);

            return new JSONObject().put(
                    "version",
                    version == null
                            ? JSONObject.NULL
                            : version.toString()
            );
        });
    }

    private void runSygicApi(
            final CallbackContext callbackContext,
            final JsonApiCall call) {

        if (!appStarted) {
            callbackContext.error(
                    "Sygic API is not ready. Wait for EVENT_APP_STARTED."
            );
            return;
        }

        sygicExecutor.submit(() -> {
            try {
                JSONObject result = call.run();
                callbackContext.success(result);
            } catch (Exception e) {
                Log.e(TAG, "Sygic API call failed", e);

                callbackContext.error(
                        e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage()
                );
            }
        });
    }

    private void registerEventListener(CallbackContext callbackContext) {
        Log.i(TAG, "Registering Cordova event listener");

        eventCallback = callbackContext;

        PluginResult result =
                new PluginResult(PluginResult.Status.NO_RESULT);

        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);

        if (appStarted) {
            sendEvent(
                    ApiEvents.EVENT_APP_STARTED,
                    "ALREADY_STARTED"
            );
        } else if (serviceConnected) {
            sendEvent(
                    -1000,
                    "SERVICE_ALREADY_CONNECTED"
            );
        }
    }

    private void removeEventListener(CallbackContext callbackContext) {
        if (eventCallback != null) {
            PluginResult end =
                    new PluginResult(PluginResult.Status.NO_RESULT);

            end.setKeepCallback(false);
            eventCallback.sendPluginResult(end);
            eventCallback = null;
        }

        callbackContext.success();
    }

    @Override
    public void onEvent(int event, String data) {
        Log.i(
                TAG,
                "Sygic onEvent: event="
                        + event
                        + ", name="
                        + eventName(event)
                        + ", data="
                        + data
        );

        if (event == ApiEvents.EVENT_APP_STARTED) {
            appStarted = true;
            Log.i(TAG, "*** SYGIC EVENT_APP_STARTED ***");
        } else if (event == ApiEvents.EVENT_APP_EXIT) {
            appStarted = false;
            Log.i(TAG, "*** SYGIC EVENT_APP_EXIT ***");
        }

        sendEvent(event, data);
    }

    @Override
    public void onServiceConnected() {
        Log.i(TAG, "*** SYGIC SERVICE CONNECTED ***");

        serviceConnected = true;

        sendEvent(
                -1000,
                "SERVICE_CONNECTED"
        );
    }

    @Override
    public void onServiceDisconnected() {
        Log.i(TAG, "*** SYGIC SERVICE DISCONNECTED ***");

        serviceConnected = false;
        appStarted = false;

        sendEvent(
                -1001,
                "SERVICE_DISCONNECTED"
        );
    }

    private void sendEvent(int event, String data) {
        CallbackContext cb = eventCallback;

        if (cb == null) {
            Log.d(
                    TAG,
                    "No Cordova event listener. Event="
                            + eventName(event)
            );
            return;
        }

        try {
            JSONObject json =
                    new JSONObject()
                            .put("event", event)
                            .put(
                                    "data",
                                    data == null
                                            ? JSONObject.NULL
                                            : data
                            )
                            .put("name", eventName(event))
                            .put("ready", appStarted)
                            .put("serviceConnected", serviceConnected);

            PluginResult result =
                    new PluginResult(
                            PluginResult.Status.OK,
                            json
                    );

            result.setKeepCallback(true);
            cb.sendPluginResult(result);

        } catch (JSONException e) {
            Log.e(
                    TAG,
                    "Failed to create event JSON",
                    e
            );
        }
    }

    private String eventName(int event) {
        if (event == ApiEvents.EVENT_APP_STARTED) {
            return "EVENT_APP_STARTED";
        }

        if (event == ApiEvents.EVENT_APP_EXIT) {
            return "EVENT_APP_EXIT";
        }

        if (event == ApiEvents.EVENT_ROUTE_COMPUTED) {
            return "EVENT_ROUTE_COMPUTED";
        }

        if (event == ApiEvents.EVENT_ROUTE_FINISH) {
            return "EVENT_ROUTE_FINISH";
        }

        if (event == ApiEvents.EVENT_OFF_ROUTE) {
            return "EVENT_OFF_ROUTE";
        }

        if (event == ApiEvents.EVENT_SPEED_EXCEEDING) {
            return "EVENT_SPEED_EXCEEDING";
        }

        if (event == ApiEvents.EVENT_SPEED_LIMIT_CHANGED) {
            return "EVENT_SPEED_LIMIT_CHANGED";
        }

        if (event == -1000) {
            return "SERVICE_CONNECTED";
        }

        if (event == -1001) {
            return "SERVICE_DISCONNECTED";
        }

        if (event == -1002) {
            return "SYGIC_PERMISSION_DENIED";
        }

        if (event == -1003) {
            return "SYGIC_PERMISSIONS_GRANTED";
        }

        return "EVENT_" + event;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy()");

        try {
            if (sygicFragment != null) {
                sygicFragment.setCallback(null);
            }
        } catch (Exception e) {
            Log.w(
                    TAG,
                    "Could not remove Sygic callback",
                    e
            );
        }

        eventCallback = null;
        pendingInitializeCallback = null;

        appStarted = false;
        serviceConnected = false;
        initialized = false;

        sygicExecutor.shutdownNow();

        super.onDestroy();
    }

    private JSONObject statusJson(String state) {
        JSONObject result = new JSONObject();

        try {
            result.put("state", state);
            result.put("initialized", initialized);
            result.put("ready", appStarted);
            result.put("serviceConnected", serviceConnected);
        } catch (JSONException ignored) {
        }

        return result;
    }

    private interface JsonApiCall {
        JSONObject run() throws Exception;
    }
}
