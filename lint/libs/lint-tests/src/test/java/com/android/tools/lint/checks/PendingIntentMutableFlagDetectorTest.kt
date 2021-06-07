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
        lint().files(
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
        ).run().expect(
            """
                src/test/pkg/PendingIntentTest.java:7: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                        PendingIntent.getActivity(null, 0, null, 0);
                                                                 ~
                src/test/pkg/PendingIntentTest.java:8: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                        PendingIntent.getActivities(null, 0, null, 0);
                                                                   ~
                0 errors, 2 warnings
            """
        )
    }

    fun testNoImmutableFlag() {
        lint().files(
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
        ).run().expect(
            """
            src/test/pkg/PendingIntentTest.java:7: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT);
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/PendingIntentTest.java:8: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE);
                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testNoImmutableFlagKotlin() {
        lint().files(
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
        ).run().expect(
            """
            src/test/pkg/PendingIntentTest.kt:7: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivity(null, 0, null, PendingIntent.FLAG_CANCEL_CURRENT)
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/PendingIntentTest.kt:8: Warning: Missing PendingIntent mutability flag [UnspecifiedImmutableFlag]
                    PendingIntent.getActivities(null, 0, null, PendingIntent.FLAG_NO_CREATE)
                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testImmutableFlag() {
        lint().files(
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
        ).run().expect("No warnings.")
    }

    fun testImmutableFlagKotlin() {
        lint().files(
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
        ).run().expectClean()
    }

    fun testMutableFlag() {
        lint().files(
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
        ).run().expectClean()
    }
}
