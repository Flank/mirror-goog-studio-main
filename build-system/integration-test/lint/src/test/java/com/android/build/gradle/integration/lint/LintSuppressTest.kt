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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Regression test for 186806269: lint.xml file is ignored in AGP
 * 4.2.0-rc01
 *
 * The bug is that with checkAllWarnings in lintOptions, attempts to turn
 * off specific issues via lint.xml did not work.
 *
 * This test checks both suppressing via a default lint.xml (due to
 * location and name) as well as via a lintOption lintConfig pointer; we
 * use UnusedIds for the first one and MissingClass for the second.
 */
class LintSuppressTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("lintSuppress").create()

    @Test
    fun checkSuppressed() {
        // Run twice to catch issues with configuration caching
        project.execute("clean", ":app:lintDebug")
        project.execute("clean", ":app:lintDebug")
        val app = project.getSubproject("app")
        val file = File(app.projectDir, "lint-report.xml")
        assertThat(file).exists()

        assertThat(file).contains("<issues")
        assertThat(file).doesNotContain("UnusedIds")
        assertThat(file).doesNotContain("MissingClass")
    }
}
