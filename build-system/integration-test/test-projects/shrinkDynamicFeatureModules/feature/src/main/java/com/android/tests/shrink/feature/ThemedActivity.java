package com.android.tests.shrink.feature;

import android.app.Activity;
import android.os.Bundle;
import com.android.tests.shrink.feature.R;

public class ThemedActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.used_string);
    }
}
