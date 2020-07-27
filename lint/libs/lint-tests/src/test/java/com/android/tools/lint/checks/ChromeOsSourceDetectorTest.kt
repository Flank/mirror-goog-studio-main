/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class ChromeOsSourceDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector {
        return ChromeOsSourceDetector()
    }

    fun testValidSetRequestedOrientation() {
        lint().files(
            java(
                """
            package test.pkg;

            import android.app.Activity;

            import android.content.pm.ActivityInfo;
            import android.os.Bundle;
            import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;

            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                    setRequestedOrientation(SCREEN_ORIENTATION_FULL_SENSOR);
                }
            }
        """
            ).indented()
        )
            .issues(ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION)
            .run()
            .expectClean()
    }

    fun testInvalidSetRequestedOrientation() {

        val expected =
            """
        src/test/pkg/MainActivity.java:15: Warning: You should not lock orientation of your activities, so that you can support a good user experience for any device or orientation [SourceLockedOrientationActivity]
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """

        lint().files(
            java(
                """
            package test.pkg;

            import android.app.Activity;

            import android.content.pm.ActivityInfo;
            import android.os.Bundle;


            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        """
            ).indented()
        )
            .issues(ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION)
            .run()
            .expect(expected)
    }

    fun testValidCameraSystemFeature() {
        lint().files(
            java(
                """
            package test.pkg;

            import android.app.Activity;

            import android.content.pm.PackageManager;
            import android.os.Bundle;


            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
                }
            }
        """
            ).indented()
        )
            .issues(ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE)
            .run()
            .expectClean()
    }

    fun testInvalidCameraSystemFeature() {

        val expected =
            """
            src/test/pkg/MainActivity.java:15: Warning: You should look for any camera available on the device, not just the rear [UnsupportedChromeOsCameraSystemFeature]
                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """

        lint().files(
            java(
                """
            package test.pkg;

            import android.app.Activity;

            import android.content.pm.PackageManager;
            import android.os.Bundle;


            public class MainActivity extends Activity {

                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);

                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
                }
            }
        """
            ).indented()
        )
            .issues(ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE)
            .run()
            .expect(expected)
    }
}
