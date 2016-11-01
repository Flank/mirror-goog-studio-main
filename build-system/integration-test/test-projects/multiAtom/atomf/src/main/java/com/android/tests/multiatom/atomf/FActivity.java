package com.android.tests.multiatom.atomf;

import android.app.Activity;
import android.os.Bundle;
import com.android.tests.multiatom.atomc.AtomC;
import com.android.tests.multiatom.base.Base;
import java.util.logging.Logger;

public class FActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.tests.multiatom.R.layout.base_layout);

        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Base.someString());
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(AtomC.someString(this));
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(AtomF.someString(this));
    }
}
