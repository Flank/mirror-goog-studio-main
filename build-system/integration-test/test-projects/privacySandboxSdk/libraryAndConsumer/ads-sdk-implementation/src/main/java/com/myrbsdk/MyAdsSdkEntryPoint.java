package com.myrbsdk;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.RequiresApi;
import com.example.ads_sdk_implementation.R;
import java.util.concurrent.Executor;

@RequiresApi(api = 34)
public class MyAdsSdkEntryPoint extends SandboxedSdkProvider {

    private SandboxedSdkContext sandboxedSdkContext;
    @Override
    public void initSdk(SandboxedSdkContext sandboxedSdkContext, Bundle bundle,
                        Executor executor, InitSdkCallback initSdkCallback) {
        Log.i("SDK", "initSdk");
        this.sandboxedSdkContext = sandboxedSdkContext;
        executor.execute(() -> initSdkCallback.onInitSdkFinished(new Bundle()));
    }

    @Override
    public View getView(Context windowContext, Bundle bundle) {
        Log.i("SDK", "getView");

        LinearLayout layout = new LinearLayout(windowContext);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        layout.setBackgroundColor(sandboxedSdkContext.getResources().getColor(R.color.box_color));
        return layout;
    }

    @Override
    public void onDataReceived(Bundle bundle, DataReceivedCallback dataReceivedCallback) {
        Log.i("SDK", "onDataReceived");
    }
}

