/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import junit.framework.TestCase.assertEquals
import org.junit.Test

class CharacterDataTestModeTest {
    private fun transform(testFile: TestFile): String {
        val contents = testFile.contents
        return CharacterDataTestMode().transform(contents)
    }

    @Test
    fun testBasic() {
        val before = xml(
            "res/values/strings.xml",
            """
            <resources>
                <dimen name="something">42dp</dimen>
                <string-array>
                    <string name="name1">My name</string>
                    <string name="name1">My <b>label</b></string>
                    <string name="name2">@string/name1</string>
                    <string name="name2"><![CDATA[Already cdata]]></string>
                </string-array>
                <string name="name1">
                    Other
                </string>
            </resources>
            """
        ).indented()

        val after = xml(
            "res/values/strings.xml",
            """
            <resources>
                <dimen name="something">42dp</dimen>
                <string-array>
                    <string name="name1"><![CDATA[My name]]></string>
                    <string name="name1">My <b>label</b></string>
                    <string name="name2">@string/name1</string>
                    <string name="name2"><![CDATA[Already cdata]]></string>
                </string-array>
                <string name="name1"><![CDATA[
                    Other
                ]]></string>
            </resources>
            """
        ).indented()

        val modified = transform(before)
        assertEquals(after.contents, modified)
    }

    @Suppress("CheckTagEmptyBody")
    @Test
    fun testEmpty() {
        val before = xml(
            "res/values/strings.xml",
            """
              <resources>
                <string name="test"></string>
              </resources>
            """
        ).indented()

        val after = xml(
            "res/values/strings.xml",
            """
            <resources>
              <string name="test"><![CDATA[]]></string>
            </resources>
            """
        ).indented()

        val modified = transform(before)
        assertEquals(after.contents, modified)
    }
}
