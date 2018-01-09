/*
 * Copyright (C) 2018 The Android Open Source Project
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

public class PowerManagerDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new PowerManagerDetector();
    }

    public void testValidTag() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"myapp:mytag\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expectClean();
    }

    public void testValidTagWithConstant() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    private static final String TAG = \"myapp:mytag\";\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expectClean();
    }

    public void testInvalidEmptyTag() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity.java:12: "
                        + "Error: Tag name should not be empty to make wake lock problems easier to debug [InvalidWakeLockTag]\n"
                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"\");\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expect(expected);
    }

    public void testNoPrefix() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity.java:12: "
                        + "Error: Tag name should use a unique prefix followed by a colon (found tag). For instance myapp:mywakelocktag. This will help with debugging [InvalidWakeLockTag]\n"
                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"tag\");\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"tag\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expect(expected);
    }

    public void testInvalidPlatformTag() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity.java:12: "
                        + "Error: *tag* is a reserved platform tag name and cannot be used [InvalidWakeLockTag]\n"
                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"*tag*\");\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"*tag*\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expect(expected);
    }

    public void testInvalidPlatformTag2() {
        String expected =
                ""
                        + "src/test/pkg/WakelockActivity.java:12: "
                        + "Error: *job*/myjob is a reserved platform tag name and cannot be used [InvalidWakeLockTag]\n"
                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"*job*/myjob\");\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";

        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.os.PowerManager;\n"
                                        + "\n"
                                        + "public class WakelockActivity extends Activity {\n"
                                        + "    @Override\n"
                                        + "    public void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n"
                                        + "        mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"*job*/myjob\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .issues(PowerManagerDetector.INVALID_WAKE_LOCK_TAG)
                .run()
                .expect(expected);
    }
}
