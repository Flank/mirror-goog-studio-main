/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.tools.lint.detector.api.TextFormat.TEXT;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

public class AppCompatCallDetectorTest extends AbstractCheckTest {
    public void testArguments() throws Exception {
        assertEquals(""
                + "src/test/pkg/AppCompatTest.java:5: Warning: Should use getSupportActionBar instead of getActionBar name [AppCompatMethod]\n"
                + "        getActionBar();                                     // ERROR\n"
                + "        ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:8: Warning: Should use startSupportActionMode instead of startActionMode name [AppCompatMethod]\n"
                + "        startActionMode(null);                              // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:11: Warning: Should use supportRequestWindowFeature instead of requestWindowFeature name [AppCompatMethod]\n"
                + "        requestWindowFeature(0);                            // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:14: Warning: Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name [AppCompatMethod]\n"
                + "        setProgressBarVisibility(true);                     // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:15: Warning: Should use setSupportProgressBarIndeterminate instead of setProgressBarIndeterminate name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminate(true);                  // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/AppCompatTest.java:16: Warning: Should use setSupportProgressBarIndeterminateVisibility instead of setProgressBarIndeterminateVisibility name [AppCompatMethod]\n"
                + "        setProgressBarIndeterminateVisibility(true);        // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 6 warnings\n",
        lintProject(
                mAppCompatJar,
                mAppCompatTest,
                mIntermediateActivity,
                // Stubs just to be able to do type resolution without needing the full appcompat jar
                mActionBarActivity,
                mActionMode
        ));
    }

    public void testNoWarningsWithoutAppCompat() throws Exception {
        assertEquals("No warnings.",
            lintProject(
                    mAppCompatTest,
                    mIntermediateActivity,
                    mActionBarActivity,
                    mActionMode
            ));
    }

    public void testNoCallWarningsInPreferenceActivitySubclass() throws Exception {
        // https://code.google.com/p/android/issues/detail?id=75700
        //noinspection all // Sample code
        assertEquals("No warnings.",
            lintProject(
                    java(""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.annotation.TargetApi;\n"
                            + "import android.os.Build;\n"
                            + "import android.os.Bundle;\n"
                            + "import android.preference.PreferenceActivity;\n"
                            + "\n"
                            + "public class AppCompatPrefTest extends PreferenceActivity {\n"
                            + "    @TargetApi(Build.VERSION_CODES.HONEYCOMB)\n"
                            + "    @Override\n"
                            + "    protected void onCreate(Bundle savedInstanceState) {\n"
                            + "        super.onCreate(savedInstanceState);\n"
                            + "        getActionBar(); // Should not generate a warning\n"
                            + "    }\n"
                            + "}\n"),
                    mAppCompatJar,
                    mIntermediateActivity,
                    // Stubs just to be able to do type resolution without needing the full appcompat jar
                    mActionBarActivity,
                    mActionMode
            ));
    }

    public void testGetOldCall() throws Exception {
        assertEquals("setProgressBarVisibility", AppCompatCallDetector.getOldCall(
            "Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name",
                TEXT));
        assertEquals("getActionBar", AppCompatCallDetector.getOldCall(
                "Should use getSupportActionBar instead of getActionBar name", TEXT));
        assertNull(AppCompatCallDetector.getOldCall("No match", TEXT));
        assertEquals("setProgressBarVisibility", AppCompatCallDetector.getOldCall(
                "Should use `setSupportProgressBarVisibility` instead of `setProgressBarVisibility` name",
                RAW));
    }

    public void testGetNewCall() throws Exception {
        assertEquals("setSupportProgressBarVisibility", AppCompatCallDetector.getNewCall(
                "Should use setSupportProgressBarVisibility instead of setProgressBarVisibility name",
                TEXT));
        assertEquals("getSupportActionBar", AppCompatCallDetector.getNewCall(
                "Should use getSupportActionBar instead of getActionBar name", TEXT));
        assertEquals("getSupportActionBar", AppCompatCallDetector.getNewCall(
                "Should use `getSupportActionBar` instead of `getActionBar` name", RAW));
        assertNull(AppCompatCallDetector.getNewCall("No match", TEXT));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @NonNull Location location, @NonNull String message) {
        assertNotNull(message, AppCompatCallDetector.getOldCall(message, TEXT));
        assertNotNull(message, AppCompatCallDetector.getNewCall(message, TEXT));
    }

