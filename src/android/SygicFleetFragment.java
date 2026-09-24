package com.vormer.sygicfleet;

import com.sygic.aura.embedded.SygicFragment;

public class SygicFleetFragment extends SygicFragment {

    private IApiCallbackProvider callbackProvider;

    public interface IApiCallbackProvider {
        com.sygic.aura.embedded.IApiCallback getSygicCallback();
    }

    public void setCallbackProvider(IApiCallbackProvider provider) {
        this.callbackProvider = provider;
    }

    @Override
    public void onResume() {
        startNavi();

        if (callbackProvider != null) {
            setCallback(callbackProvider.getSygicCallback());
        }

        super.onResume();
    }
}
