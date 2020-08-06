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

    @SuppressWarnings("all") // Sample code
    final TestFile mFirebaseAnalytics =
            java(
                    "src/com/google/firebase/analytics/FirebaseAnalytics.java",
                    ""
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

    public void testInvalidCharacters() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"a;\", new Bundle());\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must only consist of letters, numbers and underscores (found a;) [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(\"a;\", new Bundle());\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(mFirebaseAnalytics, mainActivity).run().expect(expected);
    }

    public void testInvalidLength() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"123456789012345678901234567890123456789012345678901\", new Bundle());\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must be less than 40 characters (found 51) [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(\"123456789012345678901234567890123456789012345678901\", new Bundle());\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(mFirebaseAnalytics, mainActivity).run().expect(expected);
    }

    public void testInvalidConstant() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    static final String FOO = \";\";"
                                + "    public MainActivity() {\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(FOO, new Bundle());\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:6: Error: Analytics event name must start with an alphabetic character (found ;) [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(FOO, new Bundle());\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(mFirebaseAnalytics, mainActivity).run().expect(expected);
    }

    public void testValidFirebaseAnalyticsEventName() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"a\", new Bundle());\n"
                                + "    }\n"
                                + "}");
        lint().files(mFirebaseAnalytics, mainActivity).run().expectClean();
    }

    public void testLogEvent() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        logEvent(\";\", new Bundle());\n"
                                + "    }\n"
                                + "    void logEvent(String s, Bundle b) { }\n"
                                + "}");
        lint().files(mFirebaseAnalytics, mainActivity).run().expectClean();
    }

    public void testInvalidParameterName() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        Bundle bundle = new Bundle();\n"
                                + "        bundle.putString(\"12345678901234567890123456789012345678901\", \"foo\");\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:8: Error: Bundle with invalid Analytics event parameters passed to logEvent [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    src/test/pkg/MainActivity.java:7: Analytics event parameter name must be 40 characters or less (found 41)\n"
                        + "1 errors, 0 warnings\n";

        lint().files(mFirebaseAnalytics, mainActivity).run().expect(expected);
    }

    public void testInvalidParameterNameInClassField() {
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    private static final String PARAM_NAME = \"12345678901234567890123456789012345678901\";\n"
                                + "    public MainActivity() {\n"
                                + "        Bundle bundle = new Bundle();\n"
                                + "        bundle.putString(PARAM_NAME, \"foo\");\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:9: Error: Bundle with invalid Analytics event parameters passed to logEvent [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    src/test/pkg/MainActivity.java:8: Analytics event parameter name must be 40 characters or less (found 41)\n"
                        + "1 errors, 0 warnings\n";

        lint().files(mFirebaseAnalytics, mainActivity).run().expect(expected);
    }

    public void testInvalidParameterNameInSeparateMethod() {
        TestFile util =
                java(
                        "src/test/pkg/Util.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "public class Util {\n"
                                + "    public static Bundle makeBundle() {\n"
                                + "      Bundle bundle = new Bundle();\n"
                                + "      bundle.putString(\"12345678901234567890123456789012345678901\", \"foo\");\n"
                                + "      return bundle;\n"
                                + "    }\n"
                                + "}");
        TestFile mainActivity =
                java(
                        "src/test/pkg/MainActivity.java",
                        ""
                                + "package test.pkg;\n"
                                + "import android.os.Bundle;\n"
                                + "import com.google.firebase.analytics.FirebaseAnalytics;\n"
                                + "public class MainActivity {\n"
                                + "    public MainActivity() {\n"
                                + "        Bundle bundle = Util.makeBundle();\n"
                                + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                                + "    }\n"
                                + "}");
        String expected =
                ""
                        + "src/test/pkg/MainActivity.java:7: Error: Bundle with invalid Analytics event parameters passed to logEvent [InvalidAnalyticsName]\n"
                        + "        FirebaseAnalytics.getInstance(this).logEvent(\"bar\", bundle);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    src/test/pkg/Util.java:6: Analytics event parameter name must be 40 characters or less (found 41)\n"
                        + "1 errors, 0 warnings\n";

        lint().files(util, mFirebaseAnalytics, mainActivity).run().expect(expected);
    }
}
