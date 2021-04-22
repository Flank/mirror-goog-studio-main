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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MessageRewriteWithJvmResCompilerTest(private val useRelativeResPaths: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun useRelativeResPaths() = arrayOf(true, false)
    }

    @get:Rule
    var project: GradleTestProject = builder().fromTestProject("flavoredlib").create()

    @Test
    @Throws(Exception::class)
    fun testErrorInStringsForCompile() {
        // Incorrect strings.xml should cause the res compiler to throw an error and we should
        // rewrite it to point to the original file.
        val executor = gradleTaskExecutor()
        TemporaryProjectModification.doTest(project) { it: TemporaryProjectModification ->
            it.replaceInFile(
                "app/src/flavor1/res/values/strings.xml",
                "</resources>", "<id name=\"incorrect\">hello</id></resources>"
            )
            val result = executor.expectFailure().run("assembleDebug")
            result.stderr.use { stderr ->
                assertThat(stderr)
                    .contains(
                        FileUtils.join("app", "src", "flavor1", "res", "values", "strings.xml")
                    )
            }
        }
        // Fix it up and check that it compiles correctly.
        TemporaryProjectModification.doTest(project) { it: TemporaryProjectModification ->
            it.replaceInFile(
                "app/src/flavor1/res/values/strings.xml",
                "<id name=\"incorrect\">hello</id>",
                ""
            )
            gradleTaskExecutor().run("assembleDebug")
        }
    }

    private fun gradleTaskExecutor(): GradleTaskExecutor {
        return project.executor()
                .with(BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP, useRelativeResPaths)
    }
}
