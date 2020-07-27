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
package com.android.tools.lint

import com.android.tools.lint.LintStats.Companion.create
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Location.Companion.create
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import java.io.File
import java.io.FileWriter
import java.util.ArrayList
import java.util.Collections

class TextReporterTest : AbstractCheckTest() {
    fun testBasic() {
        val file = File(targetDir, "report")
        try {
            val client: LintCliClient = createClient()
            file.parentFile.mkdirs()
            val writer = FileWriter(file)
            val flags = client.flags
            val reporter = TextReporter(client, flags, file, writer, true)
            val project = Project.create(
                client,
                File("/foo/bar/Foo"),
                File("/foo/bar/Foo")
            )
            flags.isShowEverything = true
            val location1 =
                create(
                    File("/foo/bar/Foo/AndroidManifest.xml"),
                    DefaultPosition(6, 4, 198),
                    DefaultPosition(6, 42, 236)
                )
            val incident1 = Incident(
                ManifestDetector.USES_SDK,
                "<uses-sdk> tag should specify a target API level (the highest verified " +
                    "version; when running on later versions, compatibility behaviors may " +
                    "be enabled) with android:targetSdkVersion=\"?\"",
                location1,
                null
            ).apply { this.project = project; this.severity = Severity.WARNING }
            createTextWithLineAt(
                client,
                "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n",
                location1
            )
            var secondary = create(
                incident1.file,
                DefaultPosition(7, 4, 198),
                DefaultPosition(7, 42, 236)
            )
            secondary.message = "Secondary location"
            incident1.location.secondary = secondary
            val location2 =
                create(
                    File("/foo/bar/Foo/res/layout/main.xml"),
                    DefaultPosition(11, 8, 377),
                    DefaultPosition(11, 27, 396)
                )
            val incident2 = Incident(
                HardcodedValuesDetector.ISSUE,
                "Hardcoded string \"Fooo\", should use @string resource",
                location2,
                null
            ).apply { this.project = project; this.severity = Severity.WARNING }
            createTextWithLineAt(
                client,
                "        android:text=\"Fooo\" />\n        ~~~~~~~~~~~~~~~~~~~\n",
                location2
            )
            secondary = create(
                incident1.file,
                DefaultPosition(7, 4, 198),
                DefaultPosition(7, 42, 236)
            )
            secondary.message = "Secondary location"
            incident2.location.secondary = secondary
            val tertiary = create(
                incident2.file,
                DefaultPosition(5, 4, 198),
                DefaultPosition(5, 42, 236)
            )
            secondary.secondary = tertiary
            val incidents: MutableList<Incident> = ArrayList()
            incidents.add(incident1)
            incidents.add(incident2)
            Collections.sort(incidents)
            reporter.write(create(0, 2), incidents)
            val report = file.readText()
            assertEquals(
                """
                AndroidManifest.xml:7: Warning: <uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion="?" [UsesMinSdkAttributes]
                    <uses-sdk android:minSdkVersion="8" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:8: Secondary location
                res/layout/main.xml:12: Warning: Hardcoded string "Fooo", should use @string resource [HardcodedText]
                        android:text="Fooo" />
                        ~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:8: Secondary location
                Also affects: res/layout/main.xml:6
                0 errors, 2 warnings

                """.trimIndent(),
                report.replace(File.separatorChar, '/')
            )
        } finally {
            file.delete()
        }
    }

