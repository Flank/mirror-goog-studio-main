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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import java.io.File;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
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

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return new LintInvocationType[] {
            LintInvocationType.REFLECTIVE_LINT_RUNNER
        }; // TODO(b/160392650)
    }

    @Rule
    public final GradleTestProject project;

    public LintDependencyModelTest(LintInvocationType lintInvocationType) {
        this.project = lintInvocationType.testProjectBuilder()
                .fromTestProject("lintDeps")
                .create();
    }


    @Test
    public void checkFindNestedResult() {
        // Run twice to catch issues with configuration caching
        project.execute("clean", ":app:lintDebug");
        project.execute("clean", ":app:lintDebug");

        File textReportFile = project.file("app/lint-report.txt");
        String textReport =
                FilesKt.readText(textReportFile, Charsets.UTF_8)
                        // Allow searching for substrings in Windows file reports as well
                        .replace("\\", "/");

        // Library dependency graph:
        //    app +----> androidlib +----> indirectlib2
        //        |                 |
        //        +----> javalib ---+----> indirectlib
        //        +----> javalib2

        // Should have SdCatdPath errors in all five libs.
        // The javalib/lint.xml file which turns SdCardPath into an error should
        // apply in javalib and its dependency (indirectlib2), but indirectlib
        // is directly depended on by multiple upstream projects so it's ambiguous
        // which should apply.

        // We've configured SdCardPath to be an error, so we expect to see it
        // in androidlib and indirectlib2 as error; in indirectlib1 there's more
        // ambiguity since it's imported from two contexts and either is fine.

        assertThat(textReport).contains("androidlib/src/main/java/com/example/mylibrary/MyClass.java:4: Information: Do not hardcode");
        assertThat(textReport).contains("javalib/src/main/java/com/example/MyClass.java:4: Warning: Do not hardcode");
        assertThat(textReport).contains("javalib2/src/main/java/com/example2/MyClass.java:4: Warning: Do not hardcode");
        assertThat(textReport).contains("indirectlib/src/main/java/com/example/MyClass2.java:4: Information: Do not hardcode");
        assertThat(textReport).contains("indirectlib2/src/main/java/com/example2/MyClass2.java:4: Information: Do not hardcode");

        // This issue is turned off in javalib but still returns to (default) enabled when processing
        // its sibling
        assertThat(textReport).contains("javalib2/src/main/java/com/example2/MyClass.java:5: Warning: Use Boolean.valueOf(false)");
        assertThat(textReport).doesNotContain("javalib/src/main/java/com/example/MyClass.java:5: Warning: Use Boolean.valueOf(false)");
    }
}
