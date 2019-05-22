/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test making sure that the SupportAnnotationUsage does not report errors referencing R.type.name
 * resource fields. The real reason for this test is making sure that lint's hardcoded path to the
 * R.jar file is still correct; if the location is changed in Gradle this test is expected to fail.
 * (The longer term plan is for Gradle to pass the location to lint via a flag; this is tracked in
 * b/133326990.)
 */
public class LintResourceResolveTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("lintResourceResolve").create();

    @Test
    public void checkClean() throws Exception {
        project.execute("clean", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getTestDir(), "lint-report.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.");
    }
}
