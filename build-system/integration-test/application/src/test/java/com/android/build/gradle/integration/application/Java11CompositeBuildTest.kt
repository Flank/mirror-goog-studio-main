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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for http://b/183952598, composite builds and Java 9+ language features.
 */
class Java11CompositeBuildTest {

    @get:Rule
    val project =
            GradleTestProject.builder()
                    .fromTestProject("simpleCompositeBuild")
                    .withDependencyChecker(false) // composites are not yet supported
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .create()

    @Test
    fun testTaskGraphComputedSuccessfully() {
        assumeTrue(TestUtils.runningWithJdk11Plus(System.getProperty("java.version")))

        project.getSubproject("app").buildFile.appendText("""

            android.compileOptions.sourceCompatibility 11
            android.compileOptions.targetCompatibility 11
        """.trimIndent())

        project.executor()
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .withArgument("--dry-run")
                .run("build")
    }
}
