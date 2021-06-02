/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion.VersionCodes.R
import com.android.sdklib.AndroidVersion.VersionCodes.S
import com.android.tools.lint.checks.infrastructure.TestFile

class SplashScreenDetectorTest : AbstractCheckTest() {
    override fun getDetector() = SplashScreenDetector()

    fun testDocumentationExample() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.support.v7.app.AppCompatActivity
                import android.os.Bundle

                class SplashActivity : AppCompatActivity() {
                    override fun onCreate(savedState: Bundle?) { }
                }
                """
            ).indented(),
            // v7 AppCompatActivity stub
            java(
                """
                package android.support.v7.app; // HIDE-FROM-DOCUMENTATION
                public class AppCompatActivity extends android.app.Activity {
                }
                """
            ),
            manifest().minSdk(S)
        ).run()
            .expect(
                """
                src/test/pkg/SplashActivity.kt:6: Warning: The application should not provide its own launch screen [CustomSplashScreen]
                class SplashActivity : AppCompatActivity() {
                      ~~~~~~~~~~~~~~
                0 errors, 1 warnings
            """
            )
    }

    fun testSplashScreen2() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.fragment.app.FragmentActivity
                import android.os.Bundle

                class LaunchScreen : FragmentActivity() {
                    override fun onCreate(savedState: Bundle?) { }
                }
                """
            ).indented(),
            fragmentActivityStub,
            manifest().minSdk(S)
        ).run()
            .expect(
                """
                src/test/pkg/LaunchScreen.kt:6: Warning: The application should not provide its own launch screen [CustomSplashScreen]
                class LaunchScreen : FragmentActivity() {
                      ~~~~~~~~~~~~
                0 errors, 1 warnings
            """
            )
    }

    fun testSplashScreen3() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.fragment.app.Fragment
                import android.os.Bundle

                class SplashScreen : Fragment() {
                }
                """
            ).indented(),
            fragmentStub,
            manifest().minSdk(S)
        ).run()
            .expect(
                """
                src/test/pkg/SplashScreen.kt:6: Warning: The application should not provide its own launch screen [CustomSplashScreen]
                class SplashScreen : Fragment() {
                      ~~~~~~~~~~~~
                0 errors, 1 warnings
            """
            )
    }

    fun testSplashScreen4() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.fragment.app.Fragment
                import android.os.Bundle

                class MySplashscreen : Fragment() {
                }
                """
            ).indented(),
            fragmentStub,
            manifest().minSdk(S)
        ).run()
            .expect(
                """
                src/test/pkg/MySplashscreen.kt:6: Warning: The application should not provide its own launch screen [CustomSplashScreen]
                class MySplashscreen : Fragment() {
                      ~~~~~~~~~~~~~~
                0 errors, 1 warnings
            """
            )
    }

    fun testSplashScreenPreS() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.fragment.app.Fragment
                import android.os.Bundle

                class MySplashscreen : Fragment() {
                }
                """
            ).indented(),
            fragmentStub,
            manifest().minSdk(R)
        ).run().expectClean()
    }

    fun testNonSplashScreen() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import androidx.fragment.app.FragmentActivity
                import android.os.Bundle

                class MainActivity : FragmentActivity() {
                    override fun onCreate(savedState: Bundle?) { }
                }
                """
            ).indented(),
            manifest().minSdk(S),
            fragmentActivityStub,
        ).run().expectClean()
    }

    private val fragmentActivityStub: TestFile = java(
        """
        package androidx.fragment.app;
        public class FragmentActivity extends android.app.Activity {
        }
        """
    )

    private val fragmentStub: TestFile = java(
        """
        package androidx.fragment.app;
        public class Fragment implements android.content.ComponentCallbacks {
        }
        """
    )
}
