/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/** Test ability to use lint plugin without android settings or available sdk */
class LintStandaloneNoSdkTest {

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("lintStandalone").withSdk(false).create()

    @Test
    fun emptyJavaProjectRunLint() {
        val result = project.executor().run(":lint")
        Truth.assertThat(result.failedTasks).isEmpty()
        assertThat(result.getTask(":lint")).didWork();
        assertThat(result.getTask(":lintAnalyze")).didWork();

        val file = project.file("lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead");
        assertThat(file).contains("build.gradle:7: Warning: no Java language level directives");
        assertThat(file).contains("0 errors, 2 warnings");
    }
}
