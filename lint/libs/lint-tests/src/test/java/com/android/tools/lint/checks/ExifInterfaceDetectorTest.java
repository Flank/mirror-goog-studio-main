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

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.infrastructure.TestLintTask;
import com.android.tools.lint.checks.infrastructure.TestMode;
import com.android.tools.lint.detector.api.Detector;

public class ExifInterfaceDetectorTest extends AbstractCheckTest {

    @NonNull
    @Override
    protected TestLintTask lint() {
        // This lint check deliberately treats fully qualified imports
        // differently (they are interpreted as a deliberate usage of
        // the discouraged API) so the fully qualified equivalence test
        // does not apply:
        return super.lint().skipTestModes(TestMode.FULLY_QUALIFIED);
    }

    public void testAndroidX() {
        String expected =
                ""
                        + "src/test/pkg/ExifUsage.java:3: Warning: Avoid using android.media.ExifInterface; use androidx.exifinterface.media.ExifInterface instead [ExifInterface]\n"
                        + "import android.media.ExifInterface;\n"
                        + "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ExifUsage.java:13: Warning: Avoid using android.media.ExifInterface; use androidx.exifinterface.media.ExifInterface instead [ExifInterface]\n"
                        + "        android.media.ExifInterface exif2 =\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ExifUsage.java:14: Warning: Avoid using android.media.ExifInterface; use androidx.exifinterface.media.ExifInterface instead [ExifInterface]\n"
                        + "            new android.media.ExifInterface(path);\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        //noinspection all
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.media.ExifInterface;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ExifUsage {\n"
                                        + "    // platform usage\n"
                                        + "    private void setExifLatLong(String path, String lat, String lon) throws Exception {\n"
                                        + "        ExifInterface exif = new ExifInterface(path);\n"
                                        + "        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);\n"
                                        + "        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lon);\n"
                                        + "        exif.saveAttributes();\n"
                                        + "        android.media.ExifInterface exif2 =\n"
                                        + "            new android.media.ExifInterface(path);\n"
                                        + "    }\n"
                                        + "}\n"))
                .testModes(TestMode.DEFAULT)
                .run()
                .expect(expected);
    }

    public void testAndroidSupportLibrary() {
        String expected =
                ""
                        + "src/test/pkg/ExifUsage.java:3: Warning: Avoid using android.media.ExifInterface; use android.support.media.ExifInterface instead [ExifInterface]\n"
                        + "import android.media.ExifInterface;\n"
                        + "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ExifUsage.java:13: Warning: Avoid using android.media.ExifInterface; use android.support.media.ExifInterface instead [ExifInterface]\n"
                        + "        android.media.ExifInterface exif2 =\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/ExifUsage.java:14: Warning: Avoid using android.media.ExifInterface; use android.support.media.ExifInterface instead [ExifInterface]\n"
                        + "            new android.media.ExifInterface(path);\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 3 warnings";

        //noinspection all
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.media.ExifInterface;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ExifUsage {\n"
                                        + "    // platform usage\n"
                                        + "    private void setExifLatLong(String path, String lat, String lon) throws Exception {\n"
                                        + "        ExifInterface exif = new ExifInterface(path);\n"
                                        + "        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);\n"
                                        + "        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lon);\n"
                                        + "        exif.saveAttributes();\n"
                                        + "        android.media.ExifInterface exif2 =\n"
                                        + "            new android.media.ExifInterface(path);\n"
                                        + "    }\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package android.support.media;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class ExifInterface {\n"
                                        + "    // Stub\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testNonAndroidMediaUsage() {
        //noinspection all
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class ExifUsage {\n"
                                        + "    // platform usage\n"
                                        + "    private void setExifLatLong(String path, String lat, String lon) throws Exception {\n"
                                        + "        ExifInterface exif = new ExifInterface(path);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private class ExifInterface {\n"
                                        + "        public ExifInterface(String path) {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .testModes(TestMode.DEFAULT)
                .run()
                .expectClean();
    }

    @Override
    protected Detector getDetector() {
        return new ExifInterfaceDetector();
    }
}
