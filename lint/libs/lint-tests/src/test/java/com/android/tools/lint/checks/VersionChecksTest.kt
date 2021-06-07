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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.VersionChecks.Companion.getMinSdkVersionFromMethodName
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestMode.Companion.PARTIAL
import com.android.tools.lint.detector.api.Detector

/**
 * Unit tests for [VersionChecks]. This is using the ApiDetector to
 * drive the analysis.
 */
class VersionChecksTest : AbstractCheckTest() {
    fun testConditionalApi0() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        lint().files(
            classpath(),
            manifest().minSdk(14),
            java(
                """
                package test.pkg;

                import android.animation.RectEvaluator;
                import android.graphics.Rect;
                import android.os.Build;

                @SuppressWarnings("unused")
                public class ConditionalApiTest {
                    private void test(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            new RectEvaluator(rect); // OK
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (rect != null) {
                                new RectEvaluator(rect); // OK
                            }
                        }
                    }

                    private void test2(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            new RectEvaluator(rect); // OK
                        }
                    }

                    private void test3(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            new RectEvaluator(); // ERROR
                        }
                    }

                    private void test4(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            System.out.println("Something");
                            new RectEvaluator(rect); // OK
                        } else {
                            new RectEvaluator(rect); // ERROR
                        }
                    }

                    private void test5(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                            new RectEvaluator(rect); // ERROR
                        } else {
                            new RectEvaluator(rect); // ERROR
                        }
                    }
                }
                """
            ).indented()
        )
            // We *don't* want to use provisional computation for this:
            // limit suggestions around SDK_INT checks to those implied
            // by the minSdkVersion of the library.
            .skipTestModes(PARTIAL)
            .run()
            .expect(
                """
                src/test/pkg/ConditionalApiTest.java:28: Error: Call requires API level 18 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(); // ERROR
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:37: Error: Call requires API level 21 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(rect); // ERROR
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:43: Error: Call requires API level 21 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(rect); // ERROR
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:45: Error: Call requires API level 21 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(rect); // ERROR
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:27: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:42: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                4 errors, 2 warnings
                """
            )
    }

    fun testConditionalApi1() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.widget.GridLayout;

                import static android.os.Build.VERSION;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
                public class VersionConditional1 {
                    public void test(boolean priority) {
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (Build.VERSION.SDK_INT >= 14) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        // Nested conditionals
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Flagged
                            }
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        // Nested conditionals 2
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Not flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Not flagged
                            }
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }

                    public void test2(boolean priority) {
                        if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (android.os.Build.VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (android.os.Build.VERSION.SDK_INT >= 13) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (Build.VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional1.java:18: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:18: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:24: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:24: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:30: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:30: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:36: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:36: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:40: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:40: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:48: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:48: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:54: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:54: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:60: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:60: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:62: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:62: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:65: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:65: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:76: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:84: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:90: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:94: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:94: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:96: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:102: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:108: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:114: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:118: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:126: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:132: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                32 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi1b() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        // This is like testConditionalApi1, but with each logical lookup call extracted into
        // a single method. This makes debugging through the control flow graph a lot easier.
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.widget.GridLayout;

                import static android.os.Build.VERSION;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
                public class VersionConditional1b {
                    private void m9(boolean priority) {
                        // Nested conditionals 2
                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Not flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Not flagged
                            }
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }

                    private void m8(boolean priority) {
                        // Nested conditionals
                        if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Flagged
                            }
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m7() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m6() {
                        if (VERSION.SDK_INT >= 14) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m5() {
                        if (VERSION.SDK_INT < VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }
                    }

                    private void m4() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m3() {
                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m2() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m1() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    public void test2(boolean priority) {
                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= 13) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional1b.java:23: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:31: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:31: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:33: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:33: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:36: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:36: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:44: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:44: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:52: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:52: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:58: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:58: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:68: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:68: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:76: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:76: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:84: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:84: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:92: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:92: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:100: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:106: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:110: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:110: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:112: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:118: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:124: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:130: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:134: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:142: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:148: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                32 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi2() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.graphics.drawable.Drawable;
                import android.view.View;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.GINGERBREAD;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
                public class VersionConditional2 {
                    // Requires API 16 (JELLY_BEAN)
                    // root.setBackground(background);

                    private void testGreaterThan(View root, Drawable background) {
                        if (SDK_INT > GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT > ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT > ICE_CREAM_SANDWICH_MR1) { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT > JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT > VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testGreaterThanOrEquals(View root, Drawable background) {
                        if (SDK_INT >= GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testLessThan(View root, Drawable background) {
                        if (SDK_INT < GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testLessThanOrEqual(View root, Drawable background) {
                        if (SDK_INT <= GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT <= ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT <= ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT <= JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testEquals(View root, Drawable background) {
                        if (SDK_INT == GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT == VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional2.java:20: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:24: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:42: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:46: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:50: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:66: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:72: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:78: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:98: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:104: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:128: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:132: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:136: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                13 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi2b() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        // This is like testConditionalApi2, but with each logical lookup call extracted into
        // a single method. This makes debugging through the control flow graph a lot easier.
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.graphics.drawable.Drawable;
                import android.view.View;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.GINGERBREAD;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
                public class VersionConditional2b {
                    private void gt5(View root, Drawable background) {
                        if (SDK_INT > GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gt4(View root, Drawable background) {
                        if (SDK_INT > ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gt3(View root, Drawable background) {
                        if (SDK_INT > ICE_CREAM_SANDWICH_MR1) { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gt2(View root, Drawable background) {
                        if (SDK_INT > JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gt1(View root, Drawable background) {
                        if (SDK_INT > VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gte5(View root, Drawable background) {
                        if (SDK_INT >= GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte4(View root, Drawable background) {
                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte3(View root, Drawable background) {
                        if (SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte2(View root, Drawable background) {
                        if (SDK_INT >= JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gte1(View root, Drawable background) {
                        if (SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lt5(View root, Drawable background) {
                        if (SDK_INT < GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt4(View root, Drawable background) {
                        if (SDK_INT < ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt3(View root, Drawable background) {
                        if (SDK_INT < ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt2(View root, Drawable background) {
                        if (SDK_INT < JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lt1(View root, Drawable background) {
                        if (SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte5(View root, Drawable background) {
                        if (SDK_INT <= GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lte4(View root, Drawable background) {
                        if (SDK_INT <= ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lte3(View root, Drawable background) {
                        if (SDK_INT <= ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte2(View root, Drawable background) {
                        if (SDK_INT <= JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte1(View root, Drawable background) {
                        if (SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void eq5(View root, Drawable background) {
                        if (SDK_INT == GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq4(View root, Drawable background) {
                        if (SDK_INT == ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq3(View root, Drawable background) {
                        if (SDK_INT == ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq2(View root, Drawable background) {
                        if (SDK_INT == JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void eq1(View root, Drawable background) {
                        if (SDK_INT == VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional2b.java:17: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:23: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:47: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:53: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:59: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:79: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:87: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:95: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:119: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:127: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:157: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:163: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:169: Error: Call requires API level 16 (current min is 4): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                13 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi3() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;
                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import android.view.ViewDebug;

                import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
                import static android.os.Build.VERSION_CODES.LOLLIPOP;

                @SuppressWarnings({"unused", "StatementWithEmptyBody"})
                public class VersionConditional3 {
                    public void test(ViewDebug.ExportedProperty property) {
                        // Test short circuit evaluation
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > 20 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT >= 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT == 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                        }

                        // Symbolic names instead
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > KITKAT_WATCH && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > LOLLIPOP && property.hasAdjacentMapping()) { // OK
                        }

                        // Wrong operator
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                        }

                        // Test multiple conditions in short circuit evaluation
                        if (Build.VERSION.SDK_INT > 21 &&
                                System.getProperty("something") != null &&
                                property.hasAdjacentMapping()) { // OK
                        }

                        // Test order (still before call)
                        if (System.getProperty("something") != null &&
                                Build.VERSION.SDK_INT > 21 &&
                                property.hasAdjacentMapping()) { // OK
                        }

                        // Test order (after call)
                        if (System.getProperty("something") != null &&
                                property.hasAdjacentMapping() && // ERROR
                                Build.VERSION.SDK_INT > 21) {
                        }

                        if (Build.VERSION.SDK_INT > 21 && System.getProperty("something") == null) { // OK
                            boolean p = property.hasAdjacentMapping(); // OK
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional3.java:13: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:15: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:24: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:26: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:28: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:35: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:37: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:39: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:46: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:48: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:50: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:52: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:56: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                                                                                                ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:58: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                                                                                     ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:66: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:83: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                                property.hasAdjacentMapping() && // ERROR
                                         ~~~~~~~~~~~~~~~~~~
                16 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi3b() {
        // See https://code.google.com/p/android/issues/detail?id=137195
        // This is like testConditionalApi3, but with each logical lookup call extracted into
        // a single method. This makes debugging through the control flow graph a lot easier.
        lint().files(
            classpath(),
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import android.view.ViewDebug;

                import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
                import static android.os.Build.VERSION_CODES.LOLLIPOP;

                @SuppressWarnings({"unused", "StatementWithEmptyBody"})
                public class VersionConditional3b {
                    private void m28(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 21 && System.getProperty("something") == null) { // OK
                            boolean p = property.hasAdjacentMapping(); // OK
                        }
                    }

                    private void m27(ViewDebug.ExportedProperty property) {
                        // Test order (after call)
                        if (System.getProperty("something") != null &&
                                property.hasAdjacentMapping() && // ERROR
                                Build.VERSION.SDK_INT > 21) {
                        }
                    }

                    private void m26(ViewDebug.ExportedProperty property) {
                        // Test order (still before call)
                        if (System.getProperty("something") != null &&
                                Build.VERSION.SDK_INT > 21 &&
                                property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m25(ViewDebug.ExportedProperty property) {
                        // Test multiple conditions in short circuit evaluation
                        if (Build.VERSION.SDK_INT > 21 &&
                                System.getProperty("something") != null &&
                                property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m24(ViewDebug.ExportedProperty property) {
                        // Wrong operator
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m23(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > LOLLIPOP && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m22(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > KITKAT_WATCH && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m21(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m20(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m19(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m18(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m17(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m16(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m15(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m14(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m13(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m12(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m11(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m10(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m9(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m8(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m7(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m6(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m5(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m4(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m3(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 20 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m2(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m1(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }
                }
                """
            ).indented()
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditional3b.java:21: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                                property.hasAdjacentMapping() && // ERROR
                                         ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:44: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:59: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                                                                                     ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:64: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                                                                                          ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:69: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:74: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:79: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:84: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:99: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:104: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:109: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:124: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:129: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:134: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:154: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:159: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                16 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi4() {
        lint().files(
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import androidx.core.os.BuildCompat;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.M;
                import static android.os.Build.VERSION_CODES.N;
                import static android.os.Build.VERSION_CODES.N_MR1;

                @SuppressWarnings({"unused", "WeakerAccess", "StatementWithEmptyBody"})
                public class VersionConditionals4 {
                    public void testOrConditionals(int x) {
                        if (SDK_INT < N || x < 5 || methodN()) { } // OK
                        if (SDK_INT < N || methodN()) { } // OK
                        if (methodN() || SDK_INT < N) { } // ERROR
                    }

                    public void testVersionCheckMethods() {
                        if (SDK_INT >= N) { methodN(); } // OK
                        if (getBuildSdkInt() >= N) {  methodN();  }// OK
                        if (isNougat()) {  methodN(); } // OK
                        if (isAtLeast(N)) { methodN(); } // OK
                        if (isAtLeast(10)) { methodN(); } // ERROR
                        if (isAtLeast(23)) { methodN(); } // ERROR
                        if (isAtLeast(24)) { methodN(); } // OK
                        if (isAtLeast(25)) { methodN(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodM(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodN_MR1(); } // ERROR
                        if (BuildCompat.isAtLeastNMR1()) { methodN_MR1(); } // OK
                        if (isAtLeastN()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastNMR1()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastP()) { methodP(); } // OK
                        if (BuildCompat.isAtLeastQ()) { methodQ(); } // OK
                        if (isAtLeastZ()) { methodZ(); } // OK
                    }

                    public void testAndConditionals(int x) {
                        if (methodN() && SDK_INT >= N) { } // ERROR
                        if (true && methodN() && SDK_INT >= N) { } // ERROR
                        if (true && SDK_INT >= N && methodN()) { } // OK
                    }

                    // Data-binding adds this method
                    public static int getBuildSdkInt() {
                        return SDK_INT;
                    }

                    public static boolean isNougat() {
                        return SDK_INT >= N;
                    }

                    public static boolean isAtLeast(int api) {
                        return SDK_INT >= api;
                    }

                    public static boolean isAtLeastN() {
                        return BuildCompat.isAtLeastN();
                    }

                    public static boolean isAtLeastZ() {
                        return SDK_INT >= 36;
                    }

                    @RequiresApi(M)
                    public boolean methodM() {
                        return true;
                    }

                    @RequiresApi(N)
                    public boolean methodN() {
                        return true;
                    }

                    @RequiresApi(N_MR1)
                    public boolean methodN_MR1() {
                        return true;
                    }

                    @RequiresApi(28)
                    public boolean methodP() {
                        return true;
                    }

                    @RequiresApi(29)
                    public boolean methodQ() {
                        return true;
                    }

                    @RequiresApi(29)
                    public boolean methodZ() {
                        return true;
                    }
                }
                """
            ).indented(),
            jar(
                "libs/build-compat.jar",
                base64gzip(
                    "androidx/core/os/BuildCompat.class",
                    "" +
                        "H4sIAAAAAAAAAIWUz08TQRTHv9MuXVoXqKBIKYIoYotKRbxhjLXFpLE/hJKa" +
                        "4MFMt5N2cNklu1PjnyPx4MWLHDTx4B/gH2V8uy1txZbuYebNzHuf75v3Jvv7" +
                        "z89fALaxHYOOpShuYtkfVmK4hVV/uK3jjo41hshTaUv1jCGcStcYtJzTEAwz" +
                        "RWmLcvu4LtwDXrdoZ7bomNyqcVf66+6mplrSY1gucrvhOrLxMWM6rsg4XuZF" +
                        "W1qNnHN8wtUOQ0x6WVUU3FPlQOiQYaqquPm+xE+6qFhenLjC5Eo0GBL7bVvJ" +
                        "Y1GTnqTTrG07iivp2CQ2XzziH3jG4nYz048hkam+SGl/a1C0MnhYuXD4enCx" +
                        "R4uq03ZN8VL6WcUH7rHp6xqYRFTHuoE0NgzcxwMdDw1sImPgEQi8dFkpCNhP" +
                        "vlI/EqbS8ZhhoRvUc1+r7e5XCxWqlt6zjIJtCzdncc8TVAe9mn/1rlA+YGAF" +
                        "hslcJb9bzpZ2/U71NarKlXZz5x/dzh513hJ2U7WChhAhYra4m6UUtVQhnaOI" +
                        "izlhFRF6Tf6nI+TXAVSt4JlRDjRPbPwA+0ZGCFdojNEMLEJDEgZZRscJU5im" +
                        "OYoZxBEOAFuBJzB9hlB84RS69gVa+GuPFAki7+FqEB8yntMlg4C57uG1AHh9" +
                        "ODAxCvhkHHB+OHBxFDA7DnhjODA5ClgeB1wYDlwaBXw7DpggYKerb7rA1BnC" +
                        "36Fpn7BCFpkT8b1TzJ3bh5//k5qllgMtarOk9h9R41rnsuskG6JN3zWJVPBK" +
                        "GP2Y7pJT9C+SvhI3tgQAAA=="
                )
            ),
            SUPPORT_ANNOTATIONS_JAR
        )
            .run()
            .expect(
                """
                src/test/pkg/VersionConditionals4.java:16: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (methodN() || SDK_INT < N) { } // ERROR
                            ~~~~~~~
                src/test/pkg/VersionConditionals4.java:24: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (isAtLeast(10)) { methodN(); } // ERROR
                                             ~~~~~~~
                src/test/pkg/VersionConditionals4.java:25: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (isAtLeast(23)) { methodN(); } // ERROR
                                             ~~~~~~~
                src/test/pkg/VersionConditionals4.java:30: Error: Call requires API level 25 (current min is 4): methodN_MR1 [NewApi]
                        if (BuildCompat.isAtLeastN()) { methodN_MR1(); } // ERROR
                                                        ~~~~~~~~~~~
                src/test/pkg/VersionConditionals4.java:40: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (methodN() && SDK_INT >= N) { } // ERROR
                            ~~~~~~~
                src/test/pkg/VersionConditionals4.java:41: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (true && methodN() && SDK_INT >= N) { } // ERROR
                                    ~~~~~~~
                6 errors, 0 warnings
                """
            )
    }

    fun testConditionalApi5() {
        // Regression test for
        //   -- https://code.google.com/p/android/issues/detail?id=212170
        //   -- https://code.google.com/p/android/issues/detail?id=199041
        // Handle version checks in conditionals.
        lint().files(
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.Manifest;
                import android.app.Activity;
                import android.app.ActivityOptions;
                import android.content.Intent;
                import android.content.pm.PackageManager;
                import android.os.Build.VERSION;
                import android.os.Build.VERSION_CODES;
                import android.view.View;

                public class VersionConditionals5 extends Activity {
                    public boolean test() {
                        return VERSION.SDK_INT < 23
                                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    }

                    public static void startActivity(final Activity activity, View searchCardView) {
                        final Intent intent = new Intent(activity, VersionConditionals5.class);
                        if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || searchCardView == null)
                            activity.startActivity(intent);
                        else {
                            final String transitionName = activity.getString(android.R.string.ok);
                            searchCardView.setTransitionName(transitionName);
                            final ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity,
                                    searchCardView, transitionName);
                            activity.startActivity(intent, options.toBundle());
                            activity.getWindow().getSharedElementExitTransition().setDuration(100);
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testConditionalApi6() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=207289
        lint().files(
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.animation.*;
                import android.os.Build;
                import android.view.View;

                class Test {
                    View mSelection;
                    void f() {
                        final View flashView = mSelection;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            ObjectAnimator whiteFlashIn = ObjectAnimator.ofObject(flashView,
                                    "backgroundColor", new ArgbEvaluator(), 0x00FFFFFF, 0xAAFFFFFF);
                            ObjectAnimator whiteFlashOut = ObjectAnimator.ofObject(flashView,
                                    "backgroundColor", new ArgbEvaluator(), 0xAAFFFFFF, 0x00000000);
                            whiteFlashIn.setDuration(200);
                            whiteFlashOut.setDuration(300);
                            AnimatorSet whiteFlash = new AnimatorSet();
                            whiteFlash.playSequentially(whiteFlashIn, whiteFlashOut);
                            whiteFlash.addListener(new AnimatorListenerAdapter() {
                                @SuppressWarnings("deprecation")
                                @Override public void onAnimationEnd(Animator animation) {
                                    flashView.setBackgroundDrawable(null);
                                }
                            });
                            whiteFlash.start();
                        }
                    }
                }"""
            ).indented()
        ).run().expectClean()
    }

    fun testConditionalOnConstant() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=221586
        lint().files(
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.os.Build;
                import android.widget.TextView;

                public class VersionConditionals6 extends Activity {
                    public static final boolean SUPPORTS_LETTER_SPACING = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

                    public void test(TextView textView) {
                        if (SUPPORTS_LETTER_SPACING) {
                            textView.setLetterSpacing(1f); // OK
                        }
                        textView.setLetterSpacing(1f); // ERROR
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/VersionConditionals6.java:14: Error: Call requires API level 21 (current min is 4): android.widget.TextView#setLetterSpacing [NewApi]
                    textView.setLetterSpacing(1f); // ERROR
                             ~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testVersionCheckInLibrary() {
        // Include SdkIntDetector such that we record partial state about
        // SDK_INT checks in the library which are then later used by
        // the VersionChecks during ApiDetector analysis
        val issues = arrayOf(ApiDetector.UNSUPPORTED, SdkIntDetector.ISSUE)

        lint().files(
            manifest().minSdk(4),
            java(
                """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import test.utils.Utils;
                import static test.utils.Utils.isNougat;
                import static test.utils.Utils.versionCheck;
                import static test.utils.Utils.CAPABILITIES_FROM_O;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;

                public class CheckInLibraryTest {
                    public void testVersionCheckMethods() {
                        if (isNougat()) { methodN(); } // OK
                        if (versionCheck(14)) { methodN(); } // ERROR
                        if (versionCheck(28)) { methodN(); } // OK
                        if (CAPABILITIES_FROM_O) { methodN(); } // OK
                    }

                    @RequiresApi(N)
                    public boolean methodN() {
                        return true;
                    }
                }
                """
            ).indented(),
            compiled(
                "../lib/bin/classes",
                java(
                    "../lib/src/test/utils/Utils.java",
                    """
                        package test.utils;
                        import static android.os.Build.VERSION.SDK_INT;
                        import static android.os.Build.VERSION_CODES.N;
                        import static android.os.Build.VERSION_CODES.O;
                        @SuppressWarnings("AnnotateVersionCheck")
                        public class Utils {
                            public static boolean isNougat() {
                                return SDK_INT >= N;
                            }
                            // Not named "isAtLeast" since lint has hardcoded some common names
                            // like that one
                            public static boolean versionCheck(int api) {
                                return SDK_INT >= api;
                            }
                            public static final boolean CAPABILITIES_FROM_O = SDK_INT >= O;

                            public static void runOnNougat(Runnable runnable) {
                                if (SDK_INT >= N) {
                                    runnable.run();
                                }
                            }
                        }
                    """
                ).indented(),
                0x64a854c9,
                """
                    test/utils/Utils.class:
                    H4sIAAAAAAAAAHWTy27TQBSG/0nSTC7OpQmlaaEtpQXSIGGQkECiQrRuKlmk
                    MYpDF91EjmMVt2aMfOn7sGLDJrBAsOABeCjEGctNq6B6MT5zfL7z/3NG/vP3
                    128AL/CYY6UEjrtFrGG9jA3cKyKPTY77HFsMTW3v3d6+3tOHetccHQ6Mo5HB
                    wE4Y8ruucKPXDNn2zjFDTvMnDkOt5wqnH38cO8HQGnuUKbhh349PrSipJLBi
                    RpZ9fmR9SguUCycIXV9oHxz7nBq1dVlVDmJhiEtyud07sy4s1bPEqTqIhZDo
                    K6lb2LW91EjJ9OPAdg5d2bX0PnK98ImkOLYZ1i0xCXx3ovqhuh+73mT7uDsw
                    daM/0oyDrkm25vaKLoQTaJ4Vhk6ooIQyxwMFj9Dm2FHQQVlBAUWGeuSEkRpL
                    OTURpdSVWWN85th0gvq8PkPrJksM/CoyD96O9P6Qhq4zNP6fAo2VRoVNujVO
                    V8qwIm1RtEAxuaZVod0avZnMdn4gM00KK7Tmk2SegCpqaekzZCCf6jdk663P
                    4LkvyGW/UiZzjSmgnohklDeM8EU0UvwplcmCCuGrN9GVS7qc0E3cSumXKV1L
                    xYudn8gxTGd8FVlaG6TfpHgp6ZMFq1JiaXba5+kRFmWXmYnvWJjO+ahdP0UG
                    t5PPy7iTSEgnLaxC/hstPETxH5PQHcg3AwAA
                    """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).issues(*issues)
            // If we only supply the bytecode, lint would have to analyze bytecode
            // to recognize version checks inside a compiled method; it doesn't do that;
            // this is what the SdkIntDetector is for (encouraging use of annotations
            // which captures the info). However, when we analyze the source code in a
            // library we can record the information as partial state, so this *does*
            // work in partial analysis which we want to test here.
            .skipTestModes(TestMode.BYTECODE_ONLY)
            .run()
            .expect(
                """
            src/test/pkg/CheckInLibraryTest.java:14: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                    if (versionCheck(14)) { methodN(); } // ERROR
                                            ~~~~~~~
            1 errors, 0 warnings
            """
            )
    }

    fun testVersionCheckMethodsInBinaryOperator() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=199572
        lint().files(
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.hardware.camera2.CameraAccessException;
                import android.hardware.camera2.CameraManager;
                import android.os.Build;

                public class VersionConditionals8 extends Activity {
                    private boolean mDebug;

                    public void testCamera() {
                        if (isLollipop() && mDebug) {
                            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                            try {
                                int length = manager.getCameraIdList().length;
                            } catch (Throwable ignore) {
                            }
                        }
                    }

                    private boolean isLollipop() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                    }
                }
                """
            )
        ).run().expectClean()
    }

    fun testTernaryOperator() {
        lint().files(
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.view.View;
                import android.widget.GridLayout;

                public class TestTernaryOperator {
                    public View getLayout1() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                                ? new GridLayout(null) : null;
                    }

                    public View getLayout2() {
                        return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH
                                ? null : new GridLayout(null);
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testVersionInVariable() {
        // Regression test for b/35116007:
        // Allow the SDK version to be extracted into a variable or field
        lint().files(
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.view.View;
                import android.widget.GridLayout;

                public class TestVersionInVariable {
                    private static final int STASHED_VERSION = Build.VERSION.SDK_INT;
                    public void getLayout1() {
                        final int v = Build.VERSION.SDK_INT;
                        final int version = v;
                        if (version >= 14) {
                            new GridLayout(null);
                        }
                        if (STASHED_VERSION >= 14) {
                            new GridLayout(null);
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testNegative() {
        lint().files(
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.hardware.camera2.CameraAccessException;
                import android.hardware.camera2.CameraManager;
                import android.os.Build;

                public class Negative extends Activity {
                    public void testNegative1() throws CameraAccessException {
                        if (!isLollipop()) {
                        } else {
                            ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).getCameraIdList();
                        }
                    }

                    public void testReversedOperator() throws CameraAccessException {
                        if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
                            ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).getCameraIdList();
                        }
                    }

                    private boolean isLollipop() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testPrecededBy() {
        lint().files(
            manifest().minSdk(10),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.RequiresApi;

                @SuppressWarnings({"WeakerAccess", "unused"})
                public class TestPrecededByVersionCheck {
                    @RequiresApi(22)
                    public boolean requiresLollipop() {
                        return true;
                    }

                    public void test1() {
                        if (Build.VERSION.SDK_INT < 22) {
                            return;
                        }
                        requiresLollipop(); // OK
                    }

                    public void test2() {
                        if (Build.VERSION.SDK_INT < 18) {
                            return;
                        }
                        requiresLollipop(); // ERROR: API level could be 18-21
                    }

                    public void test3() {
                        requiresLollipop(); // ERROR: Version check is after
                        if (Build.VERSION.SDK_INT < 22) {
                            return;
                        }
                        requiresLollipop(); // OK
                    }

                    public void test4() {
                        if (Build.VERSION.SDK_INT > 22) {
                            return;
                        }
                        requiresLollipop(); // ERROR: Version check is going in the wrong direction: API can be 1
                    }

                    public void test5() {
                        if (Build.VERSION.SDK_INT > 22) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // OK
                    }

                    public void test6() {
                        if (Build.VERSION.SDK_INT > 18) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // ERROR: API level can be less than 22
                    }

                    public void test7() {
                        if (Build.VERSION.SDK_INT <= 22) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // ERROR: API level can be less than 22
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
                src/test/pkg/TestPrecededByVersionCheck.java:24: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR: API level could be 18-21
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:28: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR: Version check is after
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:39: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR: Version check is going in the wrong direction: API can be 1
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:57: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR: API level can be less than 22
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:66: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR: API level can be less than 22
                        ~~~~~~~~~~~~~~~~
                5 errors, 0 warnings
                """
        )
    }

    fun testNestedChecks() {
        lint().files(
            manifest().minSdk(11),
            java(
                """
                package p1.p2;

                import android.os.Build;
                import android.widget.GridLayout;

                public class Class {
                    public void testEarlyExit1() {
                        // https://code.google.com/p/android/issues/detail?id=37728
                        if (Build.VERSION.SDK_INT < 14) return;

                        new GridLayout(null); // OK
                    }

                    public void testEarlyExit2() {
                        if (!Utils.isIcs()) {
                            return;
                        }

                        new GridLayout(null); // OK
                    }

                    public void testEarlyExit3(boolean nested) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            return;
                        }

                        if (nested) {
                            new GridLayout(null); // OK
                        }
                    }

                    public void testEarlyExit4(boolean nested) {
                        if (nested) {
                            if (Utils.isIcs()) {
                                return;
                            }
                        }

                        new GridLayout(null); // ERROR

                        if (Utils.isIcs()) { // too late
                            //noinspection UnnecessaryReturnStatement
                            return;
                        }
                    }

                    private static class Utils {
                        public static boolean isIcs() {
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                        }
                        public static boolean isGingerbread() {
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
                        }
                    }
                }"""
            ).indented()
        )
            .skipTestModes(PARTIAL)
            .run()
            .expect(
                """
                src/p1/p2/Class.java:39: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                        new GridLayout(null); // ERROR
                        ~~~~~~~~~~~~~~
                src/p1/p2/Class.java:52: Warning: Unnecessary; SDK_INT is always >= 11 [ObsoleteSdkInt]
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
            )
    }

    fun testNestedChecksKotlin() {
        // Kotlin version of testNestedChecks. There are several important changes here:
        // The version check utility method is now defined as an expression body, so there
        // is no explicit "return" keyword (which the code used to look for).
        // Second, we're accessing the version check using property syntax, not a call, which
        // also required changes to the AST analysis.
        lint().files(
            manifest().minSdk(11),
            kotlin(
                """
                package p1.p2

                import android.os.Build
                import android.widget.GridLayout

                class NestedChecks {
                    fun testEarlyExit1() {
                        // https://code.google.com/p/android/issues/detail?id=37728
                        if (Build.VERSION.SDK_INT < 14) return

                        GridLayout(null) // OK
                    }

                    fun testEarlyExit2() {
                        if (!Utils.isIcs) {
                            return
                        }

                        GridLayout(null) // OK
                    }

                    fun testEarlyExit3(nested: Boolean) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            return
                        }

                        if (nested) {
                            GridLayout(null) // OK
                        }
                    }

                    fun testEarlyExit4(nested: Boolean) {
                        if (nested) {
                            if (Utils.isIcs) {
                                return
                            }
                        }

                        GridLayout(null) // ERROR

                        if (Utils.isIcs) { // too late

                            return
                        }
                    }

                    private object Utils {
                        val isIcs: Boolean
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                        val isGingerbread: Boolean
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                    }
                }"""
            ).indented()
        )
            .skipTestModes(PARTIAL)
            .run()
            .expect(
                """
                src/p1/p2/NestedChecks.kt:39: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout() [NewApi]
                        GridLayout(null) // ERROR
                        ~~~~~~~~~~~~~~~~
                src/p1/p2/NestedChecks.kt:51: Warning: Unnecessary; SDK_INT is always >= 11 [ObsoleteSdkInt]
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
            )
    }

    fun testGetMinSdkVersionFromMethodName() {
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isAtLeastKitKat")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isKitKatSdk")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isKitKatSDK")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isRunningKitkatOrLater")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isKeyLimePieOrLater")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isKitKatOrHigher")
        )
        assertEquals(
            19,
            getMinSdkVersionFromMethodName("isKitKatOrNewer")
        )
        assertEquals(
            17, getMinSdkVersionFromMethodName("isRunningJellyBeanMR1OrLater")
        )
        assertEquals(
            20,
            getMinSdkVersionFromMethodName("isAtLeastKitKatWatch")
        )
        assertEquals(
            29,
            getMinSdkVersionFromMethodName("hasQ")
        )
        assertEquals(
            28,
            getMinSdkVersionFromMethodName("hasApi28")
        )
        assertEquals(
            28,
            getMinSdkVersionFromMethodName("isAtLeastApi28")
        )
        assertEquals(
            28,
            getMinSdkVersionFromMethodName("isAtLeastAPI_28")
        )
        assertEquals(
            28,
            getMinSdkVersionFromMethodName("isApi28OrLater")
        )
    }

    fun testVersionNameFromMethodName() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.pm.ShortcutManager;

                public abstract class VersionCheck {
                    public void test(ShortcutManager shortcutManager) {
                        // this requires API 26
                        if (isAtLeastOreo()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                        if (isOreoOrLater()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                        if (isOreoOrAbove()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                    }

                    public abstract boolean isAtLeastOreo();
                    public abstract boolean isOreoOrLater();
                    public abstract boolean isOreoOrAbove();
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinWhenStatement() {
        // Regression test for
        //   67712955: Kotlin when statement fails if subject is Build.VERSION.SDK_INT
        lint().files(
            manifest().minSdk(4),
            kotlin(
                """
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES.N
                import android.text.Html

                fun String.fromHtm() : String
                {
                    return when {
                        false, SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                        else -> Html.fromHtml(this)
                    }.toString()
                }"""
            ).indented()
        ).run().expectClean()
    }

    fun testKotlinWhenStatement2() {
        // Regression test for issue 69661204
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(21)
                fun requires21() { }

                @RequiresApi(23)
                fun requires23() { }

                fun requiresNothing() { }

                fun test() {
                    when {
                        Build.VERSION.SDK_INT >= 21 -> requires21()
                        Build.VERSION.SDK_INT >= 23 -> requires23()
                        else -> requiresNothing()
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testKotlinHelper() {
        // Regression test for issue 64550633
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import android.os.Build.VERSION_CODES.KITKAT

                inline fun fromApi(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                fun fromApiNonInline(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                inline fun notFromApi(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT < value) {
                        action()
                    }
                }

                fun test1() {
                    fromApi(KITKAT) {
                        // Example of a Java 7+ field
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // OK
                    }
                }

                fun test2() {
                    fromApiNonInline(KITKAT) {
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // OK
                    }
                }

                fun test3() {
                    notFromApi(KITKAT) {
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // ERROR
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:39: Error: Field requires API level 19 (current min is 1): java.lang.Character.UnicodeBlock#CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C [NewApi]
                    val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // ERROR
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun testKotlinEarlyExit1() {
        // Regression test for issue 71560541: Wrong API condition
        // Root cause: https://youtrack.jetbrains.com/issue/IDEA-184544
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build

                fun foo1(context: Context) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT < 26 || notificationManager == null) {
                        return
                    }

                    val channel = NotificationChannel("id", "Test", NotificationManager.IMPORTANCE_DEFAULT)
                    channel.description = "test"
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testKotlinEarlyExit2() {
        // Regression test for issue 71560541: Wrong API condition, part 2
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build

                fun foo2(context: Context) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

                    val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel("id", "Test", NotificationManager.IMPORTANCE_DEFAULT)
                    } else {
                        return
                    }

                    channel.description = "test"
                }"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testNestedIfs() {
        // Regression test for issue 67553351
        lint().files(
            java(
                """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.RequiresApi;

                @SuppressWarnings({"unused", ClassNameDiffersFromFileName})
                public class NestedIfs {
                    @RequiresApi(20)
                    private void requires20() {
                    }

//                    @RequiresApi(23)
//                    private void requires23() {
//                    }

                    void test() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                                requires23();
                            } else {
                                requires20();
                            }
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testApplyBlock() {
        // Regression test for 71809249: False positive when using lambdas and higher-order functions
        lint().files(
            kotlin(
                """
                package com.example.lintexample

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build
                import android.app.Activity

                class MainActivity : Activity() {

                    fun test(notificationChannel: NotificationChannel) {
                        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.applyForOreoOrAbove {
                            createNotificationChannel(notificationChannel)
                        }

                    }
                }

                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        block()
                    }
                    return this
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun test110576968() {
        // Regression test for 110576968: NewApi isn't resolving a static final constant API value
        lint().files(
            manifest().minSdk(15),
            java(
                """
                package test.pkg;

                import android.os.Build;

                @SuppressWarnings("unused")
                public class WorkManagerTest {
                    public void test2() {
                        if (Build.VERSION.SDK_INT >= WorkManager.MIN_JOB_SCHEDULER_API_LEVEL) {
                            SystemJobScheduler scheduler = new SystemJobScheduler(); // OK
                        }
                    }
                }"""
            ).indented(),
            java(
                """
                package test.pkg;

                @SuppressWarnings("unused")
                public class WorkManager {
                    public static final int MIN_JOB_SCHEDULER_API_LEVEL = 23;
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import android.app.job.JobScheduler;

                @RequiresApi(WorkManager.MIN_JOB_SCHEDULER_API_LEVEL)
                public class SystemJobScheduler {
                    public SystemJobScheduler() { }

                    private JobScheduler mJobScheduler;    public void schedule(int systemId) {
                        mJobScheduler.getPendingJob(systemId);
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/SystemJobScheduler.java:11: Error: Call requires API level 24 (current min is 23): android.app.job.JobScheduler#getPendingJob [NewApi]
                    mJobScheduler.getPendingJob(systemId);
                                  ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    fun test113198297() {
        // Regression test for https://issuetracker.google.com/113198297
        lint().files(
            manifest().minSdk(15),
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                class UnconditionalReturn2 {
                    fun test() =
                            run {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                                    println("hello something")
                                    return
                                }
                                requires21()
                            }

                    @RequiresApi(21)
                    fun requires21() {
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testExceptionsAndErrorsAsExitPoints() {
        // Regression lifted from issue 117793069
        lint().files(
            kotlin(
                """
                import android.app.Activity

                import android.os.Build.VERSION.SDK_INT

                class ExitTest: Activity() {

                    fun testThrow() {
                        if (SDK_INT < 11) {
                            throw IllegalStateException()
                        }
                        val actionBar = getActionBar() // OK
                    }

                    fun testError() {
                        if (SDK_INT < 11) {
                            error("Api")
                        }
                        val actionBar = getActionBar() // OK
                    }

                    fun testTodo() {
                        if (SDK_INT < 11) {
                            TODO()
                        }
                        val actionBar = getActionBar() // OK
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages(false)
    }

    fun testNotEquals() {
        // Regression test lifted from issue 117793069
        lint().files(
            manifest().minSdk(1),
            kotlin(
                """
                import android.app.Activity

                import android.widget.TextView
                import android.os.Build.VERSION.SDK_INT

                class SameTest : Activity() {

                    fun test(textView: TextView) {
                        if (SDK_INT != 11 || getActionBar() == null) { // OK
                            //NO ERROR
                        }
                        if (SDK_INT != 10 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) { // ERROR
                            //ERROR
                        }
                        if (SDK_INT != 12 || /*Call requires API level 11 (current min is 1): android.app.Activity#getActionBar*/getActionBar/**/() == null) { // ERROR
                            //ERROR
                        }
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages(false)
    }

    fun test143324759() {
        // Regression test for issue 143324759: NewApi false positive on inline kotlin lambda
        lint().files(
            manifest().minSdk(1),
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.content.pm.PackageManager

                data class VersionInfo(
                    val code: Long,
                    val name: String,
                    val timestamp: String
                )

                val Context.versionInfo: VersionInfo
                    get() {
                        val metadataBundle = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                            .metaData
                        val timestamp = metadataBundle.getString("buildTimestamp") ?: "Missing timestamp!"
                        return with(packageManager.getPackageInfo(packageName, 0)) {
                            VersionInfo(
                                code = sdk(28) { longVersionCode } ?: versionCode.toLong(),
                                name = versionName,
                                timestamp = timestamp
                            )
                        }
                    }
                """
            ).indented(),
            // TODO: This currently passes. I need to port this to bytecode to have it simulate
            // what's happening in a running app.
            // OR maybe allow a form of @RequiresApi where you indicate that one of the
            // params supplies the  level
            kotlin(
                """
                package test.pkg

                import android.os.Build

                inline fun <T> sdk(level: Int, func: () -> T): T? {
                    return if (Build.VERSION.SDK_INT >= level) {
                        func()
                    } else {
                        null
                    }
                }"""
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectInlinedMessages(false)
    }

    fun testFailedResolve() {
        // Regression test for https://issuetracker.google.com/120255046
        // Make sure method-name based checks work even if we can't resolve the
        // utility method call
        lint().files(
            kotlin(
                """
                @file:Suppress("RemoveRedundantQualifierName", "unused")

                package test.pkg

                import androidx.annotation.RequiresApi
                import foo.bar.common.os.AndroidVersion
                import foo.bar.common.os.AndroidVersion.isAtLeastQ

                fun foo() {
                    if (AndroidVersion.isAtLeastQ()) {
                        bar()
                    }
                    if (com.evo.common.os.AndroidVersion.isAtLeastQ()) {
                        bar()
                    }
                    if (isAtLeastQ()) {
                        bar()
                    }
                }

                @RequiresApi(25)
                fun bar() {
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .allowCompilationErrors() // Deliberate resolve errors
            .run().expectClean()
    }

    fun testChecksSdkIntAtLeast() {
        // Regression test for https://issuetracker.google.com/120255046
        // The @ChecksSdkIntAtLeast annotation allows annotating methods and
        // fields as version check methods without relying on (a) accessing
        // the method body to see if it's an SDK_INT check, which doesn't work
        // for compiled libraries, and (b) name patterns, which doesn't
        // work for unusually named version methods.
        lint().files(
            kotlin(
                "src/main/java/test/pkg/test.kt",
                """
                package test.pkg

                import androidx.annotation.RequiresApi

                fun test() {
                    if (versionCheck1) {
                        bar() // OK 1
                    }
                    if (Constants.getVersionCheck2()) {
                        bar() // OK 2
                    }
                    if (Constants.SUPPORTS_LETTER_SPACING) {
                        bar() // OK 3
                    }
                    sdk(28) { bar() } ?: fallback() // OK 4
                    if (Constants.getVersionCheck3("", false, 21)) {
                        bar(); // OK 5
                    }
                    "test".applyForOreoOrAbove { bar() } // OK 6
                    fromApi(10) { bar() } // OK 7
                    bar() // ERROR
                    sdk(28, { bar() }) ?: fallback() // OK 8
                    sdk(level = 28, func = { bar() }) ?: fallback() // OK 9
                    sdk( func = { bar() }, level = 28) ?: fallback() // OK 10
                }

                @RequiresApi(10)
                fun bar() {
                }

                fun fallback() {
                }
                """
            ).indented(),
            kotlin(
                "src/main/java/test/pkg/utils.kt",
                """
                @file:Suppress("RemoveRedundantQualifierName", "unused")

                package test.pkg
                import android.os.Build
                import androidx.annotation.ChecksSdkIntAtLeast

                @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                inline fun fromApi(value: Int, action: () -> Unit) {
                }

                @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O, lambda = 0)
                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                    return this
                }

                @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                inline fun <T> sdk(level: Int, func: () -> T): T? {
                    return null
                }

                @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.HONEYCOMB)
                val versionCheck1: Boolean
                    get() = false
                """
            ),
            java(
                """
                package test.pkg;

                import android.os.Build;

                import androidx.annotation.ChecksSdkIntAtLeast;

                public class Constants {
                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                    public static boolean getVersionCheck2() {
                        return false;
                    }

                    @ChecksSdkIntAtLeast(parameter = 2)
                    public static boolean getVersionCheck3(String sample, boolean sample2, int apiLevel) {
                        return false;
                    }

                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                    public static final boolean SUPPORTS_LETTER_SPACING = Boolean.getBoolean("foo");
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        )
            .run()
            .expect(
                """
                src/main/java/test/pkg/test.kt:21: Error: Call requires API level 10 (current min is 1): bar [NewApi]
                    bar() // ERROR
                    ~~~
                1 errors, 0 warnings
                """
            )
    }

    // TODO: Test out of order parameters!
    fun testChecksSdkIntAtLeastLambda() {
        // Regression test for https://issuetracker.google.com/120255046
        // The @ChecksSdkIntAtLeast annotation allows annotating methods and
        // fields as version check methods without relying on (a) accessing
        // the method body to see if it's an SDK_INT check, which doesn't work
        // for compiled libraries, and (b) name patterns, which doesn't
        // work for unusually named version methods.
        lint().files(
            java(
                """
                package test.pkg;
                import androidx.annotation.RequiresApi;
                class Scratch {
                    @RequiresApi(24)
                    public static void requiresApiN() {
                    }

                    public static void main(String[] args) {
                        Constants.runOnNougat(new Runnable() {
                            @Override
                            public void run() {
                                requiresApiN(); // OK 1
                            }
                        });
                        Constants.runOnNougat2(new Runnable() {
                            @Override
                            public void run() {
                                requiresApiN(); // OK 2
                            }
                        });
                        Constants.runOnNougat(() -> requiresApiN()); // OK 3
                        Constants.runOnNougat2(() -> requiresApiN()); // OK 4
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;

                import android.os.Build;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class Constants {
                    @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.N, lambda=0)
                    public static void runOnNougat(Runnable runnable) {
                    }

                    public static void runOnNougat2(Runnable runnable) {
                        if (SDK_INT >= N) {
                            runnable.run();
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .run()
// TODO: Add in other positions, maybe even out of order, to make sure we handle it right
            .expectClean()
    }

    fun testChecksSdkIntAtLeastBytecode() {
        // Similar to testChecksSdkIntAtLeast, but with precompiled bytecode
        // for the source files in that test insted of sources, since PSI
        // treats annotations from bytecode and source files quite differently,
        // and we want to have a unit test for the main intended purpose
        // of this functionality: identified compiled version check methods
        // in libraries as version check methods, since here looking inside
        // the method bodies won't work at all.
        // Regression test for https://issuetracker.google.com/120255046
        lint().files(
            kotlin(
                "src/main/java/test/pkg/test.kt",
                """
                package test.pkg

                import test.pkg.constants.Constants;
                import test.pkg.utils.*;
                import androidx.annotation.RequiresApi

                fun test() {
                    if (versionCheck1) {
                        bar() // OK 1
                    }
                    if (Constants.getVersionCheck2()) {
                        bar() // OK 2
                    }
                    if (Constants.SUPPORTS_LETTER_SPACING) {
                        bar() // OK 3
                    }
                    sdk(28) { bar() } ?: fallback() // OK 4
                    if (Constants.getVersionCheck3("", false, 21)) {
                        bar(); // OK 5
                    }
                    "test".applyForOreoOrAbove { bar() } // OK 6
                    fromApi(10) { bar() } // OK 7
                    bar() // ERROR
                }

                @RequiresApi(10)
                fun bar() {
                }

                fun fallback() {
                }
                """
            ).indented(),
            bytecode(
                "libs/lib1.jar",
                kotlin(
                    "src/test/pkg/utils/utils.kt",
                    """
                    @file:Suppress("RemoveRedundantQualifierName", "unused")

                    package test.pkg.utils
                    import android.os.Build
                    import androidx.annotation.ChecksSdkIntAtLeast

                    @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                    inline fun fromApi(value: Int, action: () -> Unit) {
                    }

                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O, lambda = 0)
                    inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                        return this
                    }

                    @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                    inline fun <T> sdk(level: Int, func: () -> T): T? {
                        return null
                    }


                    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.HONEYCOMB)
                    val versionCheck1: Boolean
                        get() = false
                    """
                ),
                "test/pkg/utils/UtilsKt.class:" +
                    "H4sIAAAAAAAAAI1UzVcaVxT/vQFhGPwY0fiBrVpDEtDqIEnaNBhbYzVySrWt" +
                    "xEVdPWAkI8MMZ97ASXaebvo3dNt1F+0up4sej931P+k/0dP7RjCgNJbFe/fd" +
                    "+7v3/u4H89c/v/8BIIcCw5RvCt9o1mtGy7dsYbyU51d+FIxBP+VtbtjcqRkH" +
                    "5VOzQtoQQ/TEcxtbTYshlS4U665vW45x2m4YJy2n4luuI4zdjpTNZ44YHt4K" +
                    "2+jaXzqWn98MvDJF7lQ916q+NrjjuD6XSGP7lVmpi8NqveD4W37R5MLPM8Sa" +
                    "3OMN0ze9ENUFhojNG+Uqly+q427R9WrGqemXPW5R3nfxhLHv+vst26YgER6Q" +
                    "UTHKMN/D13IorsNtg1J65G5VRBQ6w52K5NLx/6ZLgOFBuni9b/kezaEMUqMS" +
                    "h5HAhIZxTDIMtbndMhkYTWTxtp4yDKes1Enqag4TvNm03+y63oFnugfeVtlt" +
                    "U6zdQTzeG3o9n7npwvDtRunpTf1mulS6Ld7Gag+mO1tSMYR405LTSVLtZdut" +
                    "1FUsMMym/FeWSA0sJzGI2i29WifITNCrgSFDolpnePw/tnhQ7uf/0ZZbl32F" +
                    "OtDtQ+p9q0l7xcu2SbCwjKMiQ92yzbZpM6hBWUEBes30j0xPkFPw91inytKZ" +
                    "72V74wzjXTpfmz6vcp9TOKXRltYwSeVAor81q0tBIVWVAvx9fpbWzs80RY9r" +
                    "ihqiW7l8qsE1o5A2ennPSEtySQ8nlexQLqJH6I7mhnU1qSbCCdJlY3sXP6p/" +
                    "vmUEM3QtGZ5he/GlsHp+pscJP9zBj1zi9+I9+IufI4o+mnykjyWVK59lEnPT" +
                    "up6cuMR3soxLsJro951YGtcprZJlT16QIpIMq4oeuvhBiWpD6sVPuSyTBecY" +
                    "Rtr9HYTcuE7nev96rDTAIPdsvqvcee2bjgzVtZbeNOUM1eADu1b3aZzbbpX2" +
                    "b6xoOeZ+q1E2vZIctIzsVrh9xD1LvjvK2KFVc7jf8kie+67l+FbDLDhtS1hk" +
                    "3nq3MLRN161Xn6U+mHbotryKuWvJ6LMdn6Mb8bAOBWH5QaVzFkOI0L1Frz3S" +
                    "y2XREtqzlcTYW9z5Ta4OntMZocbFoGGb5MVLFKYwHUTRMIoZsocQpXjT5PFl" +
                    "4BfFDt06WVR6xQoEjBcwjF3SxpTggz5C5yySmOtQKHQoxAMKi0Rh+ddrHEZ7" +
                    "OMTxEZYCDnEs4G7AQUWqh8MHfRw+LGBectBucriH+wM5LBMHdp3DRB+HlU4f" +
                    "4sh0+qDi4x4ODwIOkyT19yFN2hs8VrFGIMkjSTr5U0K/BPZueonaCdLJopgs" +
                    "yiCXF0GuL2iKQJPUWZrs+jFClKmAnMz3sPvSCniExwV8gk+PwQSe4LNjTAo8" +
                    "FcgLrAqsCQwF8lRwjgpsCNwTeCawIpARuC+QFFgQ2BT4XGDuXy4VqTB7CAAA",
                "META-INF/main.kotlin_module:" +
                    "H4sIAAAAAAAAAGNgYGBmYGBgBGIWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcEly" +
                    "8cHE9EpLMnOKhdhDQZR3iRKDFgMA7UDMuUcAAAA="
            ),
            bytecode(
                "libs/lib1.jar",
                kotlin(
                    "src/test/pkg/utils/utils2.kt",
                    """
                    @file:Suppress("RemoveRedundantQualifierName", "unused")

                    package test.pkg.utils
                    import android.os.Build
                    import androidx.annotation.ChecksSdkIntAtLeast

                    fun mytest() {
                        println("mytest")
                    }

                    """
                ),
                """
                test/pkg/utils/Utils2Kt.class:
                H4sIAAAAAAAAAG1QyU4CQRB9PQMMjCi44IL7loAHR4g3vBgTEyIuEeXCqZGO
                aZnpMUyP0Ru/pAcPHgxnP8pYPTeNfajlvVfVVfX1/fEJ4BC7DAtaRNp7HNx7
                sZZ+5N0aWz/TDhhD8YE/cc/n6t677D2IO0JthkzwYooY7Eq1k0X6l679EmkR
                OMgSHcYkKrUSUobe1VAq3dZDwYNGHi4mcsghzzD7j8DBFIPzaABfUZNK6+8o
                jWonjyKmXRQwwzDdGoTal8o7F5r3ueYNBit4smlPZgxNyQYmsAh/liY6oKhf
                o//Ho4w7HrlW0SpniuNR2TpghqozqkUuuUt9f0C7pE7CvmAotKQSF3HQE8Mb
                3vMJcdthPLwTp9IkS9ex0jIQHRlJYo+VCjXXMlQRarCQgnkkQxoZ8puUVQm3
                yE/OOGf20Rsm994x+5rItsi6CUnHSqJtswU2sEO+Roo5alTqwm5ivokFslhs
                UvdyE8tY6YJFWMVaF1aEdIT1HzzAfZH9AQAA
                """,
                """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcClz
                8cHE9EpLMnOKhThCQZSRd4kQO5jlXaLEoMUAAMCpI5xRAAAA
                """
            ),
            bytecode(
                "libs/lib2.jar",
                java(
                    """
                    package test.pkg.constants;

                    import android.os.Build;

                    import androidx.annotation.ChecksSdkIntAtLeast;

                    public class Constants {
                        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                        public static boolean getVersionCheck2() {
                            return false;
                        }

                        @ChecksSdkIntAtLeast(parameter = 2)
                        public static boolean getVersionCheck3(String sample, boolean sample2, int apiLevel) {
                            return false;
                        }

                        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                        public static final boolean SUPPORTS_LETTER_SPACING = Boolean.getBoolean("foo");
                    }
                    """
                ),
                "test/pkg/constants/Constants.class:" +
                    "H4sIAAAAAAAAAG1RTU8bMRB9zoYkm6QQKCnlm5QeSA9dqT30AEIKEa1WWkGU" +
                    "DTnkgpyNG0wSO9p1UH9UL4hDEYf+AH4UYnYFoQUOnrGf5817Y9/e3fwF8A0f" +
                    "88hgJYfVPNawbmMGG1lsZrHFsOSfNBrHzZZ/6h22WofNU79Rq7tHPxhYh2G1" +
                    "OVFGjoSrLmQku0NRU0obbqRWEUPV46oXatn75fAp7tTPRDCI/N7AVaZmPMEj" +
                    "s8tg8bG0yEyZIbMnlTT7hO1U2wzpuu4JhjlPKnE0GXVF2OKkxFDqC9MWYURN" +
                    "k55fEkbn5cVXGmPHO+cX3Bly1Xd8E0rV3+24cbE95iEfCSPCWD7FkNsLhg8G" +
                    "8r6ehIH4LmO52ToNZbgy0ee4VRFvMEuKP7XOolLEB2wXkUWOYc2IyDjjQd8J" +
                    "HhnOlEvunowcd89FYBjmn6ADrYeCK9KmIaaH8iv2qx1U6Kcy5JphOZambNOp" +
                    "QDlPq+CiiDRtyCjFObpap8woz3z6A3aZMEsUMwmYI/Y8FpBKSlcSjF7E+v2s" +
                    "rvBSxcZbLP5DtF4llv4jll28I82lqbnKg6K9kLqGdYX0c4OLFN/HnbF8D6c8" +
                    "O3S7AgAA"
            ),
            SUPPORT_ANNOTATIONS_JAR
        )
            .run()
            .expect(
                """
                src/main/java/test/pkg/test.kt:23: Error: Call requires API level 10 (current min is 1): bar [NewApi]
                    bar() // ERROR
                    ~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testPolyadic() {
        lint().files(
            manifest().minSdk(14),
            java(
                """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION.SDK_INT;

                @SuppressWarnings("unused")
                public class PolyadicTest {
                    @RequiresApi(Build.VERSION_CODES.M)
                    public boolean methodM() {
                        return true;
                    }

                    private void test() {
                        boolean field1 = false;
                        boolean field2 = false;
                        boolean field3 = false;

                        if (field1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        } else {
                            methodM(); // ERROR 1
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            methodM(); // OK 1
                        }
                        if (field1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            methodM(); // OK 2
                        }
                        if (field1 && field2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && field3) {
                            methodM(); // OK 3
                        }
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        )
            .run()
            .expect(
                """
                src/test/pkg/PolyadicTest.java:22: Error: Call requires API level 23 (current min is 14): methodM [NewApi]
                            methodM(); // ERROR 1
                            ~~~~~~~
                1 errors, 0 warnings
                """
            )
    }

    fun testNextPlatformHandling() {
        // Regression test for b/172930073
        // Need to gracefully handle the next version of Android
        lint().files(
            manifest().minSdk(14),
            java(
                """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;
                import androidx.core.os.BuildCompat;
                import androidx.annotation.RequiresApi;

                public class TestS {
                    public int test() {
                        if (BuildCompat.isAtLeastS()) {
                            return ApiSImpl.getChecksums();
                        }
                        if (BuildCompat.isCurrentDev()) {
                            return ApiSImpl.getChecksums();
                        }
                        return 0;
                    }

                    @RequiresApi(Build.VERSION_CODES.S)
                    private static class ApiSImpl {
                        public static int getChecksums() {
                            return 0;
                        }
                    }
                }
                """
            ).indented(),
            java(
                """
                package androidx.core.os;
                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class BuildCompat {
                    @ChecksSdkIntAtLeast(codename = "S")
                    public static boolean isAtLeastS() {
                        return Build.VERSION.CODENAME.equals("S");
                    }
                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CUR_DEVELOPMENT)
                    public static boolean isCurrentDev() {
                        return false; // stub only; annotation used for version lookup
                    }
                }
                """

            ).indented(),
            java(
                """
                package android.os;

                public class Build {
                    public static class VERSION_CODES {
                        public static final int CUR_DEVELOPMENT = 10000;
                        public static final int S = CUR_DEVELOPMENT;
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testNextPlatformHandling2() {
        lint().files(
            manifest().minSdk(14),
            kotlin(
                """
                import android.os.Build
                import androidx.annotation.RequiresApi;
                import androidx.core.os.BuildCompat

                @RequiresApi(Build.VERSION_CODES.S)
                private fun requiresSFunction() {
                }

                fun testIsAtLeastS() {
                    if (BuildCompat.isAtLeastS()) {
                        requiresSFunction();
                    }
                }
                """
            ).indented(),
            java(
                """
                package androidx.core.os;
                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class BuildCompat {
                    @ChecksSdkIntAtLeast(codename = "S")
                    public static boolean isAtLeastS() {
                        return VERSION.CODENAME.equals("S");
                    }
                }
                """
            ).indented(),
            java(
                """
                package android.os;

                public class Build {
                    public static class VERSION_CODES {
                        public static final int S = 10000;
                    }
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    fun testWhen() {
        // Regression test for issue 189459502
        lint().files(
            kotlin(
                """
                import android.os.Build
                import androidx.annotation.RequiresApi

                private val supportsRenderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

                private val capture = when {
                    supportsRenderNode -> RenderNodeCapture()
                    else -> "fallback"
                }


                @RequiresApi(29)
                fun RenderNodeCapture() {
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expectClean()
    }

    override fun getDetector(): Detector {
        return ApiDetector()
    }
}
