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

package com.android.build.gradle.integration.deployment

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MappingFileAccessTest {

    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("basic")
        .create()

    @Before
    fun setup() {
        project.buildFile.appendText("""
            class MappingFileUserTask extends DefaultTask {

                @org.gradle.api.tasks.InputFiles
                Provider<FileCollection> mappingFile;

                @javax.inject.Inject
                MappingFileUserTask(Provider<FileCollection> mappingFile) {
                  this.mappingFile = mappingFile

                  doLast {
                      def file = mappingFile.get().getSingleFile()
                      println "MappingFileTask " + file
                      println getName() + " task mapping file exists is " + file.exists()
                   }
                }
            }

            android {

                buildTypes {
                  release {
                      minifyEnabled = true
                  }
                }

                // create the mapping task and register it if necessary.
                applicationVariants.all { variant ->
                    if (variant.buildType.name == "release") {
                      def mappingFile = variant.getFinalArtifact(com.android.build.api.artifact.SingleArtifact.OBFUSCATION_MAPPING_FILE.INSTANCE)
                      println "Creating mapping task for " + variant.name
                      def mappingTask = tasks.create("hello" + variant.name.capitalize(), MappingFileUserTask, mappingFile)
                      variant.register(mappingTask)
                    } else {
                      println "Not creating mapping task for " + variant.name
                    }
                }
            }
        """.trimIndent())
    }

    @Test
    fun assembleTest() {

        // b/149978740
        val buildResult =
            project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                .run("clean", "assemble")
        assertThat(buildResult.tasks).contains(":helloRelease")
        assertThat(buildResult.tasks).doesNotContain(":helloDebug")

        buildResult.stdout.use {
            ScannerSubject.assertThat(it)
                .contains("helloRelease task mapping file exists is true")
        }
    }

    @Test
    fun bundleTest() {
        // b/149978740
        val buildResult =
            project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                .run("clean", "bundle")
        assertThat(buildResult.tasks).contains(":helloRelease")
        assertThat(buildResult.tasks).doesNotContain(":helloDebug")

        buildResult.stdout.use {
            ScannerSubject.assertThat(it)
                .contains("helloRelease task mapping file exists is true")
        }
    }

    @Test
    fun useMappingFileSpecificApi() {
        project.buildFile.appendText("""

            android.applicationVariants.all {
                if (it.buildType.isMinifyEnabled()) {
                    def mappingTask = tasks.create("mappingFile" + it.name.capitalize(), MappingFileUserTask, it.mappingFileProvider)
                }
            }
        """.trimIndent())
        val result = project.executor().run("mappingFileRelease")
        TruthHelper.assertThat(result.getTask(":mappingFileRelease")).didWork()
        TruthHelper.assertThat(result.getTask(":minifyReleaseWithR8")).didWork()
    }
}
