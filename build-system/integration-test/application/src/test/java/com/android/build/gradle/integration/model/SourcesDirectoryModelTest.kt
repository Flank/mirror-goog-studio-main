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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

class SourcesDirectoryModelTest : ModelComparator() {
    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                minSdk = 14
                setUpHelloWorld()
            }
        }
    }

    private val assetCreatorTask = """

             abstract class AssetCreatorTask extends DefaultTask {
                @OutputFiles
                abstract DirectoryProperty getOutputDirectory()
                @TaskAction
                void taskAction() { /* pretend we write content here */ }
            }
    """.trimIndent()

    private val javaCreatorTask = """

             abstract class JavaCreatorTask extends DefaultTask {
                @OutputFiles
                abstract DirectoryProperty getOutputDirectory()
                @TaskAction
                void taskAction() { /* pretend we write content here */ }
            }
    """.trimIndent()

    @Test
    fun `test adding generated source directory to IDE model with addGeneratedSourceDirectory and addStaticSourceDirectory`() {
        val buildFile = project.buildFile.readText()
        with(buildFile) {
            project.buildFile.writeText(this)
            project.buildFile.appendText(assetCreatorTask)
            project.buildFile.appendText(javaCreatorTask)

            project.buildFile.appendText("""

                androidComponents {
                    onVariants(selector().all()) { variant ->
                    // use addGeneratedSourceDirectory for adding generated resources
                    TaskProvider<AssetCreatorTask> assetCreationTask =
                        project.tasks.register('create' + variant.getName() + 'Asset', AssetCreatorTask.class){
                            getOutputDirectory().set(new File(project.layout.buildDirectory.asFile.get(), "assets"))
                        }

                    variant.sources.assets?.addGeneratedSourceDirectory(
                            assetCreationTask,
                            { it.getOutputDirectory() })

                    // use addStaticSourceDirectory to add static directory
                    String staticPath = 'src/' + variant.getName() + '/static'
                    new File(project.projectDir, staticPath).mkdirs()

                    variant.sources.java?.addStaticSourceDirectory(staticPath)

                    // add java generator task
                    TaskProvider<JavaCreatorTask> javaCreationTask =
                        project.tasks.register('create' + variant.getName() + 'JavaGenerator', JavaCreatorTask.class){
                            getOutputDirectory().set(new File(project.layout.buildDirectory.asFile.get(), "java_stubs"))
                        }

                    variant.sources.java?.addGeneratedSourceDirectory(
                            javaCreationTask,
                            { it.getOutputDirectory() })
                }
            }
            """.trimIndent())
        }

        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels()

        with(result).compareBasicAndroidProject(goldenFile = "SourceDirectories")
    }

    @Test
    fun `test adding generated source directory to IDE model with registerJavaGeneratingTask old API`() {
        val buildFile = project.buildFile.readText()
        with(buildFile) {
            project.buildFile.writeText(this)
            project.buildFile.appendText(javaCreatorTask)

            project.buildFile.appendText("""
               // use old API here
               android.applicationVariants.all { variant ->

                    // add java generator task
                    File outDir = new File(project.layout.buildDirectory.asFile.get(), "java_stubs")
                    TaskProvider<JavaCreatorTask> javaCreationTask =
                        project.tasks.register('create' + variant.getName() + 'JavaGenerator', JavaCreatorTask.class){
                            getOutputDirectory().set(outDir)
                        }

                    variant.registerJavaGeneratingTask(javaCreationTask, outDir)
                }
            """.trimIndent())
        }

        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels()

        with(result).compareBasicAndroidProject(goldenFile = "SourceDirectories2")

    }
}
