package com.android.tests.shrink.feature;

import android.app.Activity;
import android.os.Bundle;
import com.android.tests.shrink.feature.R;

public class UnusedFeatureActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feat_unused_layout);
    }
}
