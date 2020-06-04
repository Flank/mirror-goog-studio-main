package com.android.tests.shrink.feature;

import android.app.Activity;
import android.os.Bundle;
import com.android.tests.shrink.feature.R;

public class FeatureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.tests.shrink.R.layout.used_from_feature_1);
        System.out.println(R.raw.text);
        changeLayout();
    }

    public void changeLayout() {
        setContentView(R.layout.feat_layout_1);
    }

    public void unusedMethod() {
        setContentView(R.layout.feat_unused_layout);
    }
}
