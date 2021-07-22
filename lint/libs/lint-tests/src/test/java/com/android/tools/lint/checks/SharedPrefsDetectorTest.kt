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

class SharedPrefsDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = SharedPrefsDetector()

    fun testGetStringSet() {
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences
                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(Build.VERSION_CODES.N)
                fun editSet(prefs: SharedPreferences) {
                    val s = prefs.getStringSet("key", null) // ?: return
                    s ?: return
                    s.removeIf { it.length < 3 }
                    s.add("error")

                    val t = mutableSetOf<String>()
                    t.add("ok")
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:11: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                s.removeIf { it.length < 3 }
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:12: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                s.add("error")
                ~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }

    fun testElvisOperator() {
        // Fix bug in data flow analyzer which was not handling the elvis operator in this
        // unit test correctly
        lint().files(
            kotlin(
                """
                package test.pkg

                import android.content.SharedPreferences
                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(Build.VERSION_CODES.N)
                fun editSet(prefs: SharedPreferences) {
                    val s = prefs.getStringSet("key", null) ?: return
                    s.add("error")
                }
                """
            ).indented(),
            SUPPORT_ANNOTATIONS_JAR
        ).run().expect(
            """
            src/test/pkg/test.kt:10: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                s.add("error")
                ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }

    fun test187437289() {
        // Regression test for
        // 187437289: MutatingSharedPrefs false positive
        lint().files(
            kotlin(
                """
                package com.example.myapplication

                class SharedPrefsTest {
                    fun test(sharedPrefs: android.content.SharedPreferences) {
                        val modified = mutableSetOf<String>().apply {
                            sharedPrefs.getStringSet("key", null)
                                ?.let { old ->
                                    addAll(old)
                                }
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testReassigned() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.SharedPreferences;
                import android.util.ArraySet;

                import java.util.Collections;
                import java.util.Set;

                public class SharedPrefsEditTest {
                    private SharedPreferences mPreferences;

                    private void setDenied(String roleName, boolean denied, String key) {
                        Set<String> roleNames = mPreferences.getStringSet(key, Collections.emptySet());
                        roleNames = new ArraySet<>(roleNames);
                        if (denied) {
                            roleNames.add(roleName); // OK 1
                        }
                    }

                    private void setDenied2(String roleName, boolean denied, String key) {
                        Set<String> roleNames = mPreferences.getStringSet(key, Collections.emptySet());
                        if (true) {
                            // Assignment in different nested scope but no other usage so we
                            // conclude we can stop tracking the roleNames array
                            roleNames = new ArraySet<>(roleNames);
                            if (denied) {
                                roleNames.add(roleName); // OK 2
                            }
                        }
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun testReassignedStillBroken() {
        // Test for a couple more scenarios with reassignment which we're not handling
        // correctly yet
        lint().files(
            java(
                """
                package test.pkg;

                import android.content.SharedPreferences;
                import android.util.ArraySet;

                import java.util.Collections;
                import java.util.Set;

                public class SharedPrefsEditTest {
                    private SharedPreferences mPreferences;

                    private void setDenied3(String roleName, boolean denied, String key) {
                        Set<String> roleNames = mPreferences.getStringSet(key, Collections.emptySet());
                        if (true) {
                            // Like setDenied2 but here there's a later usage so we need to
                            // keep tracking it
                            roleNames = new ArraySet<>(roleNames);
                            if (denied) {
                                roleNames.add(roleName); // OK (but not yet analyzed correctly)
                            }
                        }
                        boolean x = roleNames.contains("x"); // reference after reassignment that my apply
                    }

                    private void setDenied4(String roleName, boolean denied, String key) {
                        Set<String> roleNames = null;
                        if (true) {
                            roleNames = mPreferences.getStringSet(key, Collections.emptySet());
                        } else {
                            roleNames = new ArraySet<>(roleNames);
                            if (denied) {
                                roleNames.add(roleName); // OK (but not yet analyzed correctly)
                            }
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/SharedPrefsEditTest.java:19: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                            roleNames.add(roleName); // OK (but not yet analyzed correctly)
                            ~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/SharedPrefsEditTest.java:32: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                            roleNames.add(roleName); // OK (but not yet analyzed correctly)
                            ~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
        )
    }
}