    fun testWithExplanations() {
        val file = File(targetDir, "report")
        try {
            val client: LintCliClient = createClient()
            file.parentFile.mkdirs()
            val writer = FileWriter(file)
            val flags = client.flags
            val reporter =
                TextReporter(client, flags, file, writer, true)
            flags.isExplainIssues = true
            val project =
                Project.create(
                    client,
                    File("/foo/bar/Foo"),
                    File("/foo/bar/Foo")
                )
            flags.isShowEverything = true
            val location1 =
                create(
                    File("/foo/bar/Foo/AndroidManifest.xml"),
                    DefaultPosition(6, 4, 198),
                    DefaultPosition(6, 42, 236)
                )
            val incident1 = Incident(
                ManifestDetector.USES_SDK,
                "<uses-sdk> tag should specify a target API level (the highest verified " +
                    "version; when running on later versions, compatibility behaviors may " +
                    "be enabled) with android:targetSdkVersion=\"?\"",
                location1,
                null
            ).apply { this.project = project; severity = Severity.WARNING }
            createTextWithLineAt(
                client,
                "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n",
                location1
            )
            var secondary =
                create(
                    incident1.file,
                    DefaultPosition(7, 4, 198),
                    DefaultPosition(7, 42, 236)
                )
            secondary.message = "Secondary location"
            incident1.location.secondary = secondary
            val location2 =
                create(
                    File("/foo/bar/Foo/res/layout/main.xml"),
                    DefaultPosition(11, 8, 377),
                    DefaultPosition(11, 27, 396)
                )
            val incident2 = Incident(
                HardcodedValuesDetector.ISSUE,
                "Hardcoded string \"Fooo\", should use @string resource",
                location2,
                null
            ).apply { this.project = project; severity = Severity.WARNING }
            createTextWithLineAt(
                client,
                "        android:text=\"Fooo\" />\n        ~~~~~~~~~~~~~~~~~~~\n",
                location2
            )
            secondary = create(
                incident1.file,
                DefaultPosition(7, 4, 198),
                DefaultPosition(7, 42, 236)
            )
            secondary.message = "Secondary location"
            incident2.location.secondary = secondary
            val tertiary =
                create(
                    incident2.file,
                    DefaultPosition(5, 4, 198),
                    DefaultPosition(5, 42, 236)
                )
            secondary.secondary = tertiary

            // Add another warning of the same type as warning 1 to make sure we
            // (1) sort the warnings of the same issue together and (2) only print
            // the explanation twice1
            val location3 =
                create(
                    File("/foo/bar/Foo/AndroidManifest2.xml"),
                    DefaultPosition(8, 4, 198),
                    DefaultPosition(8, 42, 236)
                )
            val incident3 = Incident(
                ManifestDetector.USES_SDK,
                "<uses-sdk> tag should specify a target API level (the highest verified " +
                    "version; when running on later versions, compatibility behaviors may " +
                    "be enabled) with android:targetSdkVersion=\"?\"",
                location3,
                null
            ).apply { this.project = project; severity = Severity.WARNING }
            createTextWithLineAt(
                client,
                "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n",
                location3
            )
            val incidents: MutableList<Incident> = ArrayList()
            incidents.add(incident1)
            incidents.add(incident2)
            incidents.add(incident3)
            Collections.sort(incidents)
            reporter.write(create(0, 3), incidents)
            val report = file.readText()
            assertEquals(
                """
                AndroidManifest.xml:7: Warning: <uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion="?" [UsesMinSdkAttributes]
                    <uses-sdk android:minSdkVersion="8" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:8: Secondary location
                AndroidManifest2.xml:9: Warning: <uses-sdk> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with android:targetSdkVersion="?" [UsesMinSdkAttributes]
                    <uses-sdk android:minSdkVersion="8" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

                   Explanation for issues of type "UsesMinSdkAttributes":
                   The manifest should contain a <uses-sdk> element which defines the minimum
                   API Level required for the application to run, as well as the target
                   version (the highest API level you have tested the version for).

                   https://developer.android.com/guide/topics/manifest/uses-sdk-element.html

                res/layout/main.xml:12: Warning: Hardcoded string "Fooo", should use @string resource [HardcodedText]
                        android:text="Fooo" />
                        ~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:8: Secondary location
                Also affects: res/layout/main.xml:6

                   Explanation for issues of type "HardcodedText":
                   Hardcoding text attributes directly in layout files is bad for several
                   reasons:

                   * When creating configuration variations (for example for landscape or
                   portrait) you have to repeat the actual text (and keep it up to date when
                   making changes)

                   * The application cannot be translated to other languages by just adding
                   new translations for existing string resources.

                   There are quickfixes to automatically extract this hardcoded string into a
                   resource lookup.

                0 errors, 3 warnings

                """.trimIndent(),
                report.replace(File.separatorChar, '/')
            )
        } finally {
            file.delete()
        }
    }

    override fun getDetector(): Detector {
        error("Not used in this test")
    }

    // Test utility which helps [Incident.getErrorLines()] work such that it will return the
    // given error lines at the given location. This works by creating a fake source file
    // which has the lines at the given location and storing that in the client's source map.
    // Note that this only works when there's a single incident in a file.
    private fun createTextWithLineAt(
        client: LintCliClient,
        errorLines: String,
        location: Location
    ) {
        val line = location.start?.line ?: return
        val sb = StringBuilder()
        for (i in 0 until line) {
            sb.append("\n")
        }
        sb.append(errorLines)
        client.setSourceText(location.file, sb)
    }
}
