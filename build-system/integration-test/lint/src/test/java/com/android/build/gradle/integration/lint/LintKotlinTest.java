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

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for lint analyzing Kotlin code from Gradle. */
public class LintKotlinTest {

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintKotlin")
                    .withConfigurationCacheMaxProblems(4)
                    .create();

    @Test
    public void checkFindErrors() throws Exception {
        project.executor().expectFailure().run(":app:lintDebug");
        GradleBuildResult result = project.executor().expectFailure().run(":app:lintDebug");


        Throwable exception = result.getException();
        while (exception.getCause() != null && exception.getCause() != exception) {
            exception = exception.getCause();
        }
        assertThat(exception.getMessage())
                .contains("Lint found errors in the project; aborting build");

        File lintReport = project.file("app/lint-report.xml");
        assertThat(lintReport)
                .contains(
                        "errorLine1=\"    public SampleFragment(String foo) { // Deliberate lint error\"");
        assertThat(lintReport).contains("id=\"ValidFragment\"");
        assertThat(lintReport).doesNotContain("id=\"CallSuper\"");

        File lintResults = project.file("app/build/reports/lint-results.txt");
        assertThat(lintResults).contains("8 errors, 6 warnings");
    }
}
