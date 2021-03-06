/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;

public class EllipsizeMaxLinesDetectorTest extends AbstractCheckTest {
    private TestFile testFile =
            xml(
                    "res/layout/sample.xml",
                    ""
                            + "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                            + "    android:layout_width=\"wrap_content\"\n"
                            + "    android:layout_height=\"wrap_content\" >\n"
                            + "\n"
                            + "    <TextView\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:ellipsize=\"start\"\n" // ERROR
                            + "        android:lines=\"1\"\n"
                            + "        android:text=\"Really long text that needs to be ellipsized here - 0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\" />\n"
                            + "\n"
                            + "    <TextView\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:ellipsize=\"start\"\n" // ERROR
                            + "        android:maxLines=\"1\"\n"
                            + "        android:text=\"long text\" />\n"
                            + "\n"
                            + "    <TextView\n"
                            + "        android:layout_width=\"wrap_content\"\n"
                            + "        android:layout_height=\"wrap_content\"\n"
                            + "        android:ellipsize=\"end\"\n" // ok
                            + "        android:lines=\"1\"\n"
                            + "        android:text=\"long text\" />\n"
                            + "\n"
                            + "</RelativeLayout>\n");

    public void testSimple() {
        lint().files(testFile)
                .run()
                .expect(
                        ""
                                + "res/layout/sample.xml:9: Error: Combining ellipsize=start and lines=1 can lead to crashes. Use singleLine=true instead. [EllipsizeMaxLines]\n"
                                + "        android:lines=\"1\"\n"
                                + "        ~~~~~~~~~~~~~~~~~\n"
                                + "    res/layout/sample.xml:8: <No location-specific message>\n"
                                + "        android:ellipsize=\"start\"\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "res/layout/sample.xml:16: Error: Combining ellipsize=start and maxLines=1 can lead to crashes. Use singleLine=true instead. [EllipsizeMaxLines]\n"
                                + "        android:maxLines=\"1\"\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~\n"
                                + "    res/layout/sample.xml:15: <No location-specific message>\n"
                                + "        android:ellipsize=\"start\"\n"
                                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "2 errors, 0 warnings\n")
                .expectFixDiffs(
                        ""
                                + "Autofix for res/layout/sample.xml line 9: Replace with singleLine=\"true\":\n"
                                + "@@ -10 +10\n"
                                + "-         android:lines=\"1\"\n"
                                + "+         android:singleLine=\"true\"\n"
                                + "Autofix for res/layout/sample.xml line 16: Replace with singleLine=\"true\":\n"
                                + "@@ -17 +17\n"
                                + "-         android:maxLines=\"1\"\n"
                                + "+         android:singleLine=\"true\"\n");
    }

    public void testOkOn23() {
        lint().files(manifest().minSdk(23), testFile).run().expectClean();
    }

    @Override
    protected Detector getDetector() {
        return new EllipsizeMaxLinesDetector();
    }
}
