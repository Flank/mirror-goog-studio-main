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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.testutils.truth.PathSubject.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getDebugVariantBuildOutput
import com.android.build.gradle.integration.common.utils.getOutputFiles
import com.android.build.gradle.integration.common.utils.getSingleOutputFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

private const val OLD_NAME = "random_name"
private const val NEW_NAME = "changed_name"

/**
 * Ensures that archivesBaseName setting on android project is used when choosing the apk file names
 */
@RunWith(FilterableParameterized::class)
class ArchivesBaseNameTest(plugin: String, private val extension: String) {

    @get:Rule
    var project: GradleTestProject =
        GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin(plugin)).create()

    @Test
    fun testArtifactName() {
        checkApkName("project", extension)

        TestFileUtils.appendToFile(
            project.buildFile, "\narchivesBaseName = \'$OLD_NAME\'"
        )
        checkApkName(OLD_NAME, extension)

        TestFileUtils.searchAndReplace(project.buildFile, OLD_NAME, NEW_NAME)
        checkApkName(NEW_NAME, extension)
    }

    private fun checkApkName(name: String, extension: String) {
        val projectBuildOutput = project.executeAndReturnModel("clean","assembleDebug").onlyModel
        val debugBuildOutput = projectBuildOutput.getDebugVariantBuildOutput()

        // Get the apk file
        val outputFile = if (debugBuildOutput.assembleTaskOutputListingFile != null) {
            File(debugBuildOutput.getSingleOutputFile())
        } else {
            File(project.buildDir, "outputs/aar").listFiles()?.first()
        }

        assertThat(outputFile?.name).isEqualTo("$name-debug.$extension")
        assertThat(outputFile).isFile()
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Iterable<Array<Any>> {
            return listOf(
                arrayOf<Any>("com.android.application", "apk"),
                arrayOf<Any>("com.android.library", "aar")
            )
        }
    }
}
