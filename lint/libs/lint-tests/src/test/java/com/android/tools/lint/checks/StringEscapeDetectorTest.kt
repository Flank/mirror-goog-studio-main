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

class StringEscapeDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return StringEscapeDetector()
    }

    fun testDocumentationExampleStringEscaping() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=224150
        // 224150: Flag apostrophes escaping in XML string resources
        val expected = """
            res/values/strings.xml:3: Error: Apostrophe not preceded by \ [StringEscaping]
            <string name="some_string">'ERROR'</string>
                                       ^
            res/values/strings.xml:5: Error: Apostrophe not preceded by \ [StringEscaping]
            <string name="some_string3">What's New</string>
                                            ^
            res/values/strings.xml:12: Error: Bad character in \u unicode escape sequence [StringEscaping]
            <string name="some_string10">Unicode\u12.</string>
                                                    ^
            res/values/strings.xml:19: Error: Apostrophe not preceded by \ [StringEscaping]
              <item>It's incorrect</item>
                      ^
            res/values/strings.xml:23: Error: Apostrophe not preceded by \ [StringEscaping]
                <item quantity="few">%d piose'nki.</item>
                                             ^
            5 errors, 0 warnings"""
        lint().files(
            xml(
                "res/values/strings.xml",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="some_string">'ERROR'</string>
                    <string name="some_string2">"'OK'"</string>
                    <string name="some_string3">What's New</string>
                    <string name="some_string4">Unfinished\</string>
                    <string name="some_string5">Unicode\u</string>
                    <string name="some_string6">Unicode\u1</string>
                    <string name="some_string7">Unicode\u12</string>
                    <string name="some_string8">Unicode\u123</string>
                    <string name="some_string9">Unicode\u1234</string>
                    <string name="some_string10">Unicode\u12.</string>
                    <string name="news">  "  What's New "    </string>
                    <string name="space_slash"> \</string>
                    <string name="space_slash2">  \</string>
                    <string name="space_slash3">   \</string>
                    <string-array name="array_of_string">
                      <item>It\'s correct</item>
                      <item>It's incorrect</item>
                    </string-array>
                    <plurals name="numberOfSongsAvailable">
                        <item quantity="one">%d piosenkę.</item>
                        <item quantity="few">%d piose'nki.</item>
                        <item quantity="other">%d piosenek.</item>
                    </plurals>
                    </resources>
                    """
            ).indented()
        )
            .run()
            .expect(expected)
            .expectFixDiffs(
                """
                    Fix for res/values/strings.xml line 3: Escape Apostrophe:
                    @@ -3 +3
                    - <string name="some_string">'ERROR'</string>
                    + <string name="some_string">\'ERROR'</string>
                    Fix for res/values/strings.xml line 5: Escape Apostrophe:
                    @@ -5 +5
                    - <string name="some_string3">What's New</string>
                    + <string name="some_string3">What\'s New</string>
                    Fix for res/values/strings.xml line 19: Escape Apostrophe:
                    @@ -19 +19
                    -   <item>It's incorrect</item>
                    +   <item>It\'s incorrect</item>
                    Fix for res/values/strings.xml line 23: Escape Apostrophe:
                    @@ -23 +23
                    -     <item quantity="few">%d piose'nki.</item>
                    +     <item quantity="few">%d piose\'nki.</item>"""
            )
    }
}
