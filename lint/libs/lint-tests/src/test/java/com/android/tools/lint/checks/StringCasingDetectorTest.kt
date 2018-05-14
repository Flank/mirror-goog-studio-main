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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector

class StringCasingDetectorTest : AbstractCheckTest() {

    private val duplicateStrings = LintDetectorTest.xml(
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
        val expected = ("""
                res/values/duplicate_strings.xml:3: Warning: Duplicate string detected hello; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="hello">hello</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values/duplicate_strings.xml:4: Warning: Duplicate string detected HELLO; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="hello_caps">HELLO</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values/duplicate_strings.xml:5: Warning: Duplicate string detected hello world; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="hello_world">hello world</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values/duplicate_strings.xml:6: Warning: Duplicate string detected Hello World; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="title_casing_hello_world">Hello World</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """)
        lint().files(duplicateStrings).run().expect(expected)
    }

    private val turkishNonDuplicateStrings = LintDetectorTest.xml(
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

    private val turkishDuplicateStrings = LintDetectorTest.xml(
        "res/values-tr/duplicate_strings.xml",
        """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="hello">hello i</string>
                    <string name="hello_caps">hello İ</string>
                </resources>
                """
    )

    fun testTurkishDuplicateStrings() {
        val expected = ("""
                res/values-tr/duplicate_strings.xml:3: Warning: Duplicate string detected hello i; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="hello">hello i</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                res/values-tr/duplicate_strings.xml:4: Warning: Duplicate string detected hello İ; Please use android:inputType or android:capitalize to avoid string duplication in resources. [DuplicateStrings]
                                    <string name="hello_caps">hello İ</string>
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """)
        lint().files(turkishDuplicateStrings).run().expect(expected)
    }
}
