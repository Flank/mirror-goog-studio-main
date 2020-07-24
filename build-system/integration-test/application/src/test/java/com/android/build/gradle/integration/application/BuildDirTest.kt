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

package com.android.build.gradle.integration.application

import com.android.testutils.truth.PathSubject.assertThat
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checks that we obey project.buildDir in the DSL.
 *
 *
 * This means we don't read it too early, before the user had a chance to change it.
 */
class BuildDirTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Test
    fun buildDirIsObeyed() {
        val buildDir = changeBuildDir()

        project.execute("assembleDebug")

        assertThat(buildDir).isDirectory()
        // $project/build dir is used by default in gradle 6.6 to store configuration caching
        // report, so check for the intermediates instead.
        assertThat(project.file("build/intermediates")).doesNotExist()
    }

    private fun changeBuildDir(): File {
        val buildDir = temporaryFolder.newFolder()
        FileUtils.deletePath(buildDir)
        assertThat(buildDir).doesNotExist()

        val newBuildDir = buildDir.absolutePath.replace(File.separatorChar, '/')

        TestFileUtils.appendToFile(
            project.buildFile,
            "project.buildDir = '$newBuildDir'"
        )
        return buildDir
    }
}
