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

package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MultiProjectHtmlReporterTest extends AbstractCheckTest {
    public void testBasic() throws Exception {
        File dir = new File(getTargetDir(), "report");
        try {
            LintCliClient client =
                    new LintCliClient() {
                        @Override
                        IssueRegistry getRegistry() {
                            if (registry == null) {
                                registry =
                                        new IssueRegistry() {
                                            @NonNull
                                            @Override
                                            public List<Issue> getIssues() {
                                                return Arrays.asList(
                                                        ManifestDetector.USES_SDK,
                                                        HardcodedValuesDetector.ISSUE,
                                                        // Not reported, but for the disabled-list
                                                        ManifestDetector.MOCK_LOCATION);
                                            }
                                        };
                            }
                            return registry;
                        }
                    };

            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            MultiProjectHtmlReporter reporter =
                    new MultiProjectHtmlReporter(client, dir, new LintCliFlags());
            Project project =
                    Project.create(client, new File("/foo/bar/Foo"), new File("/foo/bar/Foo"));

            Warning warning1 =
                    new Warning(
                            ManifestDetector.USES_SDK,
                            "<uses-sdk> tag should specify a target API level (the highest verified "
                                    + "version; when running on later versions, compatibility behaviors may "
                                    + "be enabled) with android:targetSdkVersion=\"?\"",
                            Severity.WARNING,
                            project);
            warning1.line = 6;
            warning1.file = new File("/foo/bar/Foo/AndroidManifest.xml");
            warning1.errorLine = "    <uses-sdk android:minSdkVersion=\"8\" />\n    ^\n";
            warning1.path = "AndroidManifest.xml";
            warning1.location =
                    Location.create(
                            warning1.file,
                            new DefaultPosition(6, 4, 198),
                            new DefaultPosition(6, 42, 236));

            Warning warning2 =
                    new Warning(
                            HardcodedValuesDetector.ISSUE,
                            "Hardcoded string \"Fooo\", should use @string resource",
                            Severity.WARNING,
                            project);
            warning2.line = 11;
            warning2.file = new File("/foo/bar/Foo/res/layout/main.xml");
            warning2.errorLine =
                    " (java.lang.String)         android:text=\"Fooo\" />\n"
                            + "        ~~~~~~~~~~~~~~~~~~~\n";
            warning2.path = "res/layout/main.xml";
            warning2.location =
                    Location.create(
                            warning2.file,
                            new DefaultPosition(11, 8, 377),
                            new DefaultPosition(11, 27, 396));

            List<Warning> warnings = new ArrayList<>();
            warnings.add(warning1);
            warnings.add(warning2);

            reporter.write(LintStats.Companion.create(0, 2), warnings);

            String report = Files.asCharSource(new File(dir, "index.html"), Charsets.UTF_8).read();

            // Replace the timestamp to make golden file comparison work
            String timestampPrefix = "Check performed at ";
            int begin = report.indexOf(timestampPrefix);
            assertTrue(begin != -1);
            begin += timestampPrefix.length();
            int end = report.indexOf("</nav>", begin);
            assertTrue(end != -1);
            report = report.substring(0, begin) + "$DATE" + report.substring(end);

            // NOTE: If you change the output, please validate it manually in
            //  http://validator.w3.org/#validate_by_input
            // before updating the following
            assertEquals(
                    ""
                            + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                            + "\n"
                            + "<head>\n"
                            + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                            + "<title>Lint Report</title>\n"
                            + "<link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/icon?family=Material+Icons\">\n"
                            + " <link rel=\"stylesheet\" href=\"https://code.getmdl.io/1.2.1/material.blue-indigo.min.css\" />\n"
                            + "<link rel=\"stylesheet\" href=\"http://fonts.googleapis.com/css?family=Roboto:300,400,500,700\" type=\"text/css\">\n"
                            + "<script defer src=\"https://code.getmdl.io/1.2.0/material.min.js\"></script>\n"
                            + "<style>\n"
                            + HtmlReporter.CSS_STYLES
                            + "</style>\n"
                            + "<script language=\"javascript\" type=\"text/javascript\"> \n"
                            + "<!--\n"
                            + "function reveal(id) {\n"
                            + "if (document.getElementById) {\n"
                            + "document.getElementById(id).style.display = 'block';\n"
                            + "document.getElementById(id+'Link').style.display = 'none';\n"
                            + "}\n"
                            + "}\n"
                            + "function hideid(id) {\n"
                            + "if (document.getElementById) {\n"
                            + "document.getElementById(id).style.display = 'none';\n"
                            + "}\n"
                            + "}\n"
                            + "//--> \n"
                            + "</script>\n"
                            + "</head>\n"
                            + "<body class=\"mdl-color--grey-100 mdl-color-text--grey-700 mdl-base\">\n"
                            + "<div class=\"mdl-layout mdl-js-layout mdl-layout--fixed-header\">\n"
                            + "  <header class=\"mdl-layout__header\">\n"
                            + "    <div class=\"mdl-layout__header-row\">\n"
                            + "      <span class=\"mdl-layout-title\">Lint Report: 2 warnings</span>\n"
                            + "      <div class=\"mdl-layout-spacer\"></div>\n"
                            + "      <nav class=\"mdl-navigation mdl-layout--large-screen-only\">\n"
                            + "Check performed at $DATE</nav>\n"
                            + "    </div>\n"
                            + "  </header>\n"
                            + "  <div class=\"mdl-layout__drawer\">\n"
                            + "    <span class=\"mdl-layout-title\">Issue Types</span>\n"
                            + "    <nav class=\"mdl-navigation\">\n"
                            + "      <a class=\"mdl-navigation__link\" href=\"Foo.html\">Foo (2)</a>\n"
                            + "    </nav>\n"
                            + "  </div>\n"
                            + "  <main class=\"mdl-layout__content\">\n"
                            + "    <div class=\"mdl-layout__tab-panel is-active\"><section class=\"section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp\" id=\"OverviewCard\" style=\"display: block;\">\n"
                            + "            <div class=\"mdl-card mdl-cell mdl-cell--12-col\">\n"
                            + "  <div class=\"mdl-card__title\">\n"
                            + "    <h2 class=\"mdl-card__title-text\">Projects</h2>\n"
                            + "  </div>\n"
                            + "              <div class=\"mdl-card__supporting-text\">\n"
                            + "<table class=\"overview\">\n"
                            + "<tr><th>Project</th><th class=\"countColumn\">Errors</th><th class=\"countColumn\">Warnings</th></tr>\n"
                            + "<tr><td><a href=\"Foo.html\">Foo</a></td><td class=\"countColumn\">0</td><td class=\"countColumn\">2</td></tr>\n"
                            + "<tr>\n"
                            + "</table>\n"
                            + "<br/>            </div>\n"
                            + "            </div>\n"
                            + "          </section>    </div>\n"
                            + "  </main>\n"
                            + "</div>\n"
                            + "</body>\n"
                            + "</html>",
                    report);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        }
    }

    @Override
    protected Detector getDetector() {
        fail("Not used in this test");
        return null;
    }
}
