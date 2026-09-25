package com.vormer.sygicfleet;

import android.util.Log;

import com.sygic.aura.embedded.IApiCallback;
import com.sygic.aura.embedded.SygicFragment;

public class SygicFleetFragment extends SygicFragment {
    private static final String TAG = "SygicFleetFragment";

    public interface IApiCallbackProvider {
        IApiCallback getSygicCallback();
    }

    private IApiCallbackProvider callbackProvider;

    public void setCallbackProvider(IApiCallbackProvider provider) {
        this.callbackProvider = provider;
    }

@Override
public void onResume() {
    Log.i(TAG, "*** onResume() ***");

    Log.i(TAG, "*** enabling auto shutdown ***");
    setAutoShutdownNavigation(true);

    Log.i(TAG, "*** calling startNavi() ***");
    startNavi();

    if (callbackProvider != null) {
        Log.i(TAG, "*** installing Sygic callback ***");
        setCallback(callbackProvider.getSygicCallback());
    } else {
        Log.e(TAG, "*** callbackProvider IS NULL ***");
    }

    Log.i(TAG, "*** calling super.onResume() ***");
    super.onResume();

    Log.i(TAG, "*** onResume() finished ***");
}


    @Override
public void onPause() {
    Log.i(TAG, "*** onPause() ***");
    super.onPause();
}

@Override
public void onStop() {
    Log.i(TAG, "*** onStop() ***");
    super.onStop();
}

@Override
public void onDestroyView() {
    Log.i(TAG, "*** onDestroyView() ***");
    super.onDestroyView();
}

@Override
public void onDestroy() {
    Log.i(TAG, "*** onDestroy() ***");
    super.onDestroy();
}

@Override
public void onDetach() {
    Log.i(TAG, "*** onDetach() ***");
    super.onDetach();
}
}
