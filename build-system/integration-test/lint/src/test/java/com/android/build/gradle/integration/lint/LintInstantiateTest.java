/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration test for lint analyzing Kotlin code from Gradle. */
@RunWith(Parameterized.class)
public class LintInstantiateTest {

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return new LintInvocationType[] {
            LintInvocationType.REFLECTIVE_LINT_RUNNER
        }; // TODO(b/160392650)
    }

    @Rule
    public final GradleTestProject project;

    public LintInstantiateTest(LintInvocationType lintInvocationType) {
        this.project =
                lintInvocationType
                        .testProjectBuilder(87)
                        .fromTestProject("lintInstantiate")
                        .create();
    }

    @Test
    public void checkFindErrors() {
        project.execute("clean", ":app:lintDebug");
        File lintReport = project.file("app/lint-results.txt");
        assertThat(lintReport).contains("No issues found.");

        File sarifFile = new File(project.getSubproject("app").getBuildDir(), "reports/lint-results.sarif");
        assertThat(sarifFile).exists();
        assertThat(sarifFile).contains("\"$schema\" : \"https://raw.githubusercontent.com/oasis-tcs/sarif-spec/");
    }
}
