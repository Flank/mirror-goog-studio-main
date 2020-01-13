/*
 * Copyright (C) 2014 The Android Open Source Project
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

class NfcTechListDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return NfcTechListDetector()
    }

    fun test() {
        lint().files(
            xml(
                "res/xml/nfc_tech_list_formatted.xml",
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2" >

                    <!-- capture anything using NfcF -->
                    <tech-list>
                        <tech>
                android.nfc.tech.NfcA
                        </tech>
                    </tech-list>
                    <!-- OR -->
                    <tech-list>
                        <tech>
                android.nfc.tech.MifareUltralight
                        </tech>
                    </tech-list>
                    <!-- OR -->
                    <tech-list>
                        <tech>
                android.nfc.tech.ndefformatable
                        </tech>
                    </tech-list>

                </resources>
                """
            ).indented()
        ).run().expect(
            """
            res/xml/nfc_tech_list_formatted.xml:6: Error: There should not be any whitespace inside <tech> elements [NfcTechWhitespace]
            android.nfc.tech.NfcA
            ^
            res/xml/nfc_tech_list_formatted.xml:12: Error: There should not be any whitespace inside <tech> elements [NfcTechWhitespace]
            android.nfc.tech.MifareUltralight
            ^
            res/xml/nfc_tech_list_formatted.xml:18: Error: There should not be any whitespace inside <tech> elements [NfcTechWhitespace]
            android.nfc.tech.ndefformatable
            ^
            3 errors, 0 warnings
            """
        )
    }

    fun testOk() {
        //noinspection all // Sample code
        lint().files(
            xml(
                "res/xml/nfc_tech_list.xml",
                """
                 <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                     <!-- capture anything using NfcF -->
                     <tech-list>
                         <tech>android.nfc.tech.NfcA</tech>
                     </tech-list>
                     <!-- OR -->
                     <tech-list>
                          <tech>android.nfc.tech.MifareUltralight</tech>
                     </tech-list>
                     <!-- OR -->
                      <tech-list>
                          <tech>android.nfc.tech.ndefformatable</tech>
                     </tech-list>
                 </resources>
                """
            ).indented()
        ).run().expectClean()
    }
}
