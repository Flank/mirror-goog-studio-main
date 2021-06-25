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

import static com.android.build.gradle.integration.common.truth.ScannerSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test for the lintFix target on the synthetic accessor warnings found in the Kotlin
 * project.
 */
@RunWith(FilterableParameterized.class)
public class LintFixTest {

    @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter public boolean usePartialAnalysis;

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintKotlin")
                    .withConfigurationCacheMaxProblems(66)
                    .create();

    @Test
    public void checkFindNestedResult() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run(":app:lintFix");
        assertThat(result.getStderr())
                .contains(
                        "Aborting build since sources were modified to apply quickfixes after compilation");

        // Make sure quickfixes worked too
        File source = project.file("app/src/main/kotlin/test/pkg/AccessTest2.kt");
        // The original source has this:
        //    private fun method1() { ... }
        //    ...
        //    private constructor()
        //    ...
        // After applying quickfixes, it contains this:
        //    internal fun method1() { ... }
        //    ...
        //    internal constructor()
        //    ...
        assertThat(source).contains("internal fun method1()");
        assertThat(source).contains("internal constructor()");
        GradleBuildResult result2 = project.executor().expectFailure().run("clean", ":app:lintFix");
        assertThat(result2.getStderr()).contains("Lint found errors in the project; aborting build");
    }

    @Test
    public void checkFixesAppliedInDependentModule() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "\nandroid.lintOptions.checkDependencies=true\n");
        TestFileUtils.appendToFile(
                project.getSubproject(":library").getBuildFile(),
                "\nandroid.lintOptions { error 'SyntheticAccessor' }\n");

        File sourceDir = project.file("app/src/main/kotlin/test/pkg");
        File sourceDirCopy = project.file("library/src/main/kotlin/test/pkg");

        FileUtils.copyDirectory(sourceDir, sourceDirCopy);

        GradleBuildResult result = getExecutor().expectFailure().run(":app:lintFix");
        assertThat(result.getStderr())
                .contains(
                        "Aborting build since sources were modified to apply quickfixes after compilation");

        // Make sure quickfixes worked too
        File source = FileUtils.join(sourceDirCopy, "AccessTest2.kt");
        // The original source has this:
        //    private fun method1() { ... }
        //    ...
        //    private constructor()
        //    ...
        // After applying quickfixes, it contains this:
        //    internal fun method1() { ... }
        //    ...
        //    internal constructor()
        //    ...
        assertThat(source).contains("internal fun method1()");
        assertThat(source).contains("internal constructor()");
        GradleBuildResult result2 = project.executor().expectFailure().run("clean", ":app:lintFix");
        assertThat(result2.getStderr())
                .contains("Lint found errors in the project; aborting build");
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }
}

