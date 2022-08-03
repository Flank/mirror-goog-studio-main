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

package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject
import com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE
import com.android.tools.lint.LintCliFlags.ERRNO_ERRORS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.MainTest
import com.android.tools.lint.checks.AccessibilityDetector
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.LayoutConsistencyDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.PxUsageDetector
import com.android.tools.lint.checks.RangeDetector
import com.android.tools.lint.checks.RestrictToDetector
import com.android.tools.lint.checks.ScopedStorageDetector
import com.android.tools.lint.checks.TypoDetector
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.image
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.LintBaseline.Companion.isSamePathSuffix
import com.android.tools.lint.client.api.LintBaseline.Companion.prefixMatchLength
import com.android.tools.lint.client.api.LintBaseline.Companion.sameWithAbsolutePath
import com.android.tools.lint.client.api.LintBaseline.Companion.stringsEquivalent
import com.android.tools.lint.client.api.LintBaseline.Companion.suffixMatchLength
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LintBaselineTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    /**
     * Overrides TestLintClient to use the checked-in SDK that is
     * available in the tools/base repo. The "real" TestLintClient
     * is a public utility for writing lint tests, so it cannot make
     * assumptions specific to tools/base.
     */
    private inner class ToolsBaseTestLintClient : TestLintClient() {
        override fun getSdkHome(): File? {
            return TestUtils.getSdk().toFile()
        }
    }

    private fun LintBaseline.findAndMark(
        issue: Issue,
        location: Location,
        message: String,
        severity: Severity?,
        project: Project?
    ): Boolean {
        val incident = Incident(issue, location, message).apply {
            severity?.let { this.severity = it }
            project?.let { this.project = it }
        }
        return findAndMark(incident)
    }

    @Test
    fun testBaseline() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint unittest">

                <issue
                    id="UsesMinSdkAttributes"
                    severity="Warning"
                    message="&lt;uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=&quot;?&quot;"
                    category="Correctness"
                    priority="9"
                    summary="Minimum SDK and target SDK attributes not defined"
                    explanation="The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for)."
                    url="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                    urls="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html"
                    errorLine1="    &lt;uses-sdk android:minSdkVersion=&quot;8&quot; />"
                    errorLine2="    ^">
                    <location
                        file="AndroidManifest.xml"
                        line="7"/>
                </issue>

                <issue
                    id="HardcodedText"
                    severity="Warning"
                    message="[I18N] Hardcoded string &quot;Fooo&quot;, should use @string resource"
                    category="Internationalization"
                    priority="5"
                    summary="Hardcoded text"
                    explanation="Hardcoding text attributes directly in layout files is bad for several reasons:

            * When creating configuration variations (for example for landscape or portrait)you have to repeat the actual text (and keep it up to date when making changes)

            * The application cannot be translated to other languages by just adding new translations for existing string resources.

            There are quickfixes to automatically extract this hardcoded string into a resource lookup."
                    errorLine1="        android:text=&quot;Fooo&quot; />"
                    errorLine2="        ~~~~~~~~~~~~~~~~~~~">
                    <location
                        file="res/layout/main.xml"
                        line="12"/>
                    <location
                        file="res/layout/main2.xml"
                        line="11"/>
                </issue>

                <issue
                    id="Range"
                    message="Value must be ≥ 0 (was -1)"
                    errorLine1="                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,"
                    errorLine2="                                                                              ~~~~~~~~~~~~~~~">
                    <location
                        file="java/android/support/v4/widget/SlidingPaneLayout.java"
                        line="589"
                        column="79"/>
                </issue>

            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)

        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        var found: Boolean = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(File("bogus")), "Unrelated", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.totalCount).isEqualTo(3)
        // because we haven't actually matched anything
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Wrong issue
        found = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(File("bogus")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Wrong file
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout-port/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(0)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(3)

        // Match
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isTrue()
        assertThat(baseline.fixedCount).isEqualTo(2)
        assertThat(baseline.foundWarningCount).isEqualTo(1)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(2)

        // Search for the same error once it's already been found: no longer there
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("res/layout/main.xml")),
            "Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING, null
        )
        assertThat(found).isFalse()
        assertThat(baseline.foundWarningCount).isEqualTo(1)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(2)

        found = baseline.findAndMark(
            RangeDetector.RANGE,
            Location.create(
                File(
                    "java/android/support/v4/widget/SlidingPaneLayout.java"
                )
            ),
            // Match, by different message
            // Actual: "Value must be \u2265 0 (was -1)", Severity.WARNING, null
            "Value must be \u2265 0", Severity.WARNING, null
        )
        assertThat(found).isTrue()
        assertThat(baseline.fixedCount).isEqualTo(1)
        assertThat(baseline.foundWarningCount).isEqualTo(2)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(1)

        baseline.close()
    }

    @Test
    fun testSuffix() {
        assertTrue(isSamePathSuffix("foo", "foo"))
        assertTrue(isSamePathSuffix("", ""))
        assertTrue(isSamePathSuffix("abc/def/foo", "def/foo"))
        assertTrue(isSamePathSuffix("abc/def/foo", "../../def/foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "abc\\def\\foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "..\\..\\abc\\def\\foo"))
        assertTrue(isSamePathSuffix("abc\\def\\foo", "def\\foo"))
        assertFalse(isSamePathSuffix("foo", "bar"))
    }

    @Test
    fun testStringsEquivalent() {
        assertTrue(stringsEquivalent("", ""))
        assertTrue(stringsEquivalent("foo", ""))
        assertTrue(stringsEquivalent("", "bar"))
        assertTrue(stringsEquivalent("foo", "foo"))
        assertTrue(stringsEquivalent("foo", "foo."))
        assertTrue(stringsEquivalent("foo.", "foo"))
        assertTrue(stringsEquivalent("foo.", "foo. Bar."))
        assertTrue(stringsEquivalent("foo. Bar.", "foo"))
        assertTrue(stringsEquivalent("", ""))
        assertTrue(stringsEquivalent("abc def", "abc `def`"))
        assertTrue(stringsEquivalent("abc `def` ghi", "abc def ghi"))
        assertTrue(stringsEquivalent("`abc` def", "abc def"))
        assertTrue(
            stringsEquivalent(
                "Suspicious equality check: equals() is not implemented in targetType",
                "Suspicious equality check: `equals()` is not implemented in targetType"
            )
        )
        assertTrue(
            stringsEquivalent(
                "This Handler class should be static or leaks might occur name",
                "This `Handler` class should be static or leaks might occur name"
            )
        )
        assertTrue(
            stringsEquivalent(
                "Using the AllowAllHostnameVerifier HostnameVerifier is unsafe ",
                "Using the `AllowAllHostnameVerifier` HostnameVerifier is unsafe "
            )
        )
        assertTrue(
            stringsEquivalent(
                "Reading app signatures from getPackageInfo: The app signatures could be exploited if not validated properly; see issue explanation for details.",
                "Reading app signatures from `getPackageInfo`: The app signatures could be exploited if not validated properly; see issue explanation for details"
            )
        )
        assertTrue(stringsEquivalent("````abc", "abc"))
        assertFalse(stringsEquivalent("abc", "def"))
        assertFalse(stringsEquivalent("abcd", "abce"))
        assertTrue(stringsEquivalent("ab cd ?", "ab   c d?"))
    }

    @Test
    fun tolerateMinSpChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                PxUsageDetector.SMALL_SP_ISSUE,
                "Avoid using sizes smaller than 12sp: 11sp",
                "Avoid using sizes smaller than 11sp: 11sp"
            )
        )
    }

    @Test
    fun tolerateRangeMessageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                RangeDetector.RANGE,
                "Value must be ≥ 0 but can be -1",
                "Value must be ≥ 0"
            )
        )
    }

    @Test
    fun tolerateIconMissingDensityFolderMessageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                IconDetector.ICON_MISSING_FOLDER,
                "Missing density variation folders in `res`: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi",
                "Missing density variation folders in `/some/full/path/to/app/res`: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi"
            )
        )
    }

    @Test
    fun tolerateIconXmlAndPngMessageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                IconDetector.ICON_XML_AND_PNG,
                "The following images appear both as density independent `.xml` files and as bitmap files: res/drawable/background.xml",
                "The following images appear both as density independent `.xml` files and as bitmap files: /some/full/path/to/app/res/drawable/background.xml"
            )
        )
    }

    @Test
    fun tolerateRestrictToChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.method3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group (groupId=test.pkg.library)"
            )
        )
        assertTrue(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.method3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group"
            )
        )
        assertFalse(
            baseline.sameMessage(
                RestrictToDetector.RESTRICTED,
                "LibraryCode.FIELD3 can only be called from within the same library group (referenced groupId=test.pkg.library from groupId=other.app)",
                "LibraryCode.method3 can only be called from within the same library group (groupId=test.pkg.library)"
            )
        )
    }

    @Test
    fun tolerateUrlChanges() {
        assertTrue(stringsEquivalent("abcd http://some.url1", "abcd http://other.url2"))
        assertTrue(stringsEquivalent("abcd http://some.url1, ok", "abcd http://other.url2"))
        assertTrue(stringsEquivalent("abcd http://some.url1", "abcd http://other.url2, ok"))
        assertFalse(stringsEquivalent("abcd http://some.url1 different", "abcd http://other.url2, words"))
    }

    @Test
    fun tolerateScopedStorageChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                ScopedStorageDetector.ISSUE,
                "The Google Play store has a policy that limits usage of MANAGE_EXTERNAL_STORAGE",
                "Most apps are not allowed to use MANAGE_EXTERNAL_STORAGE"
            )
        )
    }

    @Test
    fun tolerateApiDetectorMessageChanges() {
        val baseline = LintBaseline(null, File(""))

        // minSdk changes can happen anytime; be flexible with these:
        assertTrue(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Call requires API level 23 (current min is 1): `foo`",
                "Call requires API level 23 (current min is 22): `foo`"
            )
        )

        // When we switch from preview builds to finalized APIs the target can change:

        assertTrue(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Call requires API level R (current min is 1): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrderedOnTop`"
            )
        )

        assertFalse(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Field requires API level R (current min is 29): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrderedOnTop`"
            )
        )

        assertFalse(
            baseline.sameMessage(
                ApiDetector.UNSUPPORTED,
                "Call requires API level R (current min is 29): `setZOrderedOnTop`",
                "Call requires API level 30 (current min is 29): `setZOrdered`"
            )
        )
    }

    @Test
    fun tolerateTypoMessageChange() {
        // Generic test for grammar change to remove spaces before question marks
        // as now enforced by LintImplTextFormat
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                TypoDetector.ISSUE,
                "Did you mean \"intended\" instead of \"actual\" ?",
                "Did you mean \"intended\" instead of \"actual\"?"
            )
        )
    }

    @Test
    fun tolerateA11yI18nChanges() {
        val baseline = LintBaseline(null, File(""))
        assertTrue(
            baseline.sameMessage(
                HardcodedValuesDetector.ISSUE,
                "Hardcoded string \"Fooo\", should use @string resource",
                "[I18N] Hardcoded string \"Fooo\", should use @string resource"
            )
        )

        assertTrue(
            baseline.sameMessage(
                AccessibilityDetector.ISSUE,
                "Empty contentDescription attribute on image",
                "[Accessibility] Empty contentDescription attribute on image"
            )
        )
    }

    @Test
    fun testFormat() {
        val baselineFile = temporaryFolder.newFile("lint-baseline.xml")
        val client = ToolsBaseTestLintClient()
        var baseline = LintBaseline(client, baselineFile)
        assertThat(baseline.writeOnClose).isFalse()
        baseline.writeOnClose = true
        assertThat(baseline.writeOnClose).isTrue()

        val project1Folder = temporaryFolder.newFolder("project1")
        val project2Folder = temporaryFolder.newFolder("project2")
        val project2 = Project.create(client, project2Folder, project2Folder)

        // Make sure file exists, since path computations depend on it
        val sourceFile = File(project1Folder, "my/source/file.txt").absoluteFile
        sourceFile.parentFile.mkdirs()
        sourceFile.createNewFile()

        baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(6, 4, 198), DefaultPosition(6, 42, 236)
            ),
            "<uses-sdk> tag should specify a target API level (the highest verified \n" +
                "version; when running on later versions, compatibility behaviors may \n" +
                "be enabled) with android:targetSdkVersion=\"?\"",
            Severity.WARNING, null
        )
        baseline.close()

        var actual = baselineFile.readText().dos2unix()

        @Language("XML")
        val expected =
            """<?xml version="1.0" encoding="UTF-8"?>
<issues format="5" by="lint unittest">

    <issue
        id="UsesMinSdkAttributes"
        message="&lt;uses-sdk> tag should specify a target API level (the highest verified &#xA;version; when running on later versions, compatibility behaviors may &#xA;be enabled) with android:targetSdkVersion=&quot;?&quot;">
        <location
            file="/foo/bar/Foo/AndroidManifest.xml"
            line="7"/>
    </issue>

    <issue
        id="HardcodedText"
        message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource">
        <location
            file="../project1/my/source/file.txt"
            line="1"/>
    </issue>

</issues>
"""
        assertThat(actual).isEqualTo(expected)

        // Now load the baseline back in and make sure we can match entries correctly
        baseline = LintBaseline(client, baselineFile)
        baseline.writeOnClose = true
        assertThat(baseline.removeFixed).isFalse()

        var found: Boolean = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        assertThat(found).isTrue()
        found = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(6, 4, 198), DefaultPosition(6, 42, 236)
            ),
            "<uses-sdk> tag should specify a target API level (the highest verified \n" +
                "version; when running on later versions, compatibility behaviors may \n" +
                "be enabled) with android:targetSdkVersion=\"?\"",
            Severity.WARNING, null
        )
        assertThat(found).isTrue()
        baseline.close()

        actual = baselineFile.readText().dos2unix()
        assertThat(actual).isEqualTo(expected)

        // Test the skip fix flag
        baseline = LintBaseline(client, baselineFile)
        baseline.writeOnClose = true
        baseline.removeFixed = true
        assertThat(baseline.removeFixed).isTrue()

        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(sourceFile, "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, project2
        )
        assertThat(found).isTrue()

        // Note that this is a different, unrelated issue
        found = baseline.findAndMark(
            ManifestDetector.APPLICATION_ICON,
            Location.create(
                File("/foo/bar/Foo/AndroidManifest.xml"),
                DefaultPosition(4, 4, 198), DefaultPosition(4, 42, 236)
            ),
            "Should explicitly set `android:icon`, there is no default",
            Severity.WARNING, null
        )
        assertThat(found).isFalse()
        baseline.close()

        actual = baselineFile.readText().dos2unix()

        // This time we should ONLY get the initial baseline issue back; we should
        // NOT see the new issue, and the fixed issue (the uses sdk error reported in the baseline
        // before but not repeated now) should be missing.
        assertThat(actual).isEqualTo(
            "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<issues format=\"5\" by=\"lint unittest\">\n" +
                "\n" +
                "    <issue\n" +
                "        id=\"HardcodedText\"\n" +
                "        message=\"Hardcoded string &quot;Fooo&quot;, should use `@string` resource\">\n" +
                "        <location\n" +
                "            file=\"../project1/my/source/file.txt\"\n" +
                "            line=\"1\"/>\n" +
                "    </issue>\n" +
                "\n" +
                "</issues>\n"
        )
    }

    @Test
    fun testChangedUrl() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        val errorMessage = "The attribute android:allowBackup is deprecated from Android 12 and higher and ..."

        @Language("XML")
        val baselineContents =
            """<?xml version="1.0" encoding="UTF-8"?>
<issues format="5" by="lint unittest">

    <issue
        id="DataExtractionRules"
        message="$errorMessage"
        errorLine1="    &lt;application"
        errorLine2="    ^">
        <location
            file="src/main/AndroidManifest.xml"
            line="5"
            column="5"/>
    </issue>

</issues>
"""
        baselineFile.writeText(baselineContents)
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        assertTrue(
            baseline.findAndMark(
                ManifestDetector.DATA_EXTRACTION_RULES,
                Location.create(File("src/main/AndroidManifest.xml")),
                errorMessage,
                Severity.WARNING,
                null
            )
        )

        baseline.close()
    }

    @Test
    fun testTemporaryMessages() {
        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFile = kotlin(
            """
            package test.pkg
            import android.location.LocationManager
            fun test() {
                val mode = LocationManager.MODE_CHANGED_ACTION
            }
            """
        ).indented()

        val baselineFolder = File(root, "baselines")
        baselineFolder.mkdirs()
        val existingBaseline = File(baselineFolder, "baseline.xml")
        val outputBaseline = File(baselineFolder, "baseline-out.xml")
        existingBaseline.writeText(
            // language=XML
            """
            <issues format="5" by="lint unittest">
                <issue
                    id="HardcodedText"
                    message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource">
                    <location
                        file="../project1/my/source/file.txt"
                        line="1"/>
                </issue>

            </issues>
            """.trimIndent()
        )

        val project = lint().files(testFile).createProjects(root).single()

        MainTest.checkDriver(
            // Expected output
            "src/test/pkg/test.kt:4: Error: Field requires API level 19 (current min is 1): android.location.LocationManager#MODE_CHANGED_ACTION [InlinedApi]\n" +
                "    val mode = LocationManager.MODE_CHANGED_ACTION\n" +
                "               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings",
            // Expected error
            "",
            // Expected exit code
            ERRNO_ERRORS,
            arrayOf(
                "--exit-code",
                "--check",
                "InlinedApi",
                "--error",
                "InlinedApi",
                "--ignore",
                "LintBaseline",
                "--baseline",
                existingBaseline.path,
                "--write-reference-baseline",
                outputBaseline.path,
                "--disable",
                "LintError",
                "--sdk-home",
                TestUtils.getSdk().toFile().path,
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        @Language("XML")
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues>

                <issue
                    id="InlinedApi"
                    message="Field requires API level 19 (current min is 1): `android.location.LocationManager#MODE_CHANGED_ACTION`">
                    <location
                        file="src/test/pkg/test.kt"
                        line="4"/>
                </issue>

            </issues>
        """.trimIndent()
        assertEquals(expected, readBaseline(outputBaseline).dos2unix()) // b/209433064
    }

    @Test
    fun testWriteOutputBaseline() {
        // Makes sure that if we have set an output baseline, and we don't have
        // an input baseline, we'll write the output baseline.
        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFile = kotlin(
            """
            package test.pkg
            val path = "/sdcard/path"
            """
        ).indented()

        val outputBaseline = File(root, "baseline-out.xml")
        val project = lint().files(testFile).createProjects(root).single()
        MainTest.checkDriver(
            // Expected output
            "src/test/pkg/test.kt:2: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n" +
                "0 errors, 1 warnings",
            // Expected error
            "",
            // Expected exit code
            ERRNO_SUCCESS,
            arrayOf(
                "--check",
                "SdCardPath",
                "--nolines",
                "--write-reference-baseline",
                outputBaseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        @Language("XML")
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues>

                <issue
                    id="SdCardPath"
                    message="Do not hardcode &quot;/sdcard/&quot;; use `Environment.getExternalStorageDirectory().getPath()` instead">
                    <location
                        file="src/test/pkg/test.kt"
                        line="2"
                        column="13"/>
                </issue>

            </issues>
        """.trimIndent()
        assertEquals(expected, readBaseline(outputBaseline))
    }

    @Test
    fun testPlatformTestCase() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("text")
        val path = "packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`">
                    <location file="packages/modules/IPsec/src/java/android/net/ipsec/ike/exceptions/AuthenticationFailedException.java"/>
                </issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.TemporaryFailureException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `ChildSaProposal` to `SaProposal` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `NoValidProposalChosenException` to `IkeProtocolException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.TunnelModeChildSessionParams`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeProtocolException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.NoValidProposalChosenException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Exception requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Class requires API level 31 (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `InvalidSyntaxException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.IkeInternalException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.TsUnacceptableException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidKeException`"><location file="$path" /></issue>
                <issue id="NewApi" message="Call requires API level 31 (current min is 30): `new android.net.ipsec.ike.exceptions.InvalidSyntaxException`"><location file="$path" /></issue>
            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        fun mark(message: String, path: String): Boolean {
            val location = Location.create(File(path))
            return baseline.findAndMark(ApiDetector.UNSUPPORTED, location, message, Severity.WARNING, null)
        }

        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Exception` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `ChildSaProposal` to `SaProposal` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `Throwable` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.TunnelModeChildSessionParams`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeProtocolException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Exception requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Class requires API level S (current min is 30): `android.net.ipsec.ike.exceptions.IkeProtocolException`", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        assertTrue(mark("Cast from `IkeInternalException` to `IkeException` requires API level 31 (current min is 30)", "/packages/modules/IPsec/src/java/com/android/internal/net/ipsec/ike/ChildSessionStateMachine.java"))
        baseline.close()
    }

    @Test
    fun testInexactMatching() {
        // Test 1: Test matching where we look at the wrong file and return instead of getting to the next one
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Call requires API level 29: `Something`"><location file="OtherFile.java"/></issue>
                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="MyFile.java"/></issue>
            </issues>
            """.trimIndent()
        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)
        assertTrue(
            baseline.findAndMark(
                ApiDetector.UNSUPPORTED,
                Location.create(File("MyFile.java")),
                "Call requires API level S: `Something`",
                Severity.WARNING,
                null
            )
        )
        baseline.close()
    }

    @Test
    fun testMessageToEntryCleanup() {
        val baselineFile = temporaryFolder.newFile("baseline.xml")

        @Language("XML")
        val baselineContents =
            """
            <issues format="5" by="lint 4.1.0" client="cli" variant="all" version="4.1.0">

                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="MyFile.java"/></issue>
                <issue id="NewApi" message="Call requires API level 30: `Something`"><location file="OtherFile.java"/></issue>
            </issues>
            """.trimIndent()

        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)

        fun mark(message: String, path: String): Boolean {
            val location = Location.create(File(path))
            return baseline.findAndMark(ApiDetector.UNSUPPORTED, location, message, Severity.WARNING, null)
        }

        assertTrue(mark("Call requires API level 30: `Something`", "MyFile.java"))
        assertTrue(mark("Call requires API level 29: `Something`", "OtherFile.java"))
        baseline.close()
    }

    @Test
    fun testUpdateBaselineWithContinue() {
        // Testing two scenarios.
        //   (1) No baseline exists (or is not specified). Ensures that the output baseline
        //       file is written and contains all issues.
        //   (2) Baseline exists. Ensures that the output baseline
        //       contains both the matched errors and any non matched
        //       errors (and removes unreported issues).

        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFile = xml(
            "res/layout/accessibility.xml",
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/newlinear" android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent">
                    <ImageView android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                    <ImageButton android:importantForAccessibility="yes" android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                </LinearLayout>
                """
        ).indented()

        val baselineFolder = File(root, "baselines")
        baselineFolder.mkdirs()
        val nonexistentBaseline = File(baselineFolder, "nonexistent-baseline.xml")
        val existingBaseline = File(baselineFolder, "baseline.xml")
        val outputBaseline = File(baselineFolder, "baseline-out.xml")
        existingBaseline.writeText(
            // language=XML
            """
            <issues format="5" by="lint unittest">
                <issue
                    id="HardcodedText"
                    message="Hardcoded string &quot;Fooo&quot;, should use `@string` resource">
                    <location
                        file="../project1/my/source/file.txt"
                        line="1"/>
                </issue>

                <issue
                    id="ContentDescription"
                    message="Missing `contentDescription` attribute on image">
                    <location
                        file="res/layout/accessibility.xml"
                        line="5"/>
                </issue>

            </issues>
            """.trimIndent()
        )

        val outputWithBaseline = """
            ../baselines/baseline.xml: Information: 1 error was filtered out because it is listed in the baseline file, ../baselines/baseline.xml
             [LintBaseline]
            ../baselines/baseline.xml: Information: 1 errors/warnings were listed in the baseline file (../baselines/baseline.xml) but not found in the project; perhaps they have been fixed? Unmatched issue types: HardcodedText [LintBaseline]
            res/layout/accessibility.xml:3: Error: Missing contentDescription attribute on image [ContentDescription]
                <ImageButton android:importantForAccessibility="yes" android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                 ~~~~~~~~~~~
            1 errors, 0 warnings (1 error filtered by baseline baseline.xml)
            """
        val outputWithoutBaseline = """
            res/layout/accessibility.xml:2: Error: Missing contentDescription attribute on image [ContentDescription]
                <ImageView android:id="@+id/android_logo" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                 ~~~~~~~~~
            res/layout/accessibility.xml:3: Error: Missing contentDescription attribute on image [ContentDescription]
                <ImageButton android:importantForAccessibility="yes" android:id="@+id/android_logo2" android:layout_width="wrap_content" android:layout_height="wrap_content" android:src="@drawable/android_button" android:focusable="false" android:clickable="false" android:layout_weight="1.0" />
                 ~~~~~~~~~~~
            2 errors, 0 warnings
            """

        val project = lint().files(testFile).createProjects(root).single()

        val scenarios: List<Pair<File?, String>> = listOf(
            null to outputWithoutBaseline,
            nonexistentBaseline to outputWithoutBaseline,
            existingBaseline to outputWithBaseline
        )

        for ((baselineFile, output) in scenarios) {
            outputBaseline.delete()

            val baselineArgs = if (baselineFile != null)
                arrayOf("--baseline", baselineFile.path)
            else
                emptyArray()

            MainTest.checkDriver(
                // Expected output
                output,
                // Expected error
                "",
                // Expected exit code
                ERRNO_ERRORS,
                arrayOf(
                    "--exit-code",
                    "--check",
                    "ContentDescription",
                    "--error",
                    "ContentDescription",
                    *baselineArgs,
                    "--write-reference-baseline",
                    outputBaseline.path,
                    "--disable",
                    "LintError",
                    project.path
                ),
                { it.replace(root.path, "ROOT") },
                null
            )

            val newBaseline = readBaseline(outputBaseline)

            // Expected baseline: lint uses a more detailed report when not rewriting an
            // existing baseline (because with baseline matching it doesn't have details
            // about the filtered items)
            @Language("XML")
            val expected = if (baselineFile != null) """
                <?xml version="1.0" encoding="UTF-8"?>
                <issues>

                    <issue
                        id="ContentDescription"
                        message="Missing `contentDescription` attribute on image">
                        <location
                            file="res/layout/accessibility.xml"
                            line="2"/>
                    </issue>

                    <issue
                        id="ContentDescription"
                        message="Missing `contentDescription` attribute on image">
                        <location
                            file="res/layout/accessibility.xml"
                            line="3"/>
                    </issue>

                </issues>
            """.trimIndent()
            else """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues>

                <issue
                    id="ContentDescription"
                    message="Missing `contentDescription` attribute on image"
                    errorLine1="    &lt;ImageView android:id=&quot;@+id/android_logo&quot; android:layout_width=&quot;wrap_content&quot; android:layout_height=&quot;wrap_content&quot; android:src=&quot;@drawable/android_button&quot; android:focusable=&quot;false&quot; android:clickable=&quot;false&quot; android:layout_weight=&quot;1.0&quot; />"
                    errorLine2="     ~~~~~~~~~">
                    <location
                        file="res/layout/accessibility.xml"
                        line="2"
                        column="6"/>
                </issue>

                <issue
                    id="ContentDescription"
                    message="Missing `contentDescription` attribute on image"
                    errorLine1="    &lt;ImageButton android:importantForAccessibility=&quot;yes&quot; android:id=&quot;@+id/android_logo2&quot; android:layout_width=&quot;wrap_content&quot; android:layout_height=&quot;wrap_content&quot; android:src=&quot;@drawable/android_button&quot; android:focusable=&quot;false&quot; android:clickable=&quot;false&quot; android:layout_weight=&quot;1.0&quot; />"
                    errorLine2="     ~~~~~~~~~~~">
                    <location
                        file="res/layout/accessibility.xml"
                        line="3"
                        column="6"/>
                </issue>

            </issues>
            """.trimIndent()
            assertEquals(expected, newBaseline.dos2unix()) // b/209433064
        }
    }

    @Test
    fun testPathsOutsideProject() {
        val client = ToolsBaseTestLintClient()
        val dir = client.pathVariables["GRADLE_USER_HOME"]
        assertNotNull(dir)
        dir!!
        if (!dir.exists()) {
            // TODO: What about sandbox? Create it?
            return
        }
        val gradleUserFile = File(dir, "mypath.txt")
        val sdkFile = File(TestUtils.getSdk().toFile(), "platform-tools/package.xml")
        try {
            gradleUserFile.writeText("Some file in gradle user home")
            val root = temporaryFolder.root
            val projects = lint().files(
                // lint check just looks for a file named *.txt and takes its *contents* a path where it reports the file
                source("src/foo/foo.txt", gradleUserFile.path),
                source("src/foo/bar.txt", sdkFile.path),
                *JarFileIssueRegistryTest.lintApiStubs,
                bytecode(
                    "lint.jar",
                    source(
                        "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                        "test.pkg.MyIssueRegistry"
                    ),
                    0x70522285
                ),
                bytecode(
                    "lint.jar",
                    kotlin(
                        """
                    package test.pkg
                    import java.io.File
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyDetector : Detector(), OtherFileScanner {
                        override fun getApplicableFiles(): EnumSet<Scope> {
                            return EnumSet.of(Scope.OTHER)
                        }

                        override fun run(context: Context) {
                            if (context.file.name.endsWith(".txt")) {
                              val path = context.file.readText()
                              val file = File(path)
                              context.report(ISSUE, Location.create(file), "My message")
                            }
                        }

                        companion object {
                            @JvmField
                            val ISSUE = Issue.create(
                                id = "MyIssueId",
                                briefDescription = "My Summary",
                                explanation = "My full explanation.",
                                category = Category.LINT,
                                priority = 10,
                                severity = Severity.WARNING,
                                implementation = Implementation(MyDetector::class.java, EnumSet.of(Scope.OTHER))
                            )
                        }
                    }
                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = listOf(MyDetector.ISSUE)
                        override val api: Int = 9
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
                    ).indented(),
                    0x9fd640fb,
                    """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGI2BijgMuZSTM7P1UvMSynKz0zRK8nPzynW
                y8nMK9FLzslMBVKJBZlCfM5gdnxxSWlSsXeJEoMWAwCSLNcCTQAAAA==
                """,
                    """
                test/pkg/MyDetector＄Companion.class:
                H4sIAAAAAAAAAJVSy04UQRQ9VT0v2kGahwoo4gMVNFJAXBgxJDpo0smgCejE
                hIUpZkospruadNUQ3c1K/8M/YGXiwkxY+lHGW80oG2Pi5j7Ovefe6nP7x89v
                3wE8wB2GOaesE4fdfbH1cVM51XZZvtDI0kNpdGaqYAzRgTySIpFmX7zcO6CO
                KgKGymNttNtgCBaXWnWUUQlRQpWh5N5ryzDf/OfkdYbVxWY3c4k24uAoFdo4
                lRuZiE31TvYS18iMdXnPs7Zk3lX5+lIrBPcbJhfaZ8W3aVFlWP6/aQzjvwlb
                ysmOdJIwnh4FJA3zpuINGFiX8A/aZysUdVYZNgb9KOTTPOTRoB/yGvdJ7eRT
                MD3or/EV9rRa4ydfKjzi21EUzPKV0sPKm5PPJY+Fg76fssb87HK8s/P6GcO9
                ZjtLhTSdPNMd4bIssYKe5kRnqJyQh1rE1vYUvXLyL9JWMccw8kdfhtGz2nLX
                0V0aWUcxjDW1US966Z7KX8m9hJCJZtaWSUvm2udDsB4bo/JGIq1VdM1wJ+vl
                bfVc+9rMds84naqWtpqanxiTOeloqcUqXajkZSPP/U9B33idMuF1JF+++xW1
                46J8w0tcgFO4SbZ+2oARhEBE0uDckHyfPB+S68fFTTzh4il4SiiiUZynWoAF
                ysKCdBXzmMGtYuE13Cb/iPAx6o12EcQYjzERYxJTFOJCTDMv7YJZTGNmF2WL
                0GLWomJx2eLKL/vlZnc4AwAA
                """,
                    """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAAAJ1XaXcT1xl+rmR7pEGAEIRgCInbECIhozGOoWnk0oIxsYgk
                U+SYGrqNpbE8tjSjzoxcuyttk7bpvrfp3nTfThunBzjlnB5OP/Yv9G/0Y09P
                nzsa27KtD2O8zF3f933e9d77r//9/R8AxvBngcOe4Xpaa7muldauGJ5R9WxH
                gRAYrtpNTbdqjm3WNM+2G67WMC1PqwWbNL1lalsUUYGxMBTT3qLhXDUbRqWq
                W5ZByn6BgXHTMr2LAtF0ZjYBBTEVfYgL9HmLpivwWLEHyrxAqm54l1qthlnV
                5xuG5Co3pzPFJX1F19qe2dAmrXazYnjcfLnX/HgxDOhK1W4Z+Ytk8nTRdura
                kuHNO7ppuaS0bE/3TJv9su2V240Gd2VCM1VwSKB/emZq8oZAdg9gEjiMI3Gk
                8JjAAV+thm7VfbUUPC5waJeqCgYFIvaCwDPp4naKfC+LJXACT6g4jpMCB+2F
                tC8350PNxPCUwJPLtkd42tJKUyNKw7H0hlawPIeGMauugrcJnKguGtXlwDKT
                qy3HcF0aa1ZvtA2BZ7uBTM8vUcl810xFsqrnZUg8jVMq3o5nGCJO2xIYSYey
                1YRNXKseWQgo1c4ghgyDpBvXdd3RmyRzEsh25AzTGXvgryDHWF1g/NEZHQVM
                W5PxSCuO4FwcGkYFEt0rCsaIiQFcpmyBIxvR2a15AhfwDhXn8TwdGsDPSTE5
                i0QxvECpOU+qNE6VAnfITQED9yVCY1olDavm3jS9xVM1Y0FvNzyBfHq3uN0z
                twq7HZS5lcB7cEnFu3GZsAKpgVa+xCuU6Bh6bYZItiROp7dbpjOyOKwu6o5r
                eNpEp833EtrLOFfxoopJTMmc7x02Cq5J873EHCtUKi9Phs2xguu2pe9KKMcR
                wXTIcli0q34tUPBegfiE3Wzp1I/h+nwoqRvkpzYpCaGCmThu4GWBC4/GQ8FN
                VtgqHeIxzi7u8EJmT8iIZw63VLwPtwXU0tpQk/ms1xmKH2DkO0bLdro8/u+9
                JekePLM30D0iPRQ9Z66aqz3DkbH1IXxYZV7rzMGW7i3yNNotR+DsnkzAxNnJ
                QwErdmy82giOyJM9jsJuZ5sC59LFXqX5SscvlOV6TlvSlXRn2XCCTFlWsYSG
                wFO9DtsdIWnJrLBDHnS+yxR8ROB8eBfvEOjG4YAhFS+t+cuFWgwrnRCstJtN
                3VmLYZVVlOMFVvQhY7VFG/ruz8XwsZD5O8EUqdvOmoJP0KvFQnlGIBfOgQEl
                wX4Kn47jk7gTUmbFWDEc06PMz/I8uHnpRrlQfjGs2A1iin0Fr8bxOXxeYDSU
                kZuthtE0LC8oWF/ccRpPNHTXzfe4FnSC5UsqXsOXBZ57BIcq+KrAf0KdP4+Y
                uZveKOzNiuHCc5vlQhbQjQNlDl9X8TV8Q95pu3L02krzqmk0aiwAhzbmS4an
                13RP51ykuRLlhV3Iz4D8QEAsc37VlKMR9mrnBBYe3jmtRo5Ftv5jW/2Hd9RI
                stP4o2MP7wztG42MCP5HXhD7Lvf/842BSDJ67UCy73gs1ZeKjAyM9E8dvZZM
                KscjI7HRgWScrTp1VEobDRuhXbf1C6EIdj4QSHi4532eNti/VaByy6wOJ260
                Lc9sGgVrxXRNPgkubV3PmdATdo0n4EHWdaPcbs4bzox8NsjCzZOiMas7phwH
                k/GKWWcBaTvs7694enW5pLeCtVM75WzeILcJTBSkAn4myYeJWrHbTtV/pggM
                BixmdwHFOdbWPmo3wHZQFlv2fyk9zzbJdlBeS4I5lXu+xVbOSxo+nfj9FUea
                jBG2/WfuQX2TnQh+HTABt/2G30RnA/ax55sTBxD1ice5O8I2tY6jD3HsPp4s
                ZlND93E6+9cdnOJdnFIBp9/6ew5uA83ygjRnJfe/UH4f20I2deY+zmb/hufu
                4p2l4VSeEoZT74r2i/uYeB1ZuSL6OCiUHuD83PA9FMvZdVxfx+zZu3h/6oMi
                GePqvFQvit/xS+3/iykFdR/iUSo3QLWGqFiWgMYIZYowJeQzhJDFSVRRI6wx
                HJEXdch0KgRqyF4GBqH/XppMBJrEsLhp5Ca3yp/zD7A0J+6h+RZa62inPppa
                S318HZ9Jquv4wgO8NpeKbFryHr5yF998C9ff9M0icZ6mGfcR7QE8ThHHiHCQ
                eI5zdIK9J/h7EjmuR/EH3+eCJ3WEkTGIP/qmfgN/YjvN+W9T4+/cRrSA7xbw
                vQK+jx+wi9cL+CF+dBvCxY/xk9tIuPLvpy5+5iLmYr+Ln7s44uJZF79wkXFh
                uKj8H5i9QWsnEAAA
                """,
                    """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAAAKVVW28bRRT+Zn13nWTjppC4DXGTuHXckHXSG9Rpipu0sNRO
                UNxaoDxt7K2ZeL0b7YwtioSUX8EPQDzyAIioiEoo6iM/CvWs161dOxEFHnbO
                nDPn+p0zs3/9/cefAG5gm2FamkJqh82GVn6mC9E2d80GF9J9FgFj0GpOSzPs
                uuvwuiYdxxKaxW2p1SxuEjEOuTZkFGAIr3Obyw2GQHapmkAI4TiCiDCcH4i1
                ZUqzJh03ghhDSK9UnjxguFY6I169p92PWEjgHBIxxDHGkG46kvQoW8siPe7Y
                Qtvs7x/JCCYoL4ty3HnKsJgtHRgdQ7MMu6Ht7B+QWmHJF7Ult7QS6ZH/SSTj
                UHGeLLkXUjCoo1oX8F4MCt6ncik5BqYnMIOUJ7tIli1uFw95ArO+6AOG7D9j
                WjXtugdNmmG56Gumdw5NO11x2m7NTH/hOl7Sd9IlMkw/IbTTjwlaEcU8Q4zQ
                6mh227KiWGT4drDYinS53Sj8N4le6sF80GlpFNh0bcPStsynRtuSm4SzdNte
                k8qG2zTdgt/7K3FcxlUCotOtiSF3Vo9H6idwl5DzULvGEJRfc2pAqnTWvBao
                8oYp9V6nktmRjjLcGxGu/4uJ2yAPCyXHbWgHptx3DU5TZti2Iw1/4rYduU2o
                k1aYEil6w0A3QPfzKnfnwN9Xe1AsUzrvDAbD3f+Z++Tr/pVNadQNaZBMaXUC
                9BQwbwl7C2iCmyT/hntcnnb1VYYfT46ycWVa8b8ofWqUaIBouicLvT6bPjla
                U/LsfujlD2FFVXan1EBKyQe/fPn9Fkmi8ZOjVDAaUsO7KTWSiiaDSSUfy0fp
                ONg/jqvnyC4xajdGdlPqOB1MvG2hqpNermsMq++A6fDkUNVj/TdppSmpURXe
                sA3Zdk2Gi7ttW/KWqdsdLvi+ZRb7bafR3HTqpDRBl9Hcbrf2TfexQTo0gyWn
                ZlhVw+Ue3xPG/Tv8kHvMTM9xdcQtVmnug9SCIJLeG0Pcp8Qp+Aifeb2ijNeI
                Jr23pktne5SuDJ0N6oSIhrqcTtx3iNEO0HLPEc39ivHfMXWM6ZwaO8alnBo5
                xlzuBS5/lVxgLJlRw+w5ssdY/qUb/HNac/Tmgi51EPMYxwKmsEihM5jDFZJm
                SHKV1gxWiHtEmgk/HD4kiQe0hjwC3VQ0b9i8BHO/YfrnNwHCXeH8gHGoZ+wj
                sPpWdQzX6U/GRhxe+mnI4cIpDhlunmo8N2y8eKrxLdwmrWHj5eFSMqcYD5ag
                oNRdH6JM1CDpx6R3Zw8BHQUd6zruYoO2uKfjExT3wATuY3MPSYEVgS2BsMCM
                wAOB6wI3BC509wkBTSAvMCtwU2BJICdwS+D2KzTjhGAACAAA
                """
                )
            ).testModes(TestMode.DEFAULT).createProjects(root)

            val lintJar = File(root, "app/lint.jar")
            assertTrue(lintJar.exists())

            val baseline = File(root, "baseline.xml")
            MainTest.checkDriver(
                // Expected output
                null, // not checked since it has an absolute path which depends on specific test machine
                // Expected error
                null,
                // Expected exit code
                ERRNO_CREATED_BASELINE,
                arrayOf(
                    "--exit-code",
                    "--ignore",
                    "LintBaseline",
                    "--baseline",
                    baseline.path,
                    "--update-baseline",
                    "--disable",
                    "LintError",
                    "--lint-rule-jars",
                    lintJar.path,
                    "--sdk-home",
                    TestUtils.getSdk().toFile().path,
                    projects[0].path
                ),
                { it.replace(root.path, "ROOT") },
                null
            )

            @Language("XML")
            val expected = """
                <?xml version="1.0" encoding="UTF-8"?>
                <issues>

                    <issue
                        id="MyIssueId"
                        message="My message">
                        <location
                            file="${'$'}GRADLE_USER_HOME/mypath.txt"/>
                    </issue>

                    <issue
                        id="MyIssueId"
                        message="My message">
                        <location
                            file="${'$'}ANDROID_HOME/platform-tools/package.xml"/>
                    </issue>

                    <issue
                        id="UsesMinSdkAttributes"
                        message="Manifest should specify a minimum API level with `&lt;uses-sdk android:minSdkVersion=&quot;?&quot; />`; if it really supports all versions of Android set it to 1">
                        <location
                            file="AndroidManifest.xml"/>
                    </issue>

                </issues>
            """.trimIndent()
            assertEquals(expected, readBaseline(baseline).dos2unix())
        } finally {
            gradleUserFile.delete()
        }
    }

    @Test
    fun testMissingBaselineIsEmptyBaseline_withLintIssue() {
        // Test the --missing-baseline-is-empty-baseline flag when there is a lint issue.
        // This test checks 3 scenarios:
        //   (1) --missing-baseline-is-empty-baseline is used, but not --update-baseline, in which
        //       case we expect the issue to be reported and no new baseline file to be written.
        //   (2) --missing-baseline-is-empty-baseline and --update-baseline are both used, in which
        //       case we expect a new baseline file to be written.
        //   (3) --missing-baseline-is-empty-baseline is not used, in which case we expect a new
        //       baseline file to be written.

        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFile = kotlin(
            """
            package test.pkg
            val path = "/sdcard/path"
            """
        ).indented()

        val baseline = File(root, "lint-baseline.xml")
        val project = lint().files(testFile).createProjects(root).single()

        // First run with --missing-baseline-is-empty-baseline flag and check that no baseline file
        // is written.
        MainTest.checkDriver(
            // Expected output
            "src/test/pkg/test.kt:2: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n" +
                "0 errors, 1 warnings",
            // Expected error
            "",
            // Expected exit code
            ERRNO_SUCCESS,
            arrayOf(
                "--missing-baseline-is-empty-baseline",
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        PathSubject.assertThat(baseline).doesNotExist()

        // Then run with --missing-baseline-is-empty-baseline and --update-baseline flags and check
        // that a baseline file is written.
        MainTest.checkDriver(
            // Expected output
            "src/test/pkg/test.kt:2: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n" +
                "0 errors, 1 warnings",
            // Expected error
            "",
            // Expected exit code
            ERRNO_CREATED_BASELINE,
            arrayOf(
                "--missing-baseline-is-empty-baseline",
                "--update-baseline",
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        @Language("XML")
        val expectedBaselineContents =
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <issues>

                    <issue
                        id="SdCardPath"
                        message="Do not hardcode &quot;/sdcard/&quot;; use `Environment.getExternalStorageDirectory().getPath()` instead">
                        <location
                            file="src/test/pkg/test.kt"
                            line="2"
                            column="13"/>
                    </issue>

                </issues>
            """.trimIndent()
        PathSubject.assertThat(baseline).exists()
        assertEquals(expectedBaselineContents, readBaseline(baseline))

        // Then run without --missing-baseline-is-empty-baseline flag and check that a baseline file
        // is written.
        baseline.delete()
        PathSubject.assertThat(baseline).doesNotExist()
        MainTest.checkDriver(
            // Expected output
            "src/test/pkg/test.kt:2: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n" +
                "0 errors, 1 warnings",
            // Expected error
            "Created baseline file ROOT" + File.separator + "lint-baseline.xml\n" +
                "\n" +
                "Also breaking the build in case this was not intentional. If you\n" +
                "deliberately created the baseline file, re-run the build and this\n" +
                "time it should succeed without warnings.\n" +
                "\n" +
                "If not, investigate the baseline path in the lintOptions config\n" +
                "or verify that the baseline file has been checked into version\n" +
                "control.\n",
            // Expected exit code
            ERRNO_CREATED_BASELINE,
            arrayOf(
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        PathSubject.assertThat(baseline).exists()
        assertEquals(expectedBaselineContents, readBaseline(baseline))
    }

    @Test
    fun testMissingBaselineIsEmptyBaseline_withoutLintIssue() {
        // Test the --missing-baseline-is-empty-baseline flag when there is not a lint issue.
        // This test checks 3 scenarios:
        //   (1) --missing-baseline-is-empty-baseline is used, in which we expect no new baseline
        //       file to be written.
        //   (2) --missing-baseline-is-empty-baseline is not used, in which case we expect a new
        //       baseline file to be written.
        //   (3) --missing-baseline-is-empty-baseline and --update-baseline are both used, in which
        //       case we expect the existing baseline file to be deleted.

        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFile = kotlin(
            """
            package test.pkg
            """
        ).indented()

        val baseline = File(root, "lint-baseline.xml")
        val project = lint().files(testFile).createProjects(root).single()

        // First run with --missing-baseline-is-empty-baseline flag and check that no baseline file
        // is written.
        MainTest.checkDriver(
            // Expected output
            "No issues found.",
            // Expected error
            "",
            // Expected exit code
            ERRNO_SUCCESS,
            arrayOf(
                "--missing-baseline-is-empty-baseline",
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        PathSubject.assertThat(baseline).doesNotExist()

        // Then run without --missing-baseline-is-empty-baseline flag and check that a baseline file
        // is written.
        MainTest.checkDriver(
            // Expected output
            "No issues found.",
            // Expected error
            "Created baseline file ROOT" + File.separator + "lint-baseline.xml\n" +
                "\n" +
                "Also breaking the build in case this was not intentional. If you\n" +
                "deliberately created the baseline file, re-run the build and this\n" +
                "time it should succeed without warnings.\n" +
                "\n" +
                "If not, investigate the baseline path in the lintOptions config\n" +
                "or verify that the baseline file has been checked into version\n" +
                "control.\n",
            // Expected exit code
            ERRNO_CREATED_BASELINE,
            arrayOf(
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        @Language("XML")
        val expectedBaselineContents =
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <issues>

                </issues>
            """.trimIndent()
        PathSubject.assertThat(baseline).exists()
        assertEquals(expectedBaselineContents, readBaseline(baseline))

        // Then run with --missing-baseline-is-empty-baseline and --update-baseline flags and check
        // that the baseline file is deleted.
        MainTest.checkDriver(
            // Expected output
            "No issues found.",
            // Expected error
            "",
            // Expected exit code
            ERRNO_CREATED_BASELINE,
            arrayOf(
                "--missing-baseline-is-empty-baseline",
                "--update-baseline",
                "--check",
                "SdCardPath",
                "--nolines",
                "--baseline",
                baseline.path,
                "--disable",
                "LintError",
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        PathSubject.assertThat(baseline).doesNotExist()
    }

    @Test
    fun testLocationMessage() {
        // Makes sure that if there's a location specific message, it doesn't override the incident message
        @Language("XML")
        val baselineContents = """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues format="6" by="lint 7.3.0-dev" type="baseline" client="gradle" dependencies="false" name="AGP (7.3.0-dev)" variant="all" version="7.3.0-dev">

                <issue
                    id="InconsistentLayout"
                    message="The id &quot;hello1&quot; in layout &quot;activity_main&quot; is missing from the following layout configurations: layout (present in layout-sw600dp)"
                    errorLine1="        android:id=&quot;@+id/hello1&quot;"
                    errorLine2="        ~~~~~~~~~~~~~~~~~~~~~~~~">
                    <location
                        file="src/main/res/layout-sw600dp/activity_main.xml"
                        line="19"
                        column="9"
                        message="Occurrence in layout-sw600dp"/>
                </issue>

            </issues>
        """.trimIndent()

        // Test 1: Test matching where we look at the wrong file and return instead of getting to the next one
        val baselineFile = temporaryFolder.newFile("baseline.xml")
        baselineFile.writeText(baselineContents)
        assertNotNull(XmlUtils.parseDocumentSilently(baselineContents, false))
        val baseline = LintBaseline(ToolsBaseTestLintClient(), baselineFile)
        assertTrue(
            baseline.findAndMark(
                LayoutConsistencyDetector.INCONSISTENT_IDS,
                Location.create(File("src/main/res/layout-sw600dp/activity_main.xml")),
                "The id \"hello1\" in layout \"activity_main\" is missing from the following layout configurations: layout (present in layout-sw600dp)",
                Severity.WARNING,
                null
            )
        )
        baseline.close()
    }

    @Test
    fun testRelativePathsInIconMessages() {
        // Make sure that the IconMissingDensityFolder check does not write absolute paths in baseline messages
        // Regression test for https://issuetracker.google.com/220161119
        val root = temporaryFolder.newFolder().canonicalFile.absoluteFile

        val testFiles = arrayOf(
            image("res/drawable-mdpi/frame.png", 472, 290)
                .fill(-0x1)
                .fill(10, 10, 362, 280, 0x00000000),
            image("res/drawable-nodpi/frame.png", 472, 290)
                .fill(-0x1)
                .fill(10, 10, 362, 280, 0x00000000),
            image("res/drawable-xlarge-nodpi-v11/frame.png", 472, 290)
                .fill(-0x1)
                .fill(10, 10, 362, 280, 0x00000000),
        )
        val baselineFolder = File(root, "baselines")
        baselineFolder.mkdirs()
        val outputBaseline = File(baselineFolder, "baseline-out.xml")

        val project = lint().files(*testFiles).createProjects(root).single()

        MainTest.checkDriver(
            // Expected output
            "ROOT/app/res: Warning: Missing density variation folders in res: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi [IconMissingDensityFolder]\n" +
                "0 errors, 1 warnings",
            // Expected error
            "",
            // Expected exit code
            ERRNO_SUCCESS,
            arrayOf(
                "--exit-code",
                "--check",
                "IconMissingDensityFolder",
                "--ignore",
                "LintBaseline",
                "--fullpath",
                "--write-reference-baseline",
                outputBaseline.path,
                "--disable",
                "LintError",
                "--sdk-home",
                TestUtils.getSdk().toFile().path,
                project.path
            ),
            { it.replace(root.path, "ROOT") },
            null
        )

        @Language("XML")
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues>

                <issue
                    id="IconMissingDensityFolder"
                    message="Missing density variation folders in `res`: drawable-hdpi, drawable-xhdpi, drawable-xxhdpi">
                    <location
                        file="res"/>
                </issue>

            </issues>
        """.trimIndent()
        assertEquals(expected, readBaseline(outputBaseline).dos2unix()) // b/209433064
    }

    @Test
    fun testPrefixMatchLength() {
        assertEquals(0, prefixMatchLength("", ""))
        assertEquals(0, prefixMatchLength("a", "b"))
        assertEquals(0, prefixMatchLength("a", ""))
        assertEquals(0, prefixMatchLength("", "b"))
        assertEquals(4, prefixMatchLength("abcd", "abcd"))
        assertEquals(4, prefixMatchLength("abcde", "abcdf"))
        assertEquals(3, prefixMatchLength("abcXabcd", "abcYYabcd"))
    }

    @Test
    fun testSuffixMatchLength() {
        assertEquals(0, suffixMatchLength("", ""))
        assertEquals(0, suffixMatchLength("a", "b"))
        assertEquals(0, suffixMatchLength("a", ""))
        assertEquals(0, suffixMatchLength("", "b"))
        assertEquals(4, suffixMatchLength("abcd", "abcd"))
        assertEquals(0, suffixMatchLength("abcde", "abcdf"))
        assertEquals(3, suffixMatchLength("abcdXabc", "abcdYYabc"))
    }

    @Test
    fun testSameWithAbsolutePath() {
        assertTrue(sameWithAbsolutePath("", ""))
        assertTrue(sameWithAbsolutePath("foo", "/path/to/foo"))
        assertTrue(sameWithAbsolutePath("the path is `foo`!", "the path is `/path/to/foo`!"))
        assertTrue(sameWithAbsolutePath("the path is `foo`!", "the path is `/path/to/foo`!", "the", "!"))

        assertFalse(sameWithAbsolutePath("/path/to/foo", "foo"))
        assertFalse(sameWithAbsolutePath("foo", "bar"))
        assertFalse(sameWithAbsolutePath("the path is `bar`!", "the path is `/path/to/foo`!"))
        assertFalse(sameWithAbsolutePath("foo", "/path/to/foo", "the"))
        assertFalse(sameWithAbsolutePath("foo", "/path/to/foo", "", "the"))
    }

    companion object {
        /**
         * Read the given [baseline] file and strip out the version
         * details in the root tag which can change over time to make
         * the golden files stable.
         */
        fun readBaseline(baseline: File): String {
            val newBaseline = baseline.readText().trim().let {
                // Filter out header attributes which would make the test file change over
                // time, like "<issues format="5" by="lint 7.1.0-dev">"
                val start = it.indexOf("<issues ") + 7
                val end = it.indexOf('>', start)
                it.substring(0, start) + it.substring(end)
            }
            return newBaseline
        }
    }
}
