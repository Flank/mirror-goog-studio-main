/*
 * Copyright (C) 2020 The Android Open Source Project
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

class PendingIntentMutableFlagDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return PendingIntentMutableFlagDetector()
    }

    fun testNoFlag() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    protected void test() {
                        PendingIntent.getActivity(null, 0, null, 0);
                        PendingIntent.getActivities(null, 0, null, 0);
                    }
                }

                """
                ).indented()
            )
        ).run().expect(
            """
                src/test/pkg/PendingIntentTest.java:7: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                        PendingIntent.getActivity(null, 0, null, 0);
                                                                 ~
                src/test/pkg/PendingIntentTest.java:8: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                        PendingIntent.getActivities(null, 0, null, 0);
                                                                   ~
                2 errors, 0 warnings
            """
        )
            .expectFixDiffs(
                """
                Fix for src/test/pkg/PendingIntentTest.java line 7: Add FLAG_IMMUTABLE (preferred):
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, 0);
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_IMMUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 7: Add FLAG_MUTABLE:
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, 0);
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_MUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 8: Add FLAG_IMMUTABLE (preferred):
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, 0);
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_IMMUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 8: Add FLAG_MUTABLE:
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, 0);
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_MUTABLE);
                """
            )
    }

    fun testNoImmutableFlag() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    protected void test() {
                        PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT);
                        PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE);
                    }
                }

                """
                ).indented()
            )
        ).run().expect(
            """
            src/test/pkg/PendingIntentTest.java:7: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/PendingIntentTest.java:8: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE);
                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
            .expectFixDiffs(
                """
                Fix for src/test/pkg/PendingIntentTest.java line 7: Add FLAG_IMMUTABLE (preferred):
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT);
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 7: Add FLAG_MUTABLE:
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT);
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 8: Add FLAG_IMMUTABLE (preferred):
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE);
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                Fix for src/test/pkg/PendingIntentTest.java line 8: Add FLAG_MUTABLE:
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE);
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_MUTABLE);
                """
            )
    }

    fun testNoImmutableFlagKotlin() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                kotlin(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    fun test() {
                        PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT)
                        PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE)
                    }
                }

                """
                ).indented()
            )
        ).run().expect(
            """
            src/test/pkg/PendingIntentTest.kt:7: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/PendingIntentTest.kt:8: Error: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE)
                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
            .expectFixDiffs(
                """
                Fix for src/test/pkg/PendingIntentTest.kt line 7: Add FLAG_IMMUTABLE (preferred):
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT)
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                Fix for src/test/pkg/PendingIntentTest.kt line 7: Add FLAG_MUTABLE:
                @@ -7 +7
                -         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT)
                +         PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
                Fix for src/test/pkg/PendingIntentTest.kt line 8: Add FLAG_IMMUTABLE (preferred):
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE)
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                Fix for src/test/pkg/PendingIntentTest.kt line 8: Add FLAG_MUTABLE:
                @@ -8 +8
                -         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE)
                +         PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE)
                """
            )
    }

    fun testImmutableFlag() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    void test() {
                        PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_IMMUTABLE);
                        PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE |
                            PendingIntent.FLAG_IMMUTABLE);
                    }
                }

                """
                ).indented()
            )
        ).run().expectClean()
    }

    fun testImmutableFlagKotlin() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                kotlin(
                    """
                package test.pkg

                import android.app.PendingIntent

                class PendingIntentTest {
                    fun test() {
                        PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_IMMUTABLE)
                        PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE or
                            PendingIntent.FLAG_IMMUTABLE)
                    }
                }

                """
                ).indented()
            )
        ).run().expectClean()
    }

    fun testMutableFlag() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    protected void test() {
                        PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_MUTABLE);
                        PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE |
                            PendingIntent.FLAG_MUTABLE);
                    }
                }

                """
                ).indented()
            )
        ).run().expectClean()
    }

    fun testFlagsVariable() {
        // Regression test for https://issuetracker.google.com/197179112
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;
                import android.os.Build;

                class TestClass {
                    void test() {
                        var pendingFlags;
                        if (Build.VERSION.SDK_INT >= 23) {
                            pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
                        } else {
                            pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                        }
                        PendingIntent.getBroadcast(null, 0, null, pendingFlags);
                    }
                }
                """
                ).indented()
            )
        ).run().expectClean()
    }

    fun testFlagsVariableKt() {
        // Regression test for https://issuetracker.google.com/197179112
        lint().projects(
            project(
                manifest().targetSdk(31),
                kotlin(
                    """
                package test.pkg

                import android.app.PendingIntent
                import android.os.Build

                class TestClass {
                    fun test() {
                        var pendingFlags = 0
                        if (Build.VERSION.SDK_INT >= 23) {
                            pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE;
                        } else {
                            pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        PendingIntent.getBroadcast(null, 0, null, pendingFlags)
                    }
                }
                """
                ).indented()
            )
        ).run().expectClean()
    }

    fun testFlagsVariableTernary() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;
                import android.content.BroadcastReceiver;
                import android.content.Context;

                class TestClass extends BroadcastReceiver {
                    private final Context appContext;
                    private PendingIntent createOnDismissedIntent() {
                        pendingIntent =
                            PendingIntent.getBroadcast(
                                appContext,
                                0 /*requestCode*/,
                                new Intent("test.pkg.INTENT_ACTION"),
                                BuildCompat.isAtLeastS()
                                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                                    : 0);
                        return pendingIntent;
                    }
                }
                """
                ).indented()
            )
        )
            .run()
            .expectClean()
    }

    fun testFlagsHelperFunction() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;
                import android.os.Build;

                class TestClass {
                    void test() {
                        PendingIntent.getBroadcast(null, 0, null, getIntentFlags());
                    }

                    private static int getIntentFlags() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                          return PendingIntent.FLAG_IMMUTABLE;
                        } else {
                          return 0;
                        }
                    }
                }
                """
                ).indented()
            )
        )
            .run()
            .expectClean()
    }

    fun testFlagsArgument() {
        lint().projects(
            project(
                manifest().targetSdk(31),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;
                import android.os.Build;

                class TestClass {
                    @Nullable
                    public static PendingIntent getActivityUnsafe(
                          Context context,
                          int requestCode,
                          Intent intent,
                          int flags,
                          @MutableFlags int mutabilityFlags) {
                        return PendingIntent.getActivity(context, requestCode, filledIntent, flags);
                    }
                }
                """
                ).indented()
            )
        )
            .run()
            .expectClean()
    }

    fun testTargetSdkBelowThirtyOneIsWarning() {
        lint().projects(
            project(
                manifest().targetSdk(30),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    protected void test() {
                        PendingIntent.getActivity(null, 0, null, 0);
                        PendingIntent.getActivities(null, 0, null, 0);
                    }
                }
                """
                ).indented()
            )
        )
            .run()
            .expectErrorCount(0)
            .expectWarningCount(2)
    }

    fun testTargetSdkBelowTwentyThreeIsClean() {
        lint().projects(
            project(
                manifest().targetSdk(22),
                java(
                    """
                package test.pkg;

                import android.app.PendingIntent;

                public class PendingIntentTest {
                    protected void test() {
                        PendingIntent.getActivity(null, 0, null, 0);
                        PendingIntent.getActivities(null, 0, null, 0);
                    }
                }
                """
                ).indented()
            )
        )
            .run()
            .expectClean()
    }
}
