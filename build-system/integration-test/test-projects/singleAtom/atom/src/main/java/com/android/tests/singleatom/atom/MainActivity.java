package com.android.tests.singleatom.atom;

import android.app.Activity;
import android.os.Bundle;
import java.util.logging.Logger;


public class MainActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.atom_main);

        Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).warning(Atom.someString());
    }
}
