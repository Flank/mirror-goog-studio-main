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

import com.android.tools.lint.detector.api.Detector

class MergeMarkerDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return MergeMarkerDetector()
    }

    fun testMergeMarker() {
        // Make sure we don't try to read binary contents
        lint().files(
            source( // instead of xml: not valid XML below
                "res/values/strings.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>

                    <string name="app_name">LibraryProject</string>
                <<<<<<< HEAD
                    <string name="string1">String 1</string>
                =======
                    <string name="string2">String 2</string>
                >>>>>>> branch-a
                    <string name="string3">String 3</string>

                </resources>
                """
            ).indented(),
            // Make sure we don't try to read binary contents
            source(
                "res/drawable-mdpi/my_icon.png",
                """
                <<<<<<< HEAD
                =======
                >>>>>>> branch-a
                </resources>
                """
            ).indented()
        ).run().expect(
            """
            res/values/strings.xml:5: Error: Missing merge marker? [MergeMarker]
            <<<<<<< HEAD
            ~~~~~~~
            res/values/strings.xml:7: Error: Missing merge marker? [MergeMarker]
            =======
            ~~~~~~~
            res/values/strings.xml:9: Error: Missing merge marker? [MergeMarker]
            >>>>>>> branch-a
            ~~~~~~~
            3 errors, 0 warnings
            """
        )
    }
}
