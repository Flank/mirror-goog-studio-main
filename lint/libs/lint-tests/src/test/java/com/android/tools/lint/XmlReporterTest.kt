/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.EllipsizeMaxLinesDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.TypoDetector
import com.android.tools.lint.checks.TypographyDetector
import com.android.tools.lint.checks.WakelockDetector
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestResultChecker
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class XmlReporterTest {
    private val xmlPrologue = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    private val sampleManifest = manifest(
        """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg">
                <uses-sdk android:minSdkVersion="10" />
            </manifest>
            """
    ).indented()

    private val sampleLayout = xml(
        "res/layout/main.xml",
        """
            <Button xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/button1"
                    android:text="Fooo" />
            """
    ).indented()

    @Test
    fun testBasic() {
        @Language("XML")
        val expected =
            """
                <issues format="5" by="lint unittest">

                    <issue
                        id="UsesMinSdkAttributes"
                        severity="Warning"
                        message="`&lt;uses-sdk>` tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with `android:targetSdkVersion=&quot;?&quot;`"
                        category="Correctness"
                        priority="9"
                        summary="Minimum SDK and target SDK attributes not defined"
                        explanation="The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for)."
                        url="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                        urls="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                        errorLine1="    &lt;uses-sdk android:minSdkVersion=&quot;10&quot; />"
                        errorLine2="     ~~~~~~~~">
                        <location
                            file="AndroidManifest.xml"
                            line="3"
                            column="6"/>
                    </issue>

                    <issue
                        id="HardcodedText"
                        severity="Warning"
                        message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource"
                        category="Internationalization"
                        priority="5"
                        summary="Hardcoded text"
                        explanation="Hardcoding text attributes directly in layout files is bad for several reasons:&#xA;&#xA;* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)&#xA;&#xA;* The application cannot be translated to other languages by just adding new translations for existing string resources.&#xA;&#xA;There are quickfixes to automatically extract this hardcoded string into a resource lookup."
                        errorLine1="        android:text=&quot;Fooo&quot; />"
                        errorLine2="        ~~~~~~~~~~~~~~~~~~~">
                        <location
                            file="res/layout/main.xml"
                            line="3"
                            column="9"/>
                    </issue>

                </issues>
                """

        lint().files(sampleManifest, sampleLayout)
            .issues(ManifestDetector.USES_SDK, HardcodedValuesDetector.ISSUE)
            .run()
            .expectXml(xmlPrologue + expected.trimIndent() + "\n")
    }

    @Test
    fun testFullPaths() {
        val client = com.android.tools.lint.checks.infrastructure.TestLintClient()
        client.flags.isFullPath = true
        lint().files(sampleManifest, sampleLayout)
            .issues(ManifestDetector.USES_SDK, HardcodedValuesDetector.ISSUE)
            .client(client)
            .run()
            .checkXmlReport(
                TestResultChecker { xml ->
                    val testRoot = client.knownProjects
                        .iterator()
                        .next()
                        .dir
                        .parent
                    val actual = xml.replace(testRoot, "TESTROOT").replace('\\', '/')
                    @Language("XML")
                    val expected =
                        """
                        <issues format="5" by="lint unittest">

                            <issue
                                id="UsesMinSdkAttributes"
                                severity="Warning"
                                message="`&lt;uses-sdk>` tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with `android:targetSdkVersion=&quot;?&quot;`"
                                category="Correctness"
                                priority="9"
                                summary="Minimum SDK and target SDK attributes not defined"
                                explanation="The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for)."
                                url="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                                urls="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                                errorLine1="    &lt;uses-sdk android:minSdkVersion=&quot;10&quot; />"
                                errorLine2="     ~~~~~~~~">
                                <location
                                    file="TESTROOT/project0/AndroidManifest.xml"
                                    line="3"
                                    column="6"/>
                            </issue>

                            <issue
                                id="HardcodedText"
                                severity="Warning"
                                message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource"
                                category="Internationalization"
                                priority="5"
                                summary="Hardcoded text"
                                explanation="Hardcoding text attributes directly in layout files is bad for several reasons:&#xA;&#xA;* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)&#xA;&#xA;* The application cannot be translated to other languages by just adding new translations for existing string resources.&#xA;&#xA;There are quickfixes to automatically extract this hardcoded string into a resource lookup."
                                errorLine1="        android:text=&quot;Fooo&quot; />"
                                errorLine2="        ~~~~~~~~~~~~~~~~~~~">
                                <location
                                    file="TESTROOT/project0/res/layout/main.xml"
                                    line="3"
                                    column="9"/>
                            </issue>

                        </issues>
                    """
                    assertEquals(xmlPrologue + expected.trimIndent() + "\n", actual)
                },
                fullPaths = true
            )
    }

    @Test
    fun testNonPrintableChars() {
        // See https://code.google.com/p/android/issues/detail?id=56205

        @Language("XML")
        val expected =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="TypographyFractions"
                    severity="Warning"
                    message="Use fraction character ¼ (&amp;#188;) instead of 1/4 ?"
                    category="Usability:Typography"
                    priority="5"
                    summary="Fraction string can be replaced with fraction character"
                    explanation="You can replace certain strings, such as 1/2, and 1/4, with dedicated characters for these, such as ½ (&amp;#189;) and ¼ (&amp;#188;). This can help make the text more readable."
                    url="https://en.wikipedia.org/wiki/Number_Forms"
                    urls="https://en.wikipedia.org/wiki/Number_Forms"
                    errorLine1="    &lt;string name=&quot;user_registration_name1_4&quot;>Register 1/4&lt;/string>"
                    errorLine2="                                             ^">
                    <location
                        file="res/values/typography.xml"
                        line="3"
                        column="46"/>
                </issue>

            </issues>
            """

        lint().files(
            xml(
                "res/values/typography.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <string name=\"user_registration_name1_4\">Register 1/4</string>\n" +
                    "</resources>\n" +
                    "\n"
            )
        )
            .issues(TypographyDetector.FRACTIONS)
            .run()
            .expectXml(xmlPrologue + expected.trimIndent() + "\n")
    }

    @Test
    fun testBaselineFile() {
        @Language("XML")
        val expected =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="UsesMinSdkAttributes"
                    message="`&lt;uses-sdk>` tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with `android:targetSdkVersion=&quot;?&quot;`"
                    errorLine1="    &lt;uses-sdk android:minSdkVersion=&quot;10&quot; />"
                    errorLine2="     ~~~~~~~~">
                    <location
                        file="AndroidManifest.xml"
                        line="3"
                        column="6"/>
                </issue>

                <issue
                    id="HardcodedText"
                    message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource"
                    errorLine1="        android:text=&quot;Fooo&quot; />"
                    errorLine2="        ~~~~~~~~~~~~~~~~~~~">
                    <location
                        file="res/layout/main.xml"
                        line="3"
                        column="9"/>
                </issue>

            </issues>
            """

        lint().files(sampleManifest, sampleLayout)
            .issues(ManifestDetector.USES_SDK, HardcodedValuesDetector.ISSUE)
            .run()
            .expectXml(
                xmlPrologue + expected.trimIndent() + "\n",
                intendedForBaseline = true
            )
    }

    @Test
    fun testFixData() {
        @Language("XML")
        val expected =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="WakelockTimeout"
                    severity="Warning"
                    message="Provide a timeout when requesting a wakelock with `PowerManager.Wakelock.acquire(long timeout)`. This will ensure the OS will cleanup any wakelocks that last longer than you intend, and will save your user&apos;s battery."
                    category="Performance"
                    priority="9"
                    summary="Using wakeLock without timeout"
                    explanation="Wakelocks have two acquire methods: one with a timeout, and one without. You should generally always use the one with a timeout. A typical timeout is 10 minutes. If the task takes longer than it is critical that it happens (i.e. can&apos;t use `JobScheduler`) then maybe they should consider a foreground service instead (which is a stronger run guarantee and lets the user know something long/important is happening)."
                    errorLine1="        wakeLock.acquire(); // ERROR"
                    errorLine2="        ~~~~~~~~~~~~~~~~~~"
                    quickfix="studio">
                    <fix
                        description="Set timeout to 10 minutes"
                        auto="false">
                        <edit
                            file="src/test/pkg/WakelockTest.java"
                            offset="488"
                            after="ock.acquire("
                            before="); // ERROR&#xA;"
                            insert="10*60*1000L /*10 minutes*/"/>
                    </fix>
                    <location
                        file="src/test/pkg/WakelockTest.java"
                        line="11"
                        column="9"/>
                </issue>

            </issues>
            """

        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import android.content.Context;\n" +
                    "import android.os.PowerManager;\n" +
                    "\n" +
                    "import static android.os.PowerManager.PARTIAL_WAKE_LOCK;\n" +
                    "\n" +
                    "/** @noinspection ClassNameDiffersFromFileName*/ " +
                    "public abstract class WakelockTest extends Context {\n" +
                    "    public PowerManager.WakeLock createWakelock() {\n" +
                    "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n" +
                    "        PowerManager.WakeLock wakeLock = manager.newWakeLock(PARTIAL_WAKE_LOCK, \"Test\");\n" +
                    "        wakeLock.acquire(); // ERROR\n" +
                    "        return wakeLock;\n" +
                    "    }\n" +
                    "}\n"
            )
        )
            .issues(WakelockDetector.TIMEOUT)
            .run()
            .expectXml(
                xmlPrologue + expected.trimIndent() + "\n",
                includeFixes = true
            )
    }

    @Test
    fun testMultipleAlternativeFixes() {
        // Typo "unsed": suggest replacements used, unused, unsaid
        // Check that XML report contains all three fixes

        @Language("XML")
        val expected =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="Typos"
                    severity="Warning"
                    message="&quot;unsed&quot; is a common misspelling; did you mean &quot;used&quot; or &quot;unused&quot; or &quot;unsaid&quot; ?"
                    category="Correctness:Messages"
                    priority="7"
                    summary="Spelling error"
                    explanation="This check looks through the string definitions, and if it finds any words that look like likely misspellings, they are flagged."
                    errorLine1="    &lt;string name=&quot;message&quot;>%d unsed resources&lt;/string>"
                    errorLine2="                              ^"
                    quickfix="studio">
                    <fix
                        description="Replace with &quot;used&quot;"
                        auto="false">
                        <edit
                            file="res/values/strings.xml"
                            offset="81"
                            after="message&quot;>%d "
                            before="unsed resour"
                            delete="unsed"
                            insert="used"/>
                    </fix>
                    <fix
                        description="Replace with &quot;unused&quot;"
                        auto="false">
                        <edit
                            file="res/values/strings.xml"
                            offset="81"
                            after="message&quot;>%d "
                            before="unsed resour"
                            delete="unsed"
                            insert="unused"/>
                    </fix>
                    <fix
                        description="Replace with &quot;unsaid&quot;"
                        auto="false">
                        <edit
                            file="res/values/strings.xml"
                            offset="81"
                            after="message&quot;>%d "
                            before="unsed resour"
                            delete="unsed"
                            insert="unsaid"/>
                    </fix>
                    <location
                        file="res/values/strings.xml"
                        line="3"
                        column="31"/>
                </issue>

            </issues>
            """

        lint().files(
            xml(
                "res/values/strings.xml",
                "" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <string name=\"message\">%d unsed resources</string>\n" +
                    "</resources>\n"
            )
        )
            .issues(TypoDetector.ISSUE)
            .run()
            .expectXml(
                xmlPrologue + expected.trimIndent() + "\n",
                includeFixes = true
            )
    }

    @Test
    fun testMultipleEditsForSingleFix() {
        // Check that XML report contains all three fixes

        @Language("XML")
        val expected =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="EllipsizeMaxLines"
                    severity="Error"
                    message="Combining `ellipsize=start` and `lines=1` can lead to crashes. Use `singleLine=true` instead."
                    category="Correctness"
                    priority="4"
                    summary="Combining Ellipsize and Maxlines"
                    explanation="Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` but that should not be done when using `ellipsize`."
                    url="https://issuetracker.google.com/issues/36950033"
                    urls="https://issuetracker.google.com/issues/36950033"
                    errorLine1="        android:lines=&quot;1&quot;"
                    errorLine2="        ~~~~~~~~~~~~~~~~~"
                    quickfix="studio">
                    <fix
                        description="Replace with singleLine=&quot;true&quot;"
                        auto="true">
                        <edit
                            file="res/layout/sample.xml"
                            offset="330"
                            after="&quot;1&quot;&#xA;        "
                            before="android:text"
                            insert="android:singleLine=&quot;true&quot; "/>
                        <edit
                            file="res/layout/sample.xml"
                            offset="304"
                            after="rt&quot;&#xA;        "
                            before="android:lines=&quot;1&quot;"
                            delete="android:lines=&quot;1&quot;"/>
                    </fix>
                    <location
                        file="res/layout/sample.xml"
                        line="9"
                        column="9"/>
                    <location
                        file="res/layout/sample.xml"
                        line="8"
                        column="9"/>
                </issue>

            </issues>
            """
        lint().files(
            xml(
                "res/layout/sample.xml",
                "" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"wrap_content\"\n" +
                    "    android:layout_height=\"wrap_content\" >\n" +
                    "\n" +
                    "    <TextView\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:ellipsize=\"start\"\n" + // ERROR

                    "        android:lines=\"1\"\n" +
                    "        android:text=\"Really long text that needs to be ellipsized here - 0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789\" />\n" +
                    "\n" +
                    "</RelativeLayout>\n"
            )
        )
            .issues(EllipsizeMaxLinesDetector.ISSUE)
            .run()
            .expectXml(
                xmlPrologue + expected.trimIndent() + "\n",
                includeFixes = true
            )
    }

    private fun lint(): TestLintTask {
        return TestLintTask.lint().sdkHome(TestUtils.getSdk())
    }
}
