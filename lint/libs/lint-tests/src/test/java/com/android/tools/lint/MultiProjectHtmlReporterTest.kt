/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location.Companion.create
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import java.io.File
import java.util.ArrayList

class MultiProjectHtmlReporterTest : AbstractCheckTest() {
    fun testBasic() {
        val dir = File(targetDir, "report")
        try {
            val client: LintCliClient = object : LintCliClient(CLIENT_UNIT_TESTS) {
                override var registry: IssueRegistry?
                    get() {
                        var registry = super.registry
                        if (registry == null) {
                            registry = object : IssueRegistry() {
                                // Not reported, but for the disabled-list
                                override val issues: List<Issue>
                                    get() = listOf(
                                        ManifestDetector.USES_SDK,
                                        HardcodedValuesDetector.ISSUE,
                                        // Not reported, but for the disabled-list
                                        ManifestDetector.MOCK_LOCATION
                                    )
                            }
                            super.registry = registry
                        }
                        return registry
                    }
                    protected set(registry) {
                        super.registry = registry
                    }
            }
            dir.mkdirs()
            val reporter =
                MultiProjectHtmlReporter(client, dir, LintCliFlags())
            val project =
                Project.create(
                    client,
                    File("/foo/bar/Foo"),
                    File("/foo/bar/Foo")
                )
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
            ).apply {
                this.project = project
                severity = Severity.WARNING
            }
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
            ).apply {
                this.project = project
                severity = Severity.WARNING
            }
            val incidents: MutableList<Incident> = ArrayList()
            incidents.add(incident1)
            incidents.add(incident2)
            reporter.write(create(0, 2), incidents)
            var report = File(dir, "index.html").readText()

            // Replace the timestamp to make golden file comparison work
            val timestampPrefix = "Check performed at "
            var begin = report.indexOf(timestampPrefix)
            assertTrue(begin != -1)
            begin += timestampPrefix.length
            val end = report.indexOf("</nav>", begin)
            assertTrue(end != -1)
            report = report.substring(0, begin) + "\$DATE" + report.substring(end)

            // NOTE: If you change the output, please validate it manually in
            //  http://validator.w3.org/#validate_by_input
            // before updating the following
            assertEquals(
                """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>Lint Report</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
 <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.blue-indigo.min.css" />
<link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Roboto:300,400,500,700" type="text/css">
<script defer src="https://code.getmdl.io/1.2.0/material.min.js"></script>
<style>
${HtmlReporter.cssStyles}</style>
<script language="javascript" type="text/javascript">
<!--
function reveal(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'block';
document.getElementById(id+'Link').style.display = 'none';
}
}
function hideid(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'none';
}
}
//-->
</script>
</head>
<body class="mdl-color--grey-100 mdl-color-text--grey-700 mdl-base">
<div class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
  <header class="mdl-layout__header">
    <div class="mdl-layout__header-row">
      <span class="mdl-layout-title">Lint Report: 2 warnings</span>
      <div class="mdl-layout-spacer"></div>
      <nav class="mdl-navigation mdl-layout--large-screen-only">
Check performed at ${"$"}DATE</nav>
    </div>
  </header>
  <div class="mdl-layout__drawer">
    <span class="mdl-layout-title">Issue Types</span>
    <nav class="mdl-navigation">
      <a class="mdl-navigation__link" href="Foo.html">Foo (2)</a>
    </nav>
  </div>
  <main class="mdl-layout__content">
    <div class="mdl-layout__tab-panel is-active"><section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="OverviewCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Projects</h2>
  </div>
              <div class="mdl-card__supporting-text">
<table class="overview">
<tr><th>Project</th><th class="countColumn">Errors</th><th class="countColumn">Warnings</th></tr>
<tr><td><a href="Foo.html">Foo</a></td><td class="countColumn">0</td><td class="countColumn">2</td></tr>
<tr>
</table>
<br/>            </div>
            </div>
          </section>    </div>
  </main>
</div>
</body>
</html>""",
                report
            )
        } finally {
            dir.delete()
        }
    }

    override fun getDetector(): Detector {
        error("Not used in this test")
    }
}
