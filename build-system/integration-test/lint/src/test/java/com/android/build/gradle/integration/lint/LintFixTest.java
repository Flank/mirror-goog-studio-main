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
import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test for the lintFix target on the synthetic accessor warnings found in the Kotlin
 * project.
 */
@RunWith(Parameterized.class)
public class LintFixTest {

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule
    public final GradleTestProject project;

    public LintFixTest(LintInvocationType lintInvocationType) {
        this.project =
                lintInvocationType.testProjectBuilder(66).fromTestProject("lintKotlin").create();
    }

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
}
