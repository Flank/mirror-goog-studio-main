package com.example.app;

import android.test.ActivityInstrumentationTestCase;
import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.Test;

public class AppActivityTest extends ActivityInstrumentationTestCase<AppActivity> {
    public AppActivityTest(String pkg, Class<AppActivity> activityClass) {
        super(pkg, activityClass);
    }

    public AppActivityTest(String pkg, Class<AppActivity> activityClass, boolean initialTouchMode) {
        super(pkg, activityClass, initialTouchMode);
    }

    public void testOnCreate() throws Exception {
        AppActivity activity = getActivity();

        // test the generated test class is available
        Test test = new Test("test");
        // test the bytecode of the tested app is present
        App app = new App("app");
        // test the bytecode of the tested app's dependencies is present
        Lib lib = new Lib("lib");
    }
}
