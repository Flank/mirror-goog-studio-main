/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test for running lint from gradle on a model with java (including indirect java)
 * dependencies, as well as dependencies that are not the hardcoded string "compile".
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintDependencyModelTest
 * </pre>
 */
@RunWith(FilterableParameterized.class)
public class LintDependencyModelTest {

    @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter public boolean usePartialAnalysis;

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintDeps").create();

    @Test
    public void checkFindNestedResult() throws Exception {
        // Run twice to catch issues with configuration caching
        getExecutor().run(":app:cleanLintDebug", ":app:lintDebug");
        getExecutor().run(":app:cleanLintDebug", ":app:lintDebug");

        String textReport = readTextReportToString();

        // Library dependency graph:
        //        ----------app----------
        //       /            \          \            depends on
        //   javalib2      androidlib    javalib
        //          \     /         \                 which depend on
        //        indirectlib      indirectlib2

        // All five libraries will trigger the SdCardPath detector.
        // The lint.xml in androidLib marks it as information, which applies to indirectLib2.
        // However, indirectlib is reachable via two projects (javalib2 and androidlib) so it's
        // ambiguous which should apply. (TODO: b/160392650: decide on a deterministic semantic)

        // We've configured SdCardPath to be an error, so we expect to see it
        // in androidlib and indirectlib2 as error; in indirectlib1 there's more
        // ambiguity since it's imported from two contexts and either is fine.

        assertThat(textReport).contains("androidlib/src/main/java/com/example/mylibrary/MyClass.java:4: Information: Do not hardcode");
        assertThat(textReport).contains("javalib/src/main/java/com/example/MyClass.java:4: Warning: Do not hardcode");
        assertThat(textReport).contains("javalib2/src/main/java/com/example2/MyClass.java:4: Warning: Do not hardcode");
        if (usePartialAnalysis) {
            // TODO(b/182859396): These should be informational, as explained in comments above
            assertThat(textReport)
                    .contains(
                            "indirectlib/src/main/java/com/example/MyClass2.java:4: Warning: Do not hardcode");
            assertThat(textReport)
                    .contains(
                            "indirectlib2/src/main/java/com/example2/MyClass2.java:4: Warning: Do not hardcode");
        } else {
            assertThat(textReport)
                    .contains(
                            "indirectlib/src/main/java/com/example/MyClass2.java:4: Information: Do not hardcode");
            assertThat(textReport)
                    .contains(
                            "indirectlib2/src/main/java/com/example2/MyClass2.java:4: Information: Do not hardcode");
        }
        // This issue is turned off in javalib but still returns to (default) enabled when processing
        // its sibling
        assertThat(textReport).contains("javalib2/src/main/java/com/example2/MyClass.java:5: Warning: Use Boolean.valueOf(false)");
        assertThat(textReport).doesNotContain("javalib/src/main/java/com/example/MyClass.java:5: Warning: Use Boolean.valueOf(false)");
    }

    @Test
    public void checkLintUpToDate() throws Exception {
        if (usePartialAnalysis) {
            ImmutableList<String> tasks =
                    ImmutableList.of(
                            ":app:lintDebug",
                            ":app:lintAnalyzeDebug",
                            ":androidlib:lintAnalyzeDebug",
                            ":javalib:lintAnalyze",
                            ":javalib2:lintAnalyze",
                            ":indirectlib:lintAnalyze",
                            ":indirectlib2:lintAnalyze");

            GradleBuildResult firstResult = getExecutor().run(":app:lintDebug");
            tasks.forEach(taskName -> assertThat(firstResult.findTask(taskName)).didWork());
            String textReport = readTextReportToString();
            // TODO(b/182859396): There should be 5 warnings; see TODO in checkFindNestedResult().
            assertThat(textReport).contains("0 errors, 7 warnings");

            GradleBuildResult secondResult = getExecutor().run(":app:lintDebug");
            tasks.forEach(taskName -> assertThat(secondResult.findTask(taskName)).wasUpToDate());

        } else {
            GradleBuildResult firstResult = getExecutor().run(":app:lintDebug");
            assertThat(firstResult.findTask(":app:lintDebug")).didWork();
            String textReport = readTextReportToString();
            assertThat(textReport).contains("0 errors, 5 warnings");

            // The lint task should not be up-to-date if not using partial analysis with
            // checkDependencies because the inputs are not modeled correctly in that case.
            GradleBuildResult secondResult = getExecutor().run(":app:lintDebug");
            assertThat(secondResult.findTask(":app:lintDebug")).didWork();
        }
    }

    // Regression test for b/187964502
    @Test
    public void testMultipleJavaModuleDependencies() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").getBuildFile(),
                "checkDependencies true",
                "checkDependencies false");

        getExecutor().run(":app:lintDebug");

        File lintModelDir =
                project.getSubproject("app")
                        .getIntermediatesDir()
                        .toPath()
                        .resolve("incremental/lintDebug")
                        .toFile();
        assertThat(lintModelDir).isDirectory();

        File librariesModelFile = new File(lintModelDir, "debug-mainArtifact-libraries.xml");
        assertThat(librariesModelFile).isFile();
        List<String> expectedJarNames =
                Arrays.asList("javalib.jar", "javalib2.jar", "indirectlib.jar", "indirectlib2.jar");
        for (String jarName : expectedJarNames) {
            assertThat(String.join("\n", Files.readAllLines(librariesModelFile.toPath())))
                    .contains(jarName);
        }
    }

    @NotNull
    private String readTextReportToString() {
        File textReportFile = project.file("app/lint-report.txt");
        String textReport =
                FilesKt.readText(textReportFile, Charsets.UTF_8)
                        // Allow searching for substrings in Windows file reports as well
                        .replace("\\", "/");
        return textReport;
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }
}
