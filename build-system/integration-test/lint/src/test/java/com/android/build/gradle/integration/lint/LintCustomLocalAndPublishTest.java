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
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
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

    @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter public boolean usePartialAnalysis;

    @Rule public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintCustomLocalAndPublishRules")
                    .withConfigurationCacheMaxProblems(2)
                    .create();

    @Test
    public void checkCustomLint() throws Exception {
        getExecutor().withFailOnWarning(false).run("clean");
        getExecutor().withFailOnWarning(false).run(":library-remote:publish");
        // Run twice to catch issues with configuration caching
        getExecutor().withFailOnWarning(false).expectFailure().run(":library:lintDebug");
        getExecutor().withFailOnWarning(false).expectFailure().run(":library:lintDebug");

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
        getExecutor().withFailOnWarning(false).expectFailure().run(":app:lintDebug");
        getExecutor().withFailOnWarning(false).expectFailure().run(":app:lintDebug");

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
        getExecutor().withFailOnWarning(false).run("clean");
        getExecutor().withFailOnWarning(false).run(":library:assembleDebug");
        getExecutor().withFailOnWarning(false).run(":library-publish-only:assembleDebug");
        getExecutor().withFailOnWarning(false).run(":library-local-only:assembleDebug");

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

    /** Check custom rules are included in the model */
    @Test
    public void checkModel() throws Exception {
        ModelContainer<AndroidProject> androidProjects =
                getModel().withFailOnWarning(false).ignoreSyncIssues().fetchAndroidProjects();

        assertThat(androidProjects.getOnlyModelMap().get(":library").getLintRuleJars()).hasSize(1);

        List<SyncIssue> syncIssues =
                androidProjects.getOnlyModelSyncIssues().stream()
                        .filter(it -> it.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());
        assertThat(syncIssues).hasSize(1);
        assertThat(syncIssues.iterator().next().getMessage())
                .isEqualTo("Unable to resolve dependency com.example.google:library-remote:1.0");
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }

    private ModelBuilder getModel() {
        return project.model().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }
}
