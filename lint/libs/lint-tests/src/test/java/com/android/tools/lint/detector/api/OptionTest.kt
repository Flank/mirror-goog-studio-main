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
package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.SdCardDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OptionTest {
    private val booleanOption = BooleanOption(
        "ignore-deprecated",
        "Whether to ignore classes and members that have been annotated with `@Deprecated`",
        false,
        """
                Normally this lint check will flag all unannotated elements, but by \
                setting this option to `true` it will skip any deprecated elements.
                """
    )
    private val stringOption = StringOption(
        "namePrefix",
        "Prefix to prepend to suggested names",
        "my"
    )
    private val stringOptionNoDefault = StringOption(
        "suffix",
        "Suggested name suffix"
    )
    private val intOption = IntOption(
        "maxCount",
        "Maximum number of elements",
        20,
        min = 10,
        max = 50
    )
    private val floatOption = FloatOption(
        "duration",
        "Expected duration",
        1.5f,
        max = 15f
    )
    private val fileOption = FileOption(
        "exclude",
        "File listing names to be excluded",
        File("path/default-excludes.txt")
    )

    @Suppress("unused") // registration has side effect of initializing options
    private val issue = Issue.create(
        id = "_TestIssue",
        briefDescription = "Sample issue associated with tested option",
        explanation = "Not applicable",
        category = Category.TESTING, priority = 10, severity = Severity.WARNING,
        implementation = Implementation(
            SdCardDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )
    ).setOptions(listOf(booleanOption, stringOption, stringOptionNoDefault, intOption, floatOption, fileOption))

    @Test
    fun testDescribe() {
        assertEquals(
            """
            namePrefix (default is "my"): Prefix to prepend to suggested names.
            """.trimIndent(),
            stringOption.describe(TextFormat.TEXT, includeExample = false).trim()
        )

        assertEquals(
            """
            **suffix**: Suggested name suffix.
            """.trimIndent(),
            stringOptionNoDefault.describe(TextFormat.RAW, includeExample = false).trim()
        )

        assertEquals(
            """
            **exclude** (default is `path/default-excludes.txt`):
            File listing names to be excluded.
            """.trimIndent(),
            fileOption.describe(TextFormat.RAW, includeExample = false).trim().replace('\\', '/')
        )

        assertEquals(
            """
            maxCount (default is 20): Maximum number of elements.
            Must be at least 10 and less than 50.
            """.trimIndent(),
            intOption.describe(TextFormat.TEXT, includeExample = false).trim().replace('\\', '/')
        )

        assertEquals(
            """
            duration (default is 1.5): Expected duration.
            Must be less than 15.0.
            """.trimIndent(),
            floatOption.describe(TextFormat.TEXT, includeExample = false).trim().replace('\\', '/')
        )

        assertEquals(
            """
            ignore-deprecated (default is false):
            Whether to ignore classes and members that have been annotated with @Deprecated.

            Normally this lint check will flag all unannotated elements, but by
            setting this option to true it will skip any deprecated elements.

            To configure this option, use a lint.xml file with an <option> like this:

            <lint>
                <issue id="_TestIssue">
                    <option name="ignore-deprecated" value="false" />
                </issue>
            </lint>
            """.trimIndent(),
            booleanOption.describe(TextFormat.TEXT).trim()
        )

        assertEquals(
            """
            <b>ignore-deprecated</b> (default is false):<br/>
            Whether to ignore classes and members that have been annotated with <code>@Deprecated</code>.<br/>
            <br/>
            Normally this lint check will flag all unannotated elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/>
            <br/>
            To configure this option, use a <code>lint.xml</code> file with an &lt;option> like this:<br/>

            <pre>
            &lt;lint>
                &lt;issue id="_TestIssue">
                    &lt;option name="ignore-deprecated" value="false" />
                &lt;/issue>
            &lt;/lint>
            </pre>
            """.trimIndent(),
            booleanOption.describe(TextFormat.HTML).trim()
        )
    }

    @Test
    fun testDescribeList() {
        assertEquals(
            """
            Available options:<br/>
            <br/>
            <b>maxCount</b> (default is 20): Maximum number of elements.<br/>
            Must be at least 10 and less than 50.<br/>
            <br/>
            To configure this option, use a <code>lint.xml</code> file with an &lt;option> like this:<br/>

            <pre>
            &lt;lint>
                &lt;issue id="_TestIssue">
                    &lt;option name="maxCount" value="20" />
                &lt;/issue>
            &lt;/lint>
            </pre>
            <br/>
            <b>ignore-deprecated</b> (default is false):<br/>
            Whether to ignore classes and members that have been annotated with <code>@Deprecated</code>.<br/>
            <br/>
            Normally this lint check will flag all unannotated elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/>
            <br/>
            To configure this option, use a <code>lint.xml</code> file with an &lt;option> like this:<br/>

            <pre>
            &lt;lint>
                &lt;issue id="_TestIssue">
                    &lt;option name="ignore-deprecated" value="false" />
                &lt;/issue>
            &lt;/lint>
            </pre>
            """.trimIndent(),
            Option.describe(listOf(intOption, booleanOption), TextFormat.HTML).trim()
        )
    }

    @Test
    fun testDefault() {
        assertEquals(false, booleanOption.defaultValue)
        assertEquals("false", booleanOption.defaultAsString())
        assertEquals(20, intOption.defaultValue)
        assertEquals("20", intOption.defaultAsString())
        assertEquals(false, booleanOption.defaultValue)
        assertEquals("false", booleanOption.defaultAsString())
        assertEquals("my", stringOption.defaultValue)
        assertEquals("\"my\"", stringOption.defaultAsString())
        assertEquals(null, stringOptionNoDefault.defaultValue)
        assertEquals(null, stringOptionNoDefault.defaultAsString())
        assertEquals("path/default-excludes.txt", fileOption.defaultAsString()?.replace('\\', '/'))
    }
}
