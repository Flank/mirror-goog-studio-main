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

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.portablePath
import com.android.tools.lint.client.api.LintXmlConfiguration
import com.android.tools.lint.client.api.UElementHandler
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

@Suppress("LintDocExample")
class OptionTest {
    companion object {
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
                TestOptionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).setOptions(listOf(booleanOption, stringOption, stringOptionNoDefault, intOption, floatOption, fileOption))
    }

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

    @Test
    fun testDetectorOption() {
        val testSource = kotlin(
            """
                fun test() {
                    val array = arrayOf(
                        "ignore-deprecated",
                        "namePrefix",
                        "suffix",
                        "maxCount",
                        "duration",
                        "exclude"
                    )
                }
                """
        )

        // Check that all the default values are returned when nothing is configured
        lint().files(testSource).issues(issue).run().expect(
            """
            src/test.kt:4: Warning: Option ignore-deprecated has value false (default) [_TestIssue]
                                    "ignore-deprecated",
                                     ~~~~~~~~~~~~~~~~~
            src/test.kt:5: Warning: Option namePrefix has value my (default) [_TestIssue]
                                    "namePrefix",
                                     ~~~~~~~~~~
            src/test.kt:6: Warning: Option suffix has value null (default) [_TestIssue]
                                    "suffix",
                                     ~~~~~~
            src/test.kt:7: Warning: Option maxCount has value 20 (default) [_TestIssue]
                                    "maxCount",
                                     ~~~~~~~~
            src/test.kt:8: Warning: Option duration has value 1.5 (default) [_TestIssue]
                                    "duration",
                                     ~~~~~~~~
            src/test.kt:9: Warning: Option exclude has value path/default-excludes.txt (default) [_TestIssue]
                                    "exclude"
                                     ~~~~~~~
            0 errors, 6 warnings
            """
        )

        // Check that configured values are used
        lint().files(testSource).issues(issue)
            .configureOption(stringOption, "some string")
            .configureOption(booleanOption, true)
            .configureOption(stringOptionNoDefault, "some other string")
            .configureOption(intOption, 42)
            .configureOption(floatOption, 5.0f)
            .configureOption(fileOption, File("something.txt"))
            .run().expect(
                """
                src/test.kt:4: Warning: Option ignore-deprecated has value true (default is false) [_TestIssue]
                                        "ignore-deprecated",
                                         ~~~~~~~~~~~~~~~~~
                src/test.kt:5: Warning: Option namePrefix has value some string (default is my) [_TestIssue]
                                        "namePrefix",
                                         ~~~~~~~~~~
                src/test.kt:6: Warning: Option suffix has value some other string (default is null) [_TestIssue]
                                        "suffix",
                                         ~~~~~~
                src/test.kt:7: Warning: Option maxCount has value 42 (default is 20) [_TestIssue]
                                        "maxCount",
                                         ~~~~~~~~
                src/test.kt:8: Warning: Option duration has value 5.0 (default is 1.5) [_TestIssue]
                                        "duration",
                                         ~~~~~~~~
                src/test.kt:9: Warning: Option exclude has value something.txt (default is path/default-excludes.txt) [_TestIssue]
                                        "exclude"
                                         ~~~~~~~
                0 errors, 6 warnings
                """
            )
    }

    @Test
    fun testValidateRange() {
        val testSource = kotlin(
            """
                @Suppress("_TestIssue")
                fun test() {
                    val array = arrayOf( "maxCount", "duration" )
                }
                """
        )

        // Check that configured values are used
        lint().files(
            testSource,
            xml(
                "lint.xml",
                """
                    <lint>
                        <issue id="_TestIssue">
                            <option name="maxCount" value="0" />
                            <option name="duration" value="15.0" />
                        </issue>
                    </lint>
                    """
            ).indented()
        ).issues(issue).run().expect(
            """
            lint.xml:3: Error: maxCount: Must be at least 10 and less than 50 [LintError]
                    <option name="maxCount" value="0" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            lint.xml:4: Error: duration: Must be less than 15.0 [LintError]
                    <option name="duration" value="15.0" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun testValidateValues() {
        val testSource = kotlin(
            """
                package test.pkg
                @Suppress("_TestIssue")
                fun test() {
                    val array = arrayOf(
                        "ignore-deprecated",
                        "maxCount",
                        "duration"
                    )
                }
                """
        )

        // Check that configured values are used
        lint().files(
            testSource,
            xml(
                "src/test/pkg/lint.xml",
                """
                <lint>
                    <issue id="TooManyViews">
                        <option name="maxCount" value="20" />
                    </issue>
                    <issue id="_TestIssue">
                        <option name="ignore-deprecated" value="enabled" />
                    </issue>
                </lint>
                """
            ).indented(),
            xml(
                "lint.xml",
                """
                    <lint>
                        <issue id="_TestIssue">
                            <option name="suffix" />
                            <option name="namePrefix" value="" />
                            <option name="maxCount" value="true" />
                            <option name="duration" value="false" />
                        </issue>
                    </lint>
                    """
            ).indented()
        ).issues(issue).run().expect(
            """
            lint.xml:5: Error: maxCount must be an integer (was true) [LintError]
                    <option name="maxCount" value="true" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/lint.xml:6: Error: Option value must be true or false (was enabled) [LintError]
                    <option name="ignore-deprecated" value="enabled" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            lint.xml:6: Error: duration must be a float (was false) [LintError]
                    <option name="duration" value="false" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            lint.xml:3: Warning: Must specify both name and value in <option> [LintWarning]
                    <option name="suffix" />
                    ^
            lint.xml:4: Warning: Must specify both name and value in <option> [LintWarning]
                    <option name="namePrefix" value="" />
                    ^
            3 errors, 2 warnings
            """
        )
    }

    @Test
    fun testNotCombinedCheck() {
        // Check that the lint task complains if you try to mix and match lint.xml and configureOption
        val lintXml = xml("lint.xml", "<lint/>")
        try {
            lint().files(kotlin(""), lintXml).issues(issue).configureOption(stringOption, "some string").run().expectClean()
            fail("Expected combining configureOption and lint.xml to fail the test")
        } catch (e: Throwable) {
            assertEquals(
                "Cannot combine lint.xml with `configureOption`; add options as <option> elements in your custom lint.xml instead",
                e.message
            )
        }
    }

    private fun lint() = TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile()).testModes(TestMode.DEFAULT)

    // Detector which reproduces problem in issue https://issuetracker.google.com/116838536
    class TestOptionDetector : Detector(), SourceCodeScanner {
        init {
            LintXmlConfiguration.warnedOptions = null
        }

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(ULiteralExpression::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler =
            object : UElementHandler() {
                override fun visitLiteralExpression(node: ULiteralExpression) {
                    val string = node.evaluateString()
                    for (option in issue.getOptions()) {
                        if (option.name == string) {
                            val value = getValue(option)
                            val default = getDefault(option)
                            val description = if (value == default) "default" else "default is $default"
                            val message = "Option $string has value $value ($description)"
                            context.report(issue, context.getLocation(node), message)
                        }
                    }
                }

                private fun getDefault(option: Option): String? {
                    val default = option.defaultAsString() ?: return null
                    if (default == "null") {
                        return null
                    }
                    if (option is FileOption) {
                        return default.portablePath()
                    }
                    return default.removePrefix("\"").removeSuffix("\"")
                }

                private fun getValue(option: Option): String? {
                    val value = option.getValue(context) ?: return null
                    if (value is File) {
                        return context.project.getDisplayPath(value)
                    }
                    return value.toString()
                }
            }
    }
}
