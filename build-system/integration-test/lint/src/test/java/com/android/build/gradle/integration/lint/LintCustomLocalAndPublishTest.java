/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.lint;

import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for publishing a custom jar in a library model, used by a consuming app, as well as having a
 * local lint jar.
 */
@RunWith(FilterableParameterized.class)
public class LintCustomLocalAndPublishTest {

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule public final GradleTestProject project;

    public LintCustomLocalAndPublishTest(LintInvocationType lintInvocationType) {
        this.project =
                lintInvocationType
                        .testProjectBuilder(2)
                        .fromTestProject("lintCustomLocalAndPublishRules")
                        .create();
    }

    @Test
    public void checkCustomLint() throws Exception {
        project.executor().withFailOnWarning(false).run("clean");
        project.executor().withFailOnWarning(false).run(":library-remote:uploadArchives");
        // Run twice to catch issues with configuration caching
        project.executor().withFailOnWarning(false).expectFailure().run(":library:lintDebug");
        project.executor().withFailOnWarning(false).expectFailure().run(":library:lintDebug");

        String libexpected =
                        ""
                        + "build.gradle:16: Warning: Unknown issue id \"UnitTestLintCheck\". Did you mean 'UnitTestLintCheck2' (Custom Lint Check) ? [UnknownIssueId]\n"
                        + "        checkOnly 'UnitTestLintCheck'\n"
                        + "                   ~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnknownIssueId\":\n"
                        + "   Lint will report this issue if it is configured with an issue id it does\n"
                        + "   not recognize in for example Gradle files or lint.xml configuration files.\n"
                        + "\n"
                        + "src"
                        + File.separator
                        + "main"
                        + File.separator
                        + "java"
                        + File.separator
                        + "com"
                        + File.separator
                        + "example"
                        + File.separator
                        + "app"
                        + File.separator
                        + "MyClass.java:19: Error: Do not implement java.util.List directly [UnitTestLintCheck2 from com.example.google.lint]\n"
                        + "public abstract class MyClass implements java.util.List {}\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck2\":\n"
                        + "   This app should not have implement java.util.List.\n"
                        + "\n"
                        + "   Identifier: com.example.google.lint\n"
                        + "\n"
                        + "1 errors, 1 warnings";

        File liblintfile =
                new File(
                        project.getSubproject("library").getProjectDir(),
                        "library-lint-results.txt");
        assertThat(liblintfile).exists();
        assertThat(liblintfile).contentWithUnixLineSeparatorsIsExactly(libexpected);
        project.executor().withFailOnWarning(false).expectFailure().run(":app:lintDebug");
        project.executor().withFailOnWarning(false).expectFailure().run(":app:lintDebug");

        String appExpected =
                ""
                        + "build.gradle:15: Warning: Unknown issue id \"UnitTestLintCheck2\". Did you mean:\n"
                        + "'UnitTestLintCheck' (Custom Lint Check)\n"
                        + "'UnitTestLintCheck3' (Custom Lint Check)\n"
                        + "? [UnknownIssueId]\n"
                        + "        checkOnly 'UnitTestLintCheck2'\n"
                        + "                   ~~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnknownIssueId\":\n"
                        + "   Lint will report this issue if it is configured with an issue id it does\n"
                        + "   not recognize in for example Gradle files or lint.xml configuration files.\n"
                        + "\n"
                        + "src"
                        + File.separator
                        + "main"
                        + File.separator
                        + "AndroidManifest.xml:11: Error: Should not specify <activity>. [UnitTestLintCheck from com.example.google.lintpublish]\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "        ^\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck\":\n"
                        + "   This app should not have any activities.\n"
                        + "\n"
                        + "   Identifier: com.example.google.lintpublish\n"
                        + "\n"
                        + FileUtils.toSystemDependentPath("src/main/java/com/example/app/Util.java")
                        + ":5: Error: Do not implement java.util.Set directly [UnitTestLintCheck3 from com.example.remote.lint]\n"
                        + "public abstract class Util implements Set {}\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "   Explanation for issues of type \"UnitTestLintCheck3\":\n"
                        + "   This app should not implement java.util.Set.\n"
                        + "\n"
                        + "   Identifier: com.example.remote.lint\n"
                        + "\n"
                        + "2 errors, 1 warnings";
        File appLintFile =
                new File(project.getSubproject("app").getProjectDir(), "lint-results.txt");
        assertThat(appLintFile).exists();
        assertThat(appLintFile).contentWithUnixLineSeparatorsIsExactly(appExpected);
    }

    @Test
    public void checkAarHasLintJar() throws Exception {
        project.executor().withFailOnWarning(false).run("clean");
        project.executor().withFailOnWarning(false).run(":library:assembleDebug");
        project.executor().withFailOnWarning(false).run(":library-publish-only:assembleDebug");
        project.executor().withFailOnWarning(false).run(":library-local-only:assembleDebug");

        project.getSubproject("library")
                .testAar(
                        "debug",
                        it -> {
                            it.contains(FN_LINT_JAR);
                        });

        project.getSubproject("library-publish-only")
                .testAar(
                        "debug",
                        it -> {
                            it.contains(FN_LINT_JAR);
                        });

        project.getSubproject("library-local-only")
                .testAar(
                        "debug",
                        it -> {
                            it.doesNotContain(FN_LINT_JAR);
                        });
    }
}
