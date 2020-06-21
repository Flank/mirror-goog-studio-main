/*
 * Copyright (C) 2012 The Android Open Source Project
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

class ToastDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = ToastDetector()

    fun testJava() {
        val expected =
            """
        src/test/pkg/ToastTest.java:32: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                ~~~~~~~~~~~~~~
        src/test/pkg/ToastTest.java:33: Warning: Expected duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG, a custom duration value is not supported [ShowToast]
                Toast toast = Toast.makeText(context, R.string.app_name, 5000);
                                                                         ~~~~
        src/test/pkg/ToastTest.java:33: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast toast = Toast.makeText(context, R.string.app_name, 5000);
                              ~~~~~~~~~~~~~~
        src/test/pkg/ToastTest.java:39: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                ~~~~~~~~~~~~~~
        src/test/pkg/ToastTest.java:54: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast toast2 = Toast.makeText(context, "foo", Toast.LENGTH_LONG); // not shown
                               ~~~~~~~~~~~~~~
        0 errors, 5 warnings
        """

        lint().files(
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.os.Bundle;
                import android.widget.Toast;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class ToastTest extends Context {
                    private Toast createToast(Context context) {
                        // Don't warn here
                        return Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                    }

                    private void showToast(Context context) {
                        // Don't warn here
                        Toast toast = Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                        System.out.println("Other intermediate code here");
                        int temp = 5 + 2;
                        toast.show();
                    }

                    private void showToast2(Context context) {
                        // Don't warn here
                        int duration = Toast.LENGTH_LONG;
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG).show();
                        Toast.makeText(context, R.string.app_name, duration).show();
                    }

                    private void broken(Context context) {
                        // Errors
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                        Toast toast = Toast.makeText(context, R.string.app_name, 5000);
                        toast.getDuration();
                    }

                    // Constructor test
                    public ToastTest(Context context) {
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG);
                    }

                    @android.annotation.SuppressLint("ShowToast")
                    private void checkSuppress1(Context context) {
                        Toast toast = Toast.makeText(this, "MyToast", Toast.LENGTH_LONG);
                    }

                    private void checkSuppress2(Context context) {
                        @android.annotation.SuppressLint("ShowToast")
                        Toast toast = Toast.makeText(this, "MyToast", Toast.LENGTH_LONG);
                    }

                    private void showToastWrongInstance(Context context) {
                        Toast toast1 = Toast.makeText(context, "foo", Toast.LENGTH_LONG); // OK
                        Toast toast2 = Toast.makeText(context, "foo", Toast.LENGTH_LONG); // not shown
                        toast1.show();
                    }

                    public static final class R {
                        public static final class string {
                            public static final int app_name = 0x7f0a0000;
                        }
                    }
                }"""
            ).indented()
        ).run()
            .expect(expected)
            .expectFixDiffs(
                """
            Fix for src/test/pkg/ToastTest.java line 32: Call show():
            @@ -32 +32
            -         Toast.makeText(context, "foo", Toast.LENGTH_LONG);
            +         Toast.makeText(context, "foo", Toast.LENGTH_LONG).show();
            Fix for src/test/pkg/ToastTest.java line 39: Call show():
            @@ -39 +39
            -         Toast.makeText(context, "foo", Toast.LENGTH_LONG);
            +         Toast.makeText(context, "foo", Toast.LENGTH_LONG).show();
            """
            )
    }

    fun testKotlin() {
        val expected =
            """
        src/test/pkg/ToastTest.kt:34: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                ~~~~~~~~~~~~~~
        src/test/pkg/ToastTest.kt:35: Warning: Expected duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG, a custom duration value is not supported [ShowToast]
                val toast = Toast.makeText(context, R.string.app_name, 5000)
                                                                       ~~~~
        src/test/pkg/ToastTest.kt:35: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                val toast = Toast.makeText(context, R.string.app_name, 5000)
                            ~~~~~~~~~~~~~~
        src/test/pkg/ToastTest.kt:40: Warning: Toast created but not shown: did you forget to call show() ? [ShowToast]
                Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                ~~~~~~~~~~~~~~
        0 errors, 4 warnings
        """

        lint().files(
            kotlin(
                """
                package test.pkg

                import android.annotation.SuppressLint
                import android.content.Context
                import android.widget.Toast

                abstract class ToastTest
                (context: Context) : Context() {
                    private fun createToast(context: Context): Toast {
                        // Don't warn here
                        return Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                    }

                    // Don't warn here
                    private fun createToast2(context: Context): Toast = Toast.makeText(context, "foo", Toast.LENGTH_LONG)

                    private fun showToast(context: Context) {
                        // Don't warn here
                        val toast = Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                        println("Other intermediate code here")
                        val temp = 5 + 2
                        toast.show()
                    }

                    private fun showToast2(context: Context) {
                        // Don't warn here
                        val duration = Toast.LENGTH_LONG
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG).show()
                        Toast.makeText(context, R.string.app_name, duration).show()
                    }

                    private fun broken(context: Context) {
                        // Errors
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                        val toast = Toast.makeText(context, R.string.app_name, 5000)
                        toast.duration
                    }

                    init { // Constructor test
                        Toast.makeText(context, "foo", Toast.LENGTH_LONG)
                    }

                    @SuppressLint("ShowToast")
                    private fun checkSuppress1(context: Context) {
                        val toast = Toast.makeText(this, "MyToast", Toast.LENGTH_LONG)
                    }

                    private fun checkSuppress2(context: Context) {
                        @SuppressLint("ShowToast")
                        val toast = Toast.makeText(this, "MyToast", Toast.LENGTH_LONG)
                    }

                    class R {
                        object string {
                            val app_name = 0x7f0a0000
                        }
                    }
                }"""
            ).indented()
        ).run()
            .expect(expected)
            .expectFixDiffs(
                """
            Fix for src/test/pkg/ToastTest.kt line 34: Call show():
            @@ -34 +34
            -         Toast.makeText(context, "foo", Toast.LENGTH_LONG)
            +         Toast.makeText(context, "foo", Toast.LENGTH_LONG).show()
            Fix for src/test/pkg/ToastTest.kt line 40: Call show():
            @@ -40 +40
            -         Toast.makeText(context, "foo", Toast.LENGTH_LONG)
            +         Toast.makeText(context, "foo", Toast.LENGTH_LONG).show()
            """
            )
    }

    fun testSnackbar() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.view.View
                import com.google.android.material.snackbar.Snackbar

                class Test {
                    fun test1(parent: View) {
                        Snackbar.make(parent, "Message", Snackbar.LENGTH_INDEFINITE) // ERROR
                    }
                    fun test2(parent: View) {
                        val sb = Snackbar.make(parent, "Message", Snackbar.LENGTH_INDEFINITE) // OK
                        sb.show()
                    }
                    fun test3(parent: View, sb2: Snackbar) {
                        val sb = Snackbar.make(parent, "Message", 500) // ERROR
                        sb2.show()
                    }
                }"""
            ).indented(),
            *snackbarStubs
        ).run().expect(
            """
            src/test/pkg/Test.kt:8: Warning: Snackbar created but not shown: did you forget to call show() ? [ShowToast]
                    Snackbar.make(parent, "Message", Snackbar.LENGTH_INDEFINITE) // ERROR
                    ~~~~~~~~~~~~~
            src/test/pkg/Test.kt:15: Warning: Snackbar created but not shown: did you forget to call show() ? [ShowToast]
                    val sb = Snackbar.make(parent, "Message", 500) // ERROR
                             ~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        ).expectFixDiffs(
            """
            Fix for src/test/pkg/Test.kt line 8: Call show():
            @@ -8 +8
            -         Snackbar.make(parent, "Message", Snackbar.LENGTH_INDEFINITE) // ERROR
            +         Snackbar.make(parent, "Message", Snackbar.LENGTH_INDEFINITE).show() // ERROR
            """
        )
    }

    val snackbarStubs = arrayOf(
        java(
            """
            package com.google.android.material.snackbar;
            public abstract class BaseTransientBottomBar {
              public void show() { }
            }
            """
        ).indented(),
        java(
            """
            package com.google.android.material.snackbar;
            import android.view.View;
            public class Snackbar extends BaseTransientBottomBar {
                public void show() { }
                public static final int LENGTH_INDEFINITE = -2;
                public static final int LENGTH_SHORT = -1;
                public static final int LENGTH_LONG = 0;

                public static Snackbar make(View view, CharSequence text, int duration) {
                    return null;
                }
                public static Snackbar make(View view, int resId, int duration) {
                   return null;
                }
            }
            """
        )
    )
}
