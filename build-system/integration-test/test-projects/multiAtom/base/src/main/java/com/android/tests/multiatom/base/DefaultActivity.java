package com.android.tests.multiatom.base;

import android.app.Activity;
import android.os.Bundle;
import java.util.logging.Logger;


public class DefaultActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_layout);

        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Base.someString());
    }
}
