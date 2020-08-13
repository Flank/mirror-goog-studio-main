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
                package test.pkg;

                import android.content.SharedPreferences
                import android.os.Build
                import android.support.annotation.RequiresApi

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
            ).indented()
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
                package test.pkg;

                import android.content.SharedPreferences
                import android.os.Build
                import android.support.annotation.RequiresApi

                @RequiresApi(Build.VERSION_CODES.N)
                fun editSet(prefs: SharedPreferences) {
                    val s = prefs.getStringSet("key", null) ?: return
                    s.add("error")
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/test.kt:10: Warning: Do not modify the set returned by SharedPreferences.getStringSet()` [MutatingSharedPrefs]
                s.add("error")
                ~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
        )
    }
}
