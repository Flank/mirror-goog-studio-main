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


import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import java.io.File;
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
 *     $ ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=LintDependencyModelTest
 * </pre>
 */
@RunWith(FilterableParameterized.class)
public class LintDependencyModelTest {

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule
    public final GradleTestProject project;

    public LintDependencyModelTest(LintInvocationType lintInvocationType) {
        this.project = lintInvocationType.testProjectBuilder()
                .fromTestProject("lintDeps")
                .create();
    }


    @Test
    public void checkFindNestedResult() throws Exception {
        // Run twice to catch issues with configuration caching
        project.execute("clean", ":app:lintDebug");
        project.execute("clean", ":app:lintDebug");

        File lintReport = project.file("app/lint-report.xml");
        // Should have errors in all three libs
        assertThat(lintReport).contains("errorLine1=\"    String s = &quot;/sdcard/androidlib&quot;;\"");
        assertThat(lintReport).contains("errorLine1=\"    String s = &quot;/sdcard/javalib&quot;;\"");
        assertThat(lintReport).contains("errorLine1=\"    String s = &quot;/sdcard/indirectlib&quot;;\"");
    }
}
