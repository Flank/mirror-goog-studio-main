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

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.RangeDetector
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.IOException

class LintBaselineTest : AbstractCheckTest() {
    @Throws(IOException::class)
    fun testBaseline() {
        val baselineFile = File.createTempFile("baseline", ".xml")
        baselineFile.deleteOnExit()

        @Language("XML")
        val baselineContents = """<?xml version="1.0" encoding="UTF-8"?>
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
"""
        Files.asCharSink(baselineFile, Charsets.UTF_8).write(baselineContents)

        val baseline = LintBaseline(createClient(), baselineFile)

        var found: Boolean
        found = baseline.findAndMark(
            ManifestDetector.USES_SDK,
            Location.create(File("bogus")), "Unrelated)", Severity.WARNING, null
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

        // Match
        found = baseline.findAndMark(
            RangeDetector.RANGE,
            Location.create(
                File(
                    "java/android/support/v4/widget/SlidingPaneLayout.java"
                )
            ),
            "Value must be \u2265 0 (was -1)", Severity.WARNING, null
        )
        assertThat(found).isTrue()
        assertThat(baseline.fixedCount).isEqualTo(1)
        assertThat(baseline.foundWarningCount).isEqualTo(2)
        assertThat(baseline.foundErrorCount).isEqualTo(0)
        assertThat(baseline.fixedCount).isEqualTo(1)

        baseline.close()
    }

    fun testSuffix() {
        assertTrue(LintBaseline.isSamePathSuffix("foo", "foo"))
        assertTrue(LintBaseline.isSamePathSuffix("", ""))
        assertTrue(LintBaseline.isSamePathSuffix("abc/def/foo", "def/foo"))
        assertTrue(LintBaseline.isSamePathSuffix("abc/def/foo", "../../def/foo"))
        assertTrue(LintBaseline.isSamePathSuffix("abc\\def\\foo", "abc\\def\\foo"))
        assertTrue(LintBaseline.isSamePathSuffix("abc\\def\\foo", "..\\..\\abc\\def\\foo"))
        assertTrue(LintBaseline.isSamePathSuffix("abc\\def\\foo", "def\\foo"))
        assertFalse(LintBaseline.isSamePathSuffix("foo", "bar"))
    }

    @Throws(IOException::class)
    fun testFormat() {
        val baselineFile = File.createTempFile("lint-baseline", ".xml")
        var baseline = LintBaseline(createClient(), baselineFile)
        assertThat(baseline.isWriteOnClose).isFalse()
        baseline.isWriteOnClose = true
        assertThat(baseline.isWriteOnClose).isTrue()

        baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("my/source/file.txt"), "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, null
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

        var actual = Files.asCharSource(baselineFile, Charsets.UTF_8).read()
            .replace(File.separatorChar, '/')

        @Language("XML")
        val expected = ("""<?xml version="1.0" encoding="UTF-8"?>
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
            file="my/source/file.txt"
            line="1"/>
    </issue>

</issues>
""")
        assertThat(actual).isEqualTo(expected)

        // Now load the baseline back in and make sure we can match entries correctly
        baseline = LintBaseline(createClient(), baselineFile)
        baseline.isWriteOnClose = true
        assertThat(baseline.isRemoveFixed).isFalse()

        var found: Boolean
        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("my/source/file.txt"), "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, null
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

        actual = Files.asCharSource(baselineFile, Charsets.UTF_8).read()
            .replace(File.separatorChar, '/')
        assertThat(actual).isEqualTo(expected)

        // Test the skip fix flag
        baseline = LintBaseline(createClient(), baselineFile)
        baseline.isWriteOnClose = true
        baseline.isRemoveFixed = true
        assertThat(baseline.isRemoveFixed).isTrue()

        found = baseline.findAndMark(
            HardcodedValuesDetector.ISSUE,
            Location.create(File("my/source/file.txt"), "", 0),
            "Hardcoded string \"Fooo\", should use `@string` resource",
            Severity.WARNING, null
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

        actual = Files.asCharSource(baselineFile, Charsets.UTF_8).read()
            .replace(File.separatorChar, '/')

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
                    "            file=\"my/source/file.txt\"\n" +
                    "            line=\"1\"/>\n" +
                    "    </issue>\n" +
                    "\n" +
                    "</issues>\n"
        )
    }

    override fun getDetector(): Detector? {
        fail("Not used by this test")
        return null
    }
}