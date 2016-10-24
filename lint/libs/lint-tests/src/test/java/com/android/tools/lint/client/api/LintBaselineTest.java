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

package com.android.tools.lint.client.api;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import junit.framework.TestCase;

public class LintBaselineTest extends TestCase {
    public void testBaseline() throws IOException {
        File baselineFile = File.createTempFile("baseline", ".xml");
        baselineFile.deleteOnExit();
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<issues format=\"4\" by=\"lint unittest\">\n"
                + "\n"
                + "    <issue\n"
                + "        id=\"UsesMinSdkAttributes\"\n"
                + "        severity=\"Warning\"\n"
                + "        message=\"&lt;uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion=&quot;?&quot;\"\n"
                + "        category=\"Correctness\"\n"
                + "        priority=\"9\"\n"
                + "        summary=\"Minimum SDK and target SDK attributes not defined\"\n"
                + "        explanation=\"The manifest should contain a `&lt;uses-sdk>` element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for.)\"\n"
                + "        url=\"http://developer.android.com/guide/topics/manifest/uses-sdk-element.html\"\n"
                + "        urls=\"http://developer.android.com/guide/topics/manifest/uses-sdk-element.html\"\n"
                + "        errorLine1=\"    &lt;uses-sdk android:minSdkVersion=&quot;8&quot; />\"\n"
                + "        errorLine2=\"    ^\">\n"
                + "        <location\n"
                + "            file=\"AndroidManifest.xml\"\n"
                + "            line=\"7\"\n"
                + "            column=\"5\"/>\n"
                + "    </issue>\n"
                + "\n"
                + "    <issue\n"
                + "        id=\"HardcodedText\"\n"
                + "        severity=\"Warning\"\n"
                + "        message=\"[I18N] Hardcoded string &quot;Fooo&quot;, should use @string resource\"\n"
                + "        category=\"Internationalization\"\n"
                + "        priority=\"5\"\n"
                + "        summary=\"Hardcoded text\"\n"
                + "        explanation=\"Hardcoding text attributes directly in layout files is bad for several reasons:\n"
                + "\n"
                + "* When creating configuration variations (for example for landscape or portrait)you have to repeat the actual text (and keep it up to date when making changes)\n"
                + "\n"
                + "* The application cannot be translated to other languages by just adding new translations for existing string resources.\n"
                + "\n"
                + "There are quickfixes to automatically extract this hardcoded string into a resource lookup.\"\n"
                + "        errorLine1=\"        android:text=&quot;Fooo&quot; />\"\n"
                + "        errorLine2=\"        ~~~~~~~~~~~~~~~~~~~\">\n"
                + "        <location\n"
                + "            file=\"res/layout/main.xml\"\n"
                + "            line=\"12\"\n"
                + "            column=\"9\"/>\n"
                + "        <location\n"
                + "            file=\"res/layout/main2.xml\"\n"
                + "            line=\"11\"\n"
                + "            column=\"19\"/>\n"
                + "    </issue>\n"
                + "\n"
                + "</issues>\n", baselineFile, Charsets.UTF_8);

        LintBaseline baseline = new LintBaseline(null, baselineFile);

        boolean found;
        found = baseline.findAndMark(ManifestDetector.USES_SDK,
                Location.create(new File("bogus")), "Unrelated)", Severity.WARNING);
        assertThat(found).isFalse();
        assertThat(baseline.getFoundWarningCount()).isEqualTo(0);
        assertThat(baseline.getFoundErrorCount()).isEqualTo(0);
        // because we haven't actually matched anything
        assertThat(baseline.getFixedCount()).isEqualTo(2);

        // Wrong issue
        found = baseline.findAndMark(ManifestDetector.USES_SDK,
                Location.create(new File("bogus")),
                "[I18N] Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING);
        assertThat(found).isFalse();
        assertThat(baseline.getFoundWarningCount()).isEqualTo(0);
        assertThat(baseline.getFoundErrorCount()).isEqualTo(0);
        assertThat(baseline.getFixedCount()).isEqualTo(2);

        // Wrong file
        found = baseline.findAndMark(HardcodedValuesDetector.ISSUE,
                Location.create(new File("res/layout-port/main.xml")),
                "[I18N] Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING);
        assertThat(found).isFalse();
        assertThat(baseline.getFoundWarningCount()).isEqualTo(0);
        assertThat(baseline.getFoundErrorCount()).isEqualTo(0);
        assertThat(baseline.getFixedCount()).isEqualTo(2);

        // Match
        found = baseline.findAndMark(HardcodedValuesDetector.ISSUE,
                Location.create(new File("res/layout/main.xml")),
                "[I18N] Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING);
        assertThat(found).isTrue();
        assertThat(baseline.getFixedCount()).isEqualTo(1);
        assertThat(baseline.getFoundWarningCount()).isEqualTo(1);
        assertThat(baseline.getFoundErrorCount()).isEqualTo(0);
        assertThat(baseline.getFixedCount()).isEqualTo(1);

        // Search for the same error once it's already been found: no longer there
        found = baseline.findAndMark(HardcodedValuesDetector.ISSUE,
                Location.create(new File("res/layout/main.xml")),
                "[I18N] Hardcoded string \"Fooo\", should use @string resource", Severity.WARNING);
        assertThat(found).isFalse();
        assertThat(baseline.getFoundWarningCount()).isEqualTo(1);
        assertThat(baseline.getFoundErrorCount()).isEqualTo(0);
        assertThat(baseline.getFixedCount()).isEqualTo(1);
    }

    public void testSuffix() {
        assertTrue(LintBaseline.isSamePathSuffix("foo", "foo"));
        assertTrue(LintBaseline.isSamePathSuffix("", ""));
        assertTrue(LintBaseline.isSamePathSuffix("abc/def/foo", "def/foo"));
        assertTrue(LintBaseline.isSamePathSuffix("abc\\def\\foo", "abc\\def\\foo"));
        assertTrue(LintBaseline.isSamePathSuffix("abc\\def\\foo", "def\\foo"));
        assertFalse(LintBaseline.isSamePathSuffix("foo", "bar"));
    }
}