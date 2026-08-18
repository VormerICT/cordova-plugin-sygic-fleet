package com.vormer.sygicfleet;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.sygic.aura.ResourceManager;
import com.sygic.aura.embedded.IApiCallback;
import com.sygic.aura.embedded.SygicFragment;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SygicFleetPlugin extends CordovaPlugin implements IApiCallback {

    private static final int PERMISSION_REQUEST = 6207;
    private static final int SYGIC_TIMEOUT_MS = 5000;
    private static final String FRAGMENT_TAG = "SygicFleetEmbeddedFragment";

    private final ExecutorService sygicExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout container;
    private SygicFragment sygicFragment;
    private CallbackContext eventCallback;

    private volatile boolean appStarted = false;
    private volatile boolean serviceConnected = false;
    private volatile boolean initialized = false;
    private CallbackContext pendingInitializeCallback;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
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
            if (initialized) {
                callbackContext.success(statusJson("already_initialized"));
                return;
            }

            if (!hasLocationPermission()) {
                pendingInitializeCallback = callbackContext;
                cordova.requestPermissions(
                        this,
                        PERMISSION_REQUEST,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}
                );
                return;
            }

            initializeAfterPermission(callbackContext);
        });
    }

    private void initializeAfterPermission(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();
        activity.runOnUiThread(() -> {
            ResourceManager resourceManager = new ResourceManager(activity, null);
            if (resourceManager.shouldUpdateResources()) {
                resourceManager.updateResources(new ResourceManager.OnResultListener() {
                    @Override
                    public void onSuccess() {
                        createFragment(callbackContext);
                    }

                    @Override
                    public void onError(int errorCode, String message) {
                        callbackContext.error("Sygic resource update failed (" + errorCode + "): " + message);
                    }
                });
            } else {
                createFragment(callbackContext);
            }
        });
    }


    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST) return;
        CallbackContext cb = pendingInitializeCallback;
        pendingInitializeCallback = null;
        if (cb == null) return;

        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (granted) {
            initializeAfterPermission(cb);
        } else {
            cb.error("Location permission is required by Sygic navigation");
        }
    }

    private void createFragment(final CallbackContext callbackContext) {
        final Activity activity = cordova.getActivity();
        activity.runOnUiThread(() -> {
            try {
                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root == null) {
                    callbackContext.error("Could not find Activity content view");
                    return;
                }

                if (container == null) {
                    container = new FrameLayout(activity);
                    container.setId(View.generateViewId());
                    container.setBackgroundColor(Color.BLACK);
                    container.setVisibility(View.GONE);

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
                    lp.leftMargin = 0;
                    lp.topMargin = 0;
                    root.addView(container, lp);
                    container.bringToFront();
                }

                FragmentManager fm = activity.getFragmentManager();
                android.app.Fragment existing = fm.findFragmentByTag(FRAGMENT_TAG);

                if (existing instanceof SygicFragment) {
                    sygicFragment = (SygicFragment) existing;
                } else {
                    sygicFragment = new SygicFragment();
                    FragmentTransaction tx = fm.beginTransaction();
                    tx.replace(container.getId(), sygicFragment, FRAGMENT_TAG);
                    tx.commitAllowingStateLoss();
                    fm.executePendingTransactions();
                }

                sygicFragment.setCallback(this);
                sygicFragment.setAutoShutdownNavigation(false);
                sygicFragment.startNavi();

                initialized = true;
                callbackContext.success(statusJson("initializing_sygic"));
            } catch (Exception e) {
                callbackContext.error("Failed to initialize Sygic fragment: " + e.getMessage());
            }
        });
    }

    private void show(JSONArray args, CallbackContext callbackContext) throws JSONException {
        final int left = Math.max(0, args.getInt(0));
        final int top = Math.max(0, args.getInt(1));
        final int width = Math.max(1, args.getInt(2));
        final int height = Math.max(1, args.getInt(3));

        cordova.getActivity().runOnUiThread(() -> {
            if (container == null) {
                callbackContext.error("Sygic is not initialized");
                return;
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
            lp.leftMargin = left;
            lp.topMargin = top;
            container.setLayoutParams(lp);
            container.setVisibility(View.VISIBLE);
            container.bringToFront();
            callbackContext.success();
        });
    }

    private void hide(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (container != null) container.setVisibility(View.GONE);
            callbackContext.success();
        });
    }

    private void navigateToAddress(final String address, final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            ApiNavigation.navigateToAddress(address, false, 0, SYGIC_TIMEOUT_MS);
            return new JSONObject().put("ok", true);
        });
    }

    private void navigateToCoordinates(final double latitude,
                                       final double longitude,
                                       final String name,
                                       final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            // Sygic Professional Navigation coordinates are WGS84 * 100000.
            // WayPoint constructor order is (name/address, longitude/X, latitude/Y).
            int lon = (int) Math.round(longitude * 100000.0d);
            int lat = (int) Math.round(latitude * 100000.0d);
            WayPoint destination = new WayPoint(name, lon, lat);
            ApiNavigation.startNavigation(destination, 0, false, SYGIC_TIMEOUT_MS);

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
            RouteInfo info = ApiNavigation.getRouteInfo(false, SYGIC_TIMEOUT_MS);
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
            GpsPosition p = ApiNavigation.getActualGpsPosition(false, SYGIC_TIMEOUT_MS);
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
        runSygicApi(callbackContext, () -> new JSONObject()
                .put("deviceId", Api.getUniqueDeviceId(SYGIC_TIMEOUT_MS)));
    }

    private void getApplicationVersion(final CallbackContext callbackContext) {
        runSygicApi(callbackContext, () -> {
            NaviVersion v = Api.getApplicationVersion(SYGIC_TIMEOUT_MS);
            return new JSONObject().put("version", v == null ? JSONObject.NULL : v.toString());
        });
    }

    private void runSygicApi(final CallbackContext callbackContext, final JsonApiCall call) {
        if (!appStarted) {
            callbackContext.error("Sygic API is not ready. Wait for EVENT_APP_STARTED.");
            return;
        }

        sygicExecutor.submit(() -> {
            try {
                JSONObject result = call.run();
                callbackContext.success(result);
            } catch (Exception e) {
                callbackContext.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void registerEventListener(CallbackContext callbackContext) {
        eventCallback = callbackContext;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void removeEventListener(CallbackContext callbackContext) {
        if (eventCallback != null) {
            PluginResult end = new PluginResult(PluginResult.Status.NO_RESULT);
            end.setKeepCallback(false);
            eventCallback.sendPluginResult(end);
            eventCallback = null;
        }
        callbackContext.success();
    }

    @Override
    public void onEvent(int event, String data) {
        if (event == ApiEvents.EVENT_APP_STARTED) {
            appStarted = true;
        } else if (event == ApiEvents.EVENT_APP_EXIT) {
            appStarted = false;
        }

        sendEvent(event, data);
    }

    @Override
    public void onServiceConnected() {
        serviceConnected = true;
        sendEvent(-1000, "SERVICE_CONNECTED");
    }

    @Override
    public void onServiceDisconnected() {
        serviceConnected = false;
        appStarted = false;
        sendEvent(-1001, "SERVICE_DISCONNECTED");
    }

    private void sendEvent(int event, String data) {
        CallbackContext cb = eventCallback;
        if (cb == null) return;

        try {
            JSONObject json = new JSONObject()
                    .put("event", event)
                    .put("data", data == null ? JSONObject.NULL : data)
                    .put("name", eventName(event))
                    .put("ready", appStarted)
                    .put("serviceConnected", serviceConnected);

            PluginResult result = new PluginResult(PluginResult.Status.OK, json);
            result.setKeepCallback(true);
            cb.sendPluginResult(result);
        } catch (JSONException ignored) {
        }
    }

    private String eventName(int event) {
        if (event == ApiEvents.EVENT_APP_STARTED) return "EVENT_APP_STARTED";
        if (event == ApiEvents.EVENT_APP_EXIT) return "EVENT_APP_EXIT";
        if (event == ApiEvents.EVENT_ROUTE_COMPUTED) return "EVENT_ROUTE_COMPUTED";
        if (event == ApiEvents.EVENT_ROUTE_FINISH) return "EVENT_ROUTE_FINISH";
        if (event == ApiEvents.EVENT_OFF_ROUTE) return "EVENT_OFF_ROUTE";
        if (event == ApiEvents.EVENT_SPEED_EXCEEDING) return "EVENT_SPEED_EXCEEDING";
        if (event == ApiEvents.EVENT_SPEED_LIMIT_CHANGED) return "EVENT_SPEED_LIMIT_CHANGED";
        if (event == -1000) return "SERVICE_CONNECTED";
        if (event == -1001) return "SERVICE_DISCONNECTED";
        return "EVENT_" + event;
    }

    private boolean hasLocationPermission() {
        return cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != PERMISSION_REQUEST) return;

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                sendEvent(-1002, "LOCATION_PERMISSION_DENIED");
                return;
            }
        }
        sendEvent(-1003, "LOCATION_PERMISSION_GRANTED");
    }

    @Override
    public void onDestroy() {
        try {
            if (sygicFragment != null) {
                sygicFragment.setCallback(null);
            }
        } catch (Exception ignored) {
        }

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
