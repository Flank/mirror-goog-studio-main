/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/**
 * Regression test for 196406778, where in release tasks, jars are missing classes causing hierarchy
 * checks to fail. Lint has a workaround for this; this test checks that workaround.
 */
public class LintMissingClassTest {

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintRelease").create();

    @Test
    public void checkLintRelease() throws IOException, InterruptedException {
        // Run twice to catch issues with configuration caching
        project.executor().run(":app:clean", ":app:lintRelease");
        project.executor().run(":app:clean", ":app:lintRelease");
        File file = project.getSubproject("app").file("build/reports/lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.");
    }
}
