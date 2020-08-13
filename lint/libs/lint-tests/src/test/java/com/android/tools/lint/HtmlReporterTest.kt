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

package com.android.tools.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.image
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import org.junit.Assert.assertTrue

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.LogDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestResultTransformer
import java.io.File
import org.junit.Test

class HtmlReporterTest {
    @Test
    fun testBasic() {
        val client = TestLintClient()
        client.flags.enabledIds.add(LogDetector.CONDITIONAL.id)
        val transformer = TestResultTransformer { output ->
            var report: String
            // Replace the timestamp to make golden file comparison work
            val timestampPrefix = "Check performed at "
            var begin = output.indexOf(timestampPrefix)
            assertTrue(begin != -1)
            begin += timestampPrefix.length
            val end = output.indexOf("</nav>", begin)
            assertTrue(end != -1)
            report = output.substring(0, begin) + "\$DATE" + output.substring(end)

            report = report.replace(File.separatorChar, '/')

            // There's some (single) trailing space in the output, but
            // the IDE strips it out of the expected output's raw string literal:
            report = report.replace(" \n", "\n")

            report
        }

        TestLintTask.lint().sdkHome(TestUtils.getSdk()).files(
            manifest(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="10" />
                </manifest>
                """
            ),
            xml(
                "res/layout/main.xml",
                """
                <Button xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/button1"
                    android:text="Fooo" />
                """
            ),
            xml(
                "res/layout/main2.xml",
                """
                <Button xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/button1"
                    android:text="Bar" />
                """
            ),
            image("res/drawable-hdpi/icon1.png", 48, 48).fill(-0xff00d7),
            image("res/drawable-hdpi/icon2.png", 49, 49).fill(-0xff00d7),
            image("res/drawable-hdpi/icon3.png", 49, 49).fill(-0xff00d7),
            image("res/drawable-hdpi/icon4.png", 49, 49).fill(-0xff00d7)
        )
            .issues(
                ManifestDetector.USES_SDK,
                HardcodedValuesDetector.ISSUE,
                IconDetector.DUPLICATES_NAMES,
                // Not reported, but for the disabled-list
                ManifestDetector.MOCK_LOCATION,
                // Not reported, but disabled by default and enabled via flags (b/111035260)
                LogDetector.CONDITIONAL
            )
            .client(client)
            .run()

            // NOTE: If you change the output, please validate it manually in
            //  http://validator.w3.org/#validate_by_input
            // before updating the following

            .expectHtml(
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
      <span class="mdl-layout-title">Lint Report: 4 warnings</span>
      <div class="mdl-layout-spacer"></div>
      <nav class="mdl-navigation mdl-layout--large-screen-only">
Check performed at ${"$"}DATE</nav>
    </div>
  </header>
  <div class="mdl-layout__drawer">
    <span class="mdl-layout-title">Issue Types</span>
    <nav class="mdl-navigation">
      <a class="mdl-navigation__link" href="#overview"><i class="material-icons">dashboard</i>Overview</a>
      <a class="mdl-navigation__link" href="#UsesMinSdkAttributes"><i class="material-icons warning-icon">warning</i>Minimum SDK and target SDK attributes not defined (1)</a>
      <a class="mdl-navigation__link" href="#IconDuplicates"><i class="material-icons warning-icon">warning</i>Duplicated icons under different names (1)</a>
      <a class="mdl-navigation__link" href="#HardcodedText"><i class="material-icons warning-icon">warning</i>Hardcoded text (2)</a>
    </nav>
  </div>
  <main class="mdl-layout__content">
    <div class="mdl-layout__tab-panel is-active">
<a name="overview"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="OverviewCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Overview</h2>
  </div>
              <div class="mdl-card__supporting-text">
<table class="overview">
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Correctness">Correctness</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#UsesMinSdkAttributes">UsesMinSdkAttributes</a>: Minimum SDK and target SDK attributes not defined</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Usability:Icons">Usability:Icons</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#IconDuplicates">IconDuplicates</a>: Duplicated icons under different names</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Internationalization">Internationalization</a>
</td></tr>
<tr>
<td class="countColumn">2</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#HardcodedText">HardcodedText</a>: Hardcoded text</td></tr>
</table>
<br/>              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="OverviewCardLink" onclick="hideid('OverviewCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Correctness"></a>
<a name="UsesMinSdkAttributes"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="UsesMinSdkAttributesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Minimum SDK and target SDK attributes not defined</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="project0/AndroidManifest.xml">AndroidManifest.xml</a>:4</span>: <span class="message"><code>&lt;uses-sdk></code> tag should specify a target API level (the highest verified version; when running on later versions, compatibility behaviors may be enabled) with <code>android:targetSdkVersion="?"</code></span><br /><pre class="errorlines">
<span class="lineno"> 1 </span>
<span class="lineno"> 2 </span>              <span class="tag">&lt;manifest</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 3 </span>                  <span class="attribute">package</span>=<span class="value">"test.pkg"</span>>
<span class="caretline"><span class="lineno"> 4 </span>                  <span class="tag">&lt;</span><span class="warning"><span class="tag">uses-sdk</span></span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 5 </span>              <span class="tag">&lt;/manifest></span>
<span class="lineno"> 6 </span>              </pre>

</div>
<div class="metadata"><div class="explanation" id="explanationUsesMinSdkAttributes" style="display: none;">
The manifest should contain a <code>&lt;uses-sdk></code> element which defines the minimum API Level required for the application to run, as well as the target version (the highest API level you have tested the version for).<br/><div class="moreinfo">More info: <a href="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html">https://developer.android.com/guide/topics/manifest/uses-sdk-element.html</a>
</div>To suppress this error, use the issue id "UsesMinSdkAttributes" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">UsesMinSdkAttributes</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Correctness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 9/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationUsesMinSdkAttributesLink" onclick="reveal('explanationUsesMinSdkAttributes');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="UsesMinSdkAttributesCardLink" onclick="hideid('UsesMinSdkAttributesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Usability:Icons"></a>
<a name="IconDuplicates"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="IconDuplicatesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Duplicated icons under different names</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="project0/res/drawable-hdpi/icon4.png">res/drawable-hdpi/icon4.png</a></span>: <span class="message">The following unrelated icon files have identical contents: icon2.png, icon3.png, icon4.png</span><br />
<ul><span class="location"><a href="project0/res/drawable-hdpi/icon3.png">res/drawable-hdpi/icon3.png</a></span>: <span class="message">&lt;No location-specific message</span><br /><span class="location"><a href="project0/res/drawable-hdpi/icon2.png">res/drawable-hdpi/icon2.png</a></span>: <span class="message">&lt;No location-specific message</span><br /></ul><table>
<tr><td><a href="project0/res/drawable-hdpi/icon2.png"><img border="0" align="top" src="project0/res/drawable-hdpi/icon2.png" /></a>
</td><td><a href="project0/res/drawable-hdpi/icon3.png"><img border="0" align="top" src="project0/res/drawable-hdpi/icon3.png" /></a>
</td><td><a href="project0/res/drawable-hdpi/icon4.png"><img border="0" align="top" src="project0/res/drawable-hdpi/icon4.png" /></a>
</td></tr><tr><th>hdpi</th><th>hdpi</th><th>hdpi</th></tr>
</table>
</div>
<div class="metadata"><div class="explanation" id="explanationIconDuplicates" style="display: none;">
If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.<br/>To suppress this error, use the issue id "IconDuplicates" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">IconDuplicates</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Icons</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Usability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 3/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationIconDuplicatesLink" onclick="reveal('explanationIconDuplicates');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="IconDuplicatesCardLink" onclick="hideid('IconDuplicatesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Internationalization"></a>
<a name="HardcodedText"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="HardcodedTextCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Hardcoded text</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="project0/res/layout/main.xml">res/layout/main.xml</a>:4</span>: <span class="message">Hardcoded string "Fooo", should use <code>@string</code> resource</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span>
<span class="lineno"> 2 </span>                <span class="tag">&lt;Button</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 3 </span>                    <span class="prefix">android:</span><span class="attribute">id</span>=<span class="value">"@+id/button1"</span>
<span class="caretline"><span class="lineno"> 4 </span>                    <span class="warning"><span class="prefix">android:</span><span class="attribute">text</span>=<span class="value">"Fooo"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 5 </span>                </pre>

<span class="location"><a href="project0/res/layout/main2.xml">res/layout/main2.xml</a>:4</span>: <span class="message">Hardcoded string "Bar", should use <code>@string</code> resource</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span>
<span class="lineno"> 2 </span>                <span class="tag">&lt;Button</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 3 </span>                    <span class="prefix">android:</span><span class="attribute">id</span>=<span class="value">"@+id/button1"</span>
<span class="caretline"><span class="lineno"> 4 </span>                    <span class="warning"><span class="prefix">android:</span><span class="attribute">text</span>=<span class="value">"Bar"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 5 </span>                </pre>

</div>
<div class="metadata"><div class="explanation" id="explanationHardcodedText" style="display: none;">
Hardcoding text attributes directly in layout files is bad for several reasons:<br/>
<br/>
* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>
<br/>
* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>
<br/>
There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/>To suppress this error, use the issue id "HardcodedText" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">HardcodedText</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Internationalization</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 5/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationHardcodedTextLink" onclick="reveal('explanationHardcodedText');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="HardcodedTextCardLink" onclick="hideid('HardcodedTextCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="MissingIssues"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="MissingIssuesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Disabled Checks</h2>
  </div>
              <div class="mdl-card__supporting-text">
One or more issues were not run by lint, either
because the check is not enabled by default, or because
it was disabled with a command line flag or via one or
more <code>lint.xml</code> configuration files in the project directories.
<div id="SuppressedIssues" style="display: none;"><br/><br/></div>              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="SuppressedIssuesLink" onclick="reveal('SuppressedIssues');">
List Missing Issues</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="MissingIssuesCardLink" onclick="hideid('MissingIssuesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="SuppressInfo"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="SuppressCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Suppressing Warnings and Errors</h2>
  </div>
              <div class="mdl-card__supporting-text">
Lint errors can be suppressed in a variety of ways:<br/>
<br/>
1. With a <code>@SuppressLint</code> annotation in the Java code<br/>
2. With a <code>tools:ignore</code> attribute in the XML file<br/>
3. With a //noinspection comment in the source code<br/>
4. With ignore flags specified in the <code>build.gradle</code> file, as explained below<br/>
5. With a <code>lint.xml</code> configuration file in the project<br/>
6. With a <code>lint.xml</code> configuration file passed to lint via the --config flag<br/>
7. With the --ignore flag passed to lint.<br/>
<br/>
To suppress a lint warning with an annotation, add a <code>@SuppressLint("id")</code> annotation on the class, method or variable declaration closest to the warning instance you want to disable. The id can be one or more issue id's, such as <code>"UnusedResources"</code> or <code>{"UnusedResources","UnusedIds"}</code>, or it can be <code>"all"</code> to suppress all lint warnings in the given scope.<br/>
<br/>
To suppress a lint warning with a comment, add a <code>//noinspection id</code> comment on the line before the statement with the error.<br/>
<br/>
To suppress a lint warning in an XML file, add a <code>tools:ignore="id"</code> attribute on the element containing the error, or one of its surrounding elements. You also need to define the namespace for the tools prefix on the root element in your document, next to the <code>xmlns:android</code> declaration:<br/>
<code>xmlns:tools="http://schemas.android.com/tools"</code><br/>
<br/>
To suppress a lint warning in a <code>build.gradle</code> file, add a section like this:<br/>

<pre>
android {
    lintOptions {
        disable 'TypographyFractions','TypographyQuotes'
    }
}
</pre>
<br/>
Here we specify a comma separated list of issue id's after the disable command. You can also use <code>warning</code> or <code>error</code> instead of <code>disable</code> to change the severity of issues.<br/>
<br/>
To suppress lint warnings with a configuration XML file, create a file named <code>lint.xml</code> and place it at the root directory of the module in which it applies.<br/>
<br/>
The format of the <code>lint.xml</code> file is something like the following:<br/>

<pre>
&lt;?xml version="1.0" encoding="UTF-8"?>
&lt;lint>
    &lt;!-- Ignore everything in the test source set -->
    &lt;issue id="all">
        &lt;ignore path="\*/test/\*" />
    &lt;/issue>

    &lt;!-- Disable this given check in this project -->
    &lt;issue id="IconMissingDensityFolder" severity="ignore" />

    &lt;!-- Ignore the ObsoleteLayoutParam issue in the given files -->
    &lt;issue id="ObsoleteLayoutParam">
        &lt;ignore path="res/layout/activation.xml" />
        &lt;ignore path="res/layout-xlarge/activation.xml" />
        &lt;ignore regexp="(foo|bar)\.java" />
    &lt;/issue>

    &lt;!-- Ignore the UselessLeaf issue in the given file -->
    &lt;issue id="UselessLeaf">
        &lt;ignore path="res/layout/main.xml" />
    &lt;/issue>

    &lt;!-- Change the severity of hardcoded strings to "error" -->
    &lt;issue id="HardcodedText" severity="error" />
&lt;/lint>
</pre>
<br/>
To suppress lint checks from the command line, pass the --ignore flag with a comma separated list of ids to be suppressed, such as:<br/>
<code>${'$'} lint --ignore UnusedResources,UselessLeaf /my/project/path</code><br/>
<br/>
For more information, see <a href="https://developer.android.com/studio/write/lint.html#config">https://developer.android.com/studio/write/lint.html#config</a><br/>

            </div>
            </div>
          </section>    </div>
  </main>
</div>
</body>
</html>""",
                transformer
            )
    }
}
