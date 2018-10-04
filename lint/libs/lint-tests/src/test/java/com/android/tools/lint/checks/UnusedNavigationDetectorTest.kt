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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class UnusedNavigationDetectorTest : AbstractCheckTest() {
    fun testMissing() {
        lint().files(
            xml(
                "res/navigation/used.xml", "" +
                        "<navigation />"
            ),
            xml(
                "res/layout/mylayout2.xml", "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                        "</LinearLayout>"
            ),
            xml(
                "res/layout/mylayout.xml", "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                        "\n" +
                        "    <fragment\n" +
                        "        android:id=\"@+id/fragment3\"\n" +
                        "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                        "        app:defaultNavHost=\"true\"\n" +
                        "        app:navGraph=\"@navigation/other_name\" />\n" +
                        "\n" +
                        "</LinearLayout>"
            )
        ).incremental("res/navigation/used.xml").run().expect(
            "" +
                    "res/navigation/used.xml:1: Error: This navigation graph is not referenced from any layout files (expected to find it in at least one layout file with a NavHostFragment with app:navGraph=\"@navigation/used\" attribute). [UnusedNavigation]\n" +
                    "<navigation />\n" +
                    "~~~~~~~~~~~~~~\n" +
                    "1 errors, 0 warnings"
        )
    }

    fun testOk() {
        lint().files(
            xml(
                "res/navigation/used.xml", "" +
                        "<navigation />"
            ),
            xml(
                "res/layout/mylayout2.xml", "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                        "</LinearLayout>"
            ),
            xml(
                "res/layout/mylayout.xml", "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                        "\n" +
                        "    <fragment\n" +
                        "        android:id=\"@+id/fragment3\"\n" +
                        "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                        "        app:defaultNavHost=\"true\"\n" +
                        "        app:navGraph=\"@navigation/used\" />\n" +
                        "\n" +
                        "</LinearLayout>"
            )
        ).incremental("res/navigation/used.xml").run().expectClean()
    }

    override fun getDetector(): Detector {
        return UnusedNavigationDetector()
    }
}
