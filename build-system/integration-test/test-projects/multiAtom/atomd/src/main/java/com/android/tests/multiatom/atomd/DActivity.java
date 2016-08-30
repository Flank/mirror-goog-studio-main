package com.android.tests.multiatom.atomd;

import android.app.Activity;
import android.os.Bundle;
import java.util.logging.Logger;
import com.android.tests.multiatom.atoma.AtomA;
import com.android.tests.multiatom.atomb.AtomB;
import com.android.tests.multiatom.base.Base;

public class DActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.tests.multiatom.base.R.layout.base_layout);

        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Base.someString());
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(AtomA.someString(this));
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(AtomB.someString(this));
        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(AtomD.someString(this));
    }
}
