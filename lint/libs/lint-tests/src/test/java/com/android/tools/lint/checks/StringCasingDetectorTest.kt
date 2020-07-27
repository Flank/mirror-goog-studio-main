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

class StringCasingDetectorTest : AbstractCheckTest() {

    private val duplicateStrings = xml(
        "res/values/duplicate_strings.xml",
        """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello">hello</string>
                    <string name="hello_caps">HELLO</string>
                    <string name="hello_world">hello world</string>
                    <string name="title_casing_hello_world">Hello World</string>
                </resources>
                """
    )

    override fun getDetector(): Detector {
        return StringCasingDetector()
    }

    fun testDuplicateStrings() {
        val expected =
            """
                res/values/duplicate_strings.xml:3: Warning: Duplicate string value HELLO, used in hello_caps and hello. Use android:inputType or android:capitalize to treat these as the same and avoid string duplication. [DuplicateStrings]
                                    <string name="hello">hello</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    res/values/duplicate_strings.xml:4: Duplicates value in hello (case varies, but you can use android:inputType or android:capitalize in the presentation)
                res/values/duplicate_strings.xml:5: Warning: Duplicate string value Hello World, used in hello_world and title_casing_hello_world. Use android:inputType or android:capitalize to treat these as the same and avoid string duplication. [DuplicateStrings]
                                    <string name="hello_world">hello world</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    res/values/duplicate_strings.xml:6: Duplicates value in hello_world (case varies, but you can use android:inputType or android:capitalize in the presentation)
                0 errors, 2 warnings
                """
        lint().files(duplicateStrings).run().expect(expected)
    }

    private val turkishNonDuplicateStrings = xml(
        "res/values-tr/duplicate_strings.xml",
        """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello i">hello i</string>
                    <string name="hello_cap">hello I</string>
                </resources>
                """
    )

    fun testTurkishNonDuplicateStrings() {
        lint().files(turkishNonDuplicateStrings).run().expectClean()
    }

    private val turkishDuplicateStrings = xml(
        "res/values-tr/duplicate_strings.xml",
        """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello">hello i</string>
                    <string name="hello_caps">hello İ</string>
                </resources>
                """
    )

    fun testTurkishDuplicateStrings() {
        val expected =
            """
                res/values-tr/duplicate_strings.xml:3: Warning: Duplicate string value hello İ, used in hello_caps and hello. Use android:inputType or android:capitalize to treat these as the same and avoid string duplication. [DuplicateStrings]
                                    <string name="hello">hello i</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    res/values-tr/duplicate_strings.xml:4: Duplicates value in hello (case varies, but you can use android:inputType or android:capitalize in the presentation)
                0 errors, 1 warnings
                """
        lint().files(turkishDuplicateStrings).run().expect(expected)
    }

    fun testDuplicatesWithoutCaseDifferences() {
        val expected =
            """
                res/values/duplicate_strings.xml:3: Warning: Duplicate string value Hello, used in hello1, hello2 and hello3 [DuplicateStrings]
                                    <string name="hello1">Hello</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    res/values/duplicate_strings.xml:4: Duplicates value in hello1
                    res/values/duplicate_strings.xml:5: Duplicates value in hello1
                0 errors, 1 warnings
                """
        lint().files(
            xml(
                "res/values/duplicate_strings.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello1">Hello</string>
                    <string name="hello2">Hello</string>
                    <string name="hello3">Hello</string>
                </resources>
                """
            )
        ).run().expect(expected)
    }

    fun testIgnoredNonTranslatable() {
        // Regression test for
        // https://issuetracker.google.com/112492581
        lint().files(
            xml(
                "res/values/duplicate_strings.xml",
                """
                <resources>
                    <string name="off">off</string>
                    <string translatable="false" name="off_debug">Off</string>
                </resources>
                """
            ).indented()
        ).run().expectClean()
    }

    fun testCharacterData() {
        // Regression test for
        // https://issuetracker.google.com/142533357: Duplicate string doesn't work with CDATA
        lint().files(
            xml(
                "res/values/duplicate_strings.xml",
                "<resources>\n" +
                    "    <string name=\"app_name\">lint bug</string>\n" +
                    "    <string name=\"item_one\"><![CDATA[<b>%1$\\s</b>]]> did something</string>\n" +
                    "    <string name=\"item_two\"><![CDATA[<b>You</b>]]> did something <![CDATA[<b>%1$\\s</b>]]></string>\n" +
                    "</resources>"
            ).indented()
        ).run().expectClean()
    }
}
