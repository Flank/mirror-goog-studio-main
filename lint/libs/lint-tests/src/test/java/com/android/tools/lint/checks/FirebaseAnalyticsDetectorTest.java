/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector;

public class FirebaseAnalyticsDetectorTest extends AbstractCheckTest {

    final TestFile mFirebaseAnalytics = java("src/com/google/firebase/analytics/FirebaseAnalytics.java", ""
            + "package com.google.firebase.analytics;\n"
            + "import android.os.Bundle;\n"
            + "public class FirebaseAnalytics {\n"
            + "    private FirebaseAnalytics() { }\n"
            + "    public static FirebaseAnalytics getInstance(Object object) {\n"
            + "        return new FirebaseAnalytics();\n"
            + "    }\n"
            + "    public void logEvent(String s, Bundle b) { }\n"
            + "}");

    @Override
    protected Detector getDetector() {
        return new FirebaseAnalyticsDetector();
    }

    public void testInvalidCharacters() throws Exception {
        TestFile mainActivity = java("src/test/pkg/MainActivity.java", ""
                + "package test.pkg;\n"
                + "import android.os.Bundle;\n"
                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                + "public class MainActivity {\n"
                + "    public MainActivity() {\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(\"a;\", new Bundle());\n"
                + "    }\n"
                + "}");
        String expected = ""
                + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must only consist of letters, numbers and underscores (found a;) [InvalidAnalyticsEventName]\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(\"a;\", new Bundle());\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        String result = lintProject(mFirebaseAnalytics, mainActivity);

        assertEquals(expected, result);
    }

    public void testInvalidLength() throws Exception {
        TestFile mainActivity = java("src/test/pkg/MainActivity.java", ""
                + "package test.pkg;\n"
                + "import android.os.Bundle;\n"
                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                + "public class MainActivity {\n"
                + "    public MainActivity() {\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(\"123456789012345678901234567890123\", new Bundle());\n"
                + "    }\n"
                + "}");
        String expected = ""
                + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must be less than 32 characters (found 33) [InvalidAnalyticsEventName]\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(\"123456789012345678901234567890123\", new Bundle());\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        String result = lintProject(mFirebaseAnalytics, mainActivity);

        assertEquals(expected, result);
    }

    public void testInvalidConstant() throws Exception {
        TestFile mainActivity = java("src/test/pkg/MainActivity.java", ""
                + "package test.pkg;\n"
                + "import android.os.Bundle;\n"
                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                + "public class MainActivity {\n"
                + "    static final String FOO = \";\";"
                + "    public MainActivity() {\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(FOO, new Bundle());\n"
                + "    }\n"
                + "}");
        String expected = ""
                + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must start with an alphabetic character (found ;) [InvalidAnalyticsEventName]\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(FOO, new Bundle());\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";

        String result = lintProject(mFirebaseAnalytics, mainActivity);

        assertEquals(expected, result);
    }

    public void testValidFirebaseAnalyticsEventName() throws Exception {
        TestFile mainActivity = java("src/test/pkg/MainActivity.java", ""
                + "package test.pkg;\n"
                + "import android.os.Bundle;\n"
                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                + "public class MainActivity {\n"
                + "    public MainActivity() {\n"
                + "        FirebaseAnalytics.getInstance(this).logEvent(\"a\", new Bundle());\n"
                + "    }\n"
                + "}");
        String expected = "No warnings.";

        String result = lintProject(mFirebaseAnalytics, mainActivity);

        assertEquals(expected, result);
    }

    public void testLogEvent() throws Exception {
        TestFile mainActivity = java("src/test/pkg/MainActivity.java", ""
                + "package test.pkg;\n"
                + "import android.os.Bundle;\n"
                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                + "public class MainActivity {\n"
                + "    public MainActivity() {\n"
                + "        logEvent(\";\", new Bundle());\n"
                + "    }\n"
                + "    void logEvent(String s, Bundle b) { }\n"
                + "}");
        String expected = "No warnings.";

        String result = lintProject(mFirebaseAnalytics, mainActivity);

        assertEquals(expected, result);
    }
}
