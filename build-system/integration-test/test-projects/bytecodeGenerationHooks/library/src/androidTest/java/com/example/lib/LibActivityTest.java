package com.example.lib;

import android.test.ActivityInstrumentationTestCase;
import com.example.bytecode.Lib;
import com.example.bytecode.Test;

public class LibActivityTest extends ActivityInstrumentationTestCase<LibActivity> {
    public LibActivityTest(String pkg, Class<LibActivity> activityClass) {
        super(pkg, activityClass);
    }

    public LibActivityTest(String pkg, Class<LibActivity> activityClass, boolean initialTouchMode) {
        super(pkg, activityClass, initialTouchMode);
    }

    public void testOnCreate() throws Exception {
        LibActivity activity = getActivity();

        // test the generated test class is available
        Test test = new Test("test");
        // test the bytecode of the tested lib is present
        Lib lib = new Lib("lib");
    }
}