    @Override
    protected Detector getDetector() {
        return new AppCompatCallDetector();
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mActionBarActivity = java(""
            + "package android.support.v7.app;\n"
            + "\n"
            + "import android.app.ActionBar;\n"
            + "import android.app.Activity;\n"
            + "import android.support.v7.view.ActionMode;\n"
            + "/**\n"
            + " * Just a dumb stub for unit test\n"
            + " */\n"
            + "public class ActionBarActivity extends Activity {\n"
            + "    protected ActionBar getSupportActionBar() {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    public ActionMode startSupportActionMode(ActionMode.Callback callback) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    public boolean supportRequestWindowFeature(int featureId) {\n"
            + "        return true;\n"
            + "    }\n"
            + "\n"
            + "    public void setSupportProgressBarVisibility(boolean visible) {\n"
            + "    }\n"
            + "\n"
            + "    public void setSupportProgressBarIndeterminateVisibility(boolean visible) {\n"
            + "    }\n"
            + "\n"
            + "    public void setSupportProgressBarIndeterminate(boolean indeterminate) {\n"
            + "    }\n"
            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mActionMode = java(""
            + "package android.support.v7.view;\n"
            + "\n"
            + "/**\n"
            + " * Just a unit testing stub\n"
            + " */\n"
            + "public class ActionMode {\n"
            + "    public interface Callback {\n"
            + "    }\n"
            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mAppCompatTest = java(""
            + "package test.pkg;\n"
            + "\n"
            + "public class AppCompatTest extends IntermediateActivity {\n"
            + "    public void test() {\n"
            + "        getActionBar();                                     // ERROR\n"
            + "        getSupportActionBar();                              // OK\n"
            + "\n"
            + "        startActionMode(null);                              // ERROR\n"
            + "        startSupportActionMode(null);                       // OK\n"
            + "\n"
            + "        requestWindowFeature(0);                            // ERROR\n"
            + "        supportRequestWindowFeature(0);                     // OK\n"
            + "\n"
            + "        setProgressBarVisibility(true);                     // ERROR\n"
            + "        setProgressBarIndeterminate(true);                  // ERROR\n"
            + "        setProgressBarIndeterminateVisibility(true);        // ERROR\n"
            + "\n"
            + "        setSupportProgressBarVisibility(true);              // OK\n"
            + "        setSupportProgressBarIndeterminate(true);           // OK\n"
            + "        setSupportProgressBarIndeterminateVisibility(true); // OK\n"
            + "    }\n"
            + "}\n");

    // Dummy file
    private final TestFile mAppCompatJar = base64gzip("libs/appcompat-v7-18.0.0.jar", "");

    @SuppressWarnings("all") // Sample code
    private TestFile mIntermediateActivity = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import android.app.ActionBar;\n"
            + "import android.support.v7.app.ActionBarActivity;\n"
            + "import android.support.v7.view.ActionMode;\n"
            + "\n"
            + "public class IntermediateActivity extends ActionBarActivity {\n"
            + "    protected ActionBar getSupportActionBar() {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    public ActionMode startSupportActionMode(ActionMode.Callback callback) {\n"
            + "        return null;\n"
            + "    }\n"
            + "\n"
            + "    public boolean supportRequestWindowFeature(int featureId) {\n"
            + "        return true;\n"
            + "    }\n"
            + "\n"
            + "    public void setSupportProgressBarIndeterminateVisibility(boolean visible) {\n"
            + "    }\n"
            + "\n"
            + "    public void setSupportProgressBarIndeterminate(boolean indeterminate) {\n"
            + "    }\n"
            + "}\n");
}
