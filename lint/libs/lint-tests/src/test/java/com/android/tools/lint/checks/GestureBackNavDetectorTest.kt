/*
 * Copyright (C) 2022 The Android Open Source Project
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

class GestureBackNavDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return GestureBackNavDetector()
    }

    private val manifestBackInvokeEnabled = manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.pkg">

            <uses-permission-sdk-33 android:name="foo.bar.BAZ" />

            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name"
                android:enableOnBackInvokedCallback="true" >
                <activity
                    android:name=".KeyEventKeyCodeBackTest"
                    android:label="@string/app_name" >
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
        """
    )

    fun testDocumentationExample() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.java:11: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                    if (KeyEvent.KEYCODE_BACK == keyCode) {
                        ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            java(
                "src/test/pkg/KeyEventKeyCodeBackTest.java",
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.view.KeyEvent;

                @SuppressWarnings("unused")
                public class KeyEventKeyCodeBackTest extends Activity {

                    public boolean onKeyUp(int keyCode, KeyEvent event) {
                        if (KeyEvent.KEYCODE_BACK == keyCode) {
                          return true;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected).expectFixDiffs(
            """
            Show URL for src/test/pkg/KeyEventKeyCodeBackTest.java line 11: https://developer.android.com/about/versions/13/features/predictive-back-gesture
            """
        )
    }

    fun testKeyCodeBackSwitchJava() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.java:12: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                      case KeyEvent.KEYCODE_BACK:
                           ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            java(
                "src/test/pkg/KeyEventKeyCodeBackTest.java",
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.view.KeyEvent;

                @SuppressWarnings("unused")
                public class KeyEventKeyCodeBackTest extends Activity {

                    public boolean onKeyUp(int keyCode, KeyEvent event) {
                        switch (keyCode) {
                          case KeyEvent.KEYCODE_BACK:
                            break;
                          default:
                            break;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKeyCodeIfStatementKotlin() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.kt:10: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                    if (KeyEvent.KEYCODE_BACK == keyCode) {
                        ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.view.KeyEvent;

                class KeyEventKeyCodeBackTest : Activity() {

                    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
                        if (KeyEvent.KEYCODE_BACK == keyCode) {
                          return true;
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKeyCodeBackSwitchKotlin() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.kt:10: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                      KeyEvent.KEYCODE_BACK -> println("keycode back")
                      ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.app.Activity;
                import android.view.KeyEvent;

                class KeyEventKeyCodeBackTest : Activity() {

                    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
                        when (keyCode) {
                          KeyEvent.KEYCODE_BACK -> println("keycode back")
                          else -> println("else")
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testDialogOnKeyListenerSwitchKotlin() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.kt:11: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                      KeyEvent.KEYCODE_BACK -> println("keycode back")
                      ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.view.KeyEvent;
                import android.content.DialogInterface;
                import android.content.DialogInterface.OnKeyListener;

                class KeyEventKeyCodeBackTest : onKeyListener() {

                    override fun onKey(dialog: DialogInterface, keyCode: Int, event: KeyEvent): Boolean {
                        when (keyCode) {
                          KeyEvent.KEYCODE_BACK -> println("keycode back")
                          else -> println("else")
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKeyCodeCheckInUtil() {
        val expected =
            """
            src/test/pkg/KeyEventKeyCodeBackTest.kt:16: Warning: If intercepting back events, this should be handled through the registration of callbacks on the window level; Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture [GestureBackNavigation]
                      KeyEvent.KEYCODE_BACK -> println("keycode back")
                      ~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        lint().files(
            manifestBackInvokeEnabled,
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.app.Activity;
                import android.view.KeyEvent;

                class KeyEventKeyCodeBackTest : Activity() {

                   override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
                     Util.handleKeyCode(keyCode)
                   }
                }

                 class Util {
                     fun handleKeyCode(keyCode: Int) {
                        when (keyCode) {
                          KeyEvent.KEYCODE_BACK -> println("keycode back")
                          else -> println("else")
                        }
                     }
                 }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testKeyUpCleanKotlin() {
        lint().files(
            manifestBackInvokeEnabled,
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.app.Activity;
                import android.view.KeyEvent;
                import android.util.Log;

                class KeyEventKeyCodeBackTest : Activity() {

                   override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
                     Log.i("Is KEYCODE_BACK", KeyEvent.KEYCODE_BACK == keyCode)
                   }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testEnableBackInvokeDisabled() {
        lint().files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">

                    <uses-permission-sdk-33 android:name="foo.bar.BAZ" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".KeyEventKeyCodeBackTest"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
            """
            ),
            kotlin(
                "src/test/pkg/KeyEventKeyCodeBackTest.kt",
                """
                package test.pkg;

                import android.app.Activity;
                import android.view.KeyEvent;

                class KeyEventKeyCodeBackTest : Activity() {

                   override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
                     Util.handleKeyCode(keyCode)
                   }
                }

                 class Util {
                     fun handleKeyCode(keyCode: Int) {
                        when (keyCode) {
                          KeyEvent.KEYCODE_BACK -> println("keycode back")
                          else -> println("else")
                        }
                     }
                 }
                """
            ).indented()
        ).run().expectClean()
    }
}
