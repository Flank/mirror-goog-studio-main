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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.ide.model.sync.Variant
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

class ModelSyncFilesTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin("com.android.application")).create()

    @Test
    fun testApplicationModel() {
        val variantSyncFileModel = getAppSyncModel()
        Truth.assertThat(variantSyncFileModel.variantCase)
            .isEqualTo(Variant.VariantCase.APPLICATIONVARIANTMODEL)
        Truth.assertThat(variantSyncFileModel.applicationVariantModel).isNotNull()
        Truth.assertThat(variantSyncFileModel.applicationVariantModel.applicationId)
            .isEqualTo("com.example.helloworld")
    }

    @Test
    fun testCustomizedApplicationIdInModel() {
        project.buildFile.appendText("""
        abstract class ApplicationIdProducerTask extends DefaultTask {

            @OutputFile
            abstract RegularFileProperty getOutputFile()

            @TaskAction
            void taskAction() {
                getOutputFile().get().getAsFile().write("set.from.task." + name)
            }
        }

        androidComponents {
            // b/176931684
            // disable androidTest as it forces the applicationId resolution
            beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
                variantBuilder.enableAndroidTest = false
            }
            onVariants(selector().withBuildType("debug")) { variant ->
                TaskProvider appIdProducer = tasks.register(variant.name + "AppIdProducerTask", ApplicationIdProducerTask.class) { task ->
                    File outputDir = new File(getBuildDir(), task.name)
                    outputDir.mkdirs()
                    task.getOutputFile().set(new File(outputDir, "appId.txt"))

                }
                variant.setApplicationId(appIdProducer.flatMap { task ->
                        task.getOutputFile().map { it.getAsFile().text }
                })
            }
        }
        """.trimIndent())
        val variantSyncFileModel = getAppSyncModel()
        Truth.assertThat(variantSyncFileModel.variantCase)
            .isEqualTo(Variant.VariantCase.APPLICATIONVARIANTMODEL)
        Truth.assertThat(variantSyncFileModel.applicationVariantModel).isNotNull()
        Truth.assertThat(variantSyncFileModel.applicationVariantModel.applicationId)
            .isEqualTo("set.from.task.debugAppIdProducerTask")
    }

    private fun getAppSyncModel(): Variant {
        val variant = getAppVariant()
        Truth.assertThat(variant.mainArtifact.modelSyncFiles.size).isEqualTo(1)
        val appModelSync = variant.mainArtifact.modelSyncFiles.first()
        return appModelSync.syncFile.let { appModelSyncFile ->
            appModelSyncFile.delete()
            val result = project.executor().run(appModelSync.taskName)
            Truth.assertThat(result.failedTasks).isEmpty()
            Truth.assertThat(appModelSyncFile.exists()).isTrue()
            FileInputStream(appModelSyncFile).use {
                Variant.parseFrom(it)
            }
        }
    }

    private fun getAppVariant() =
        project.modelV2()
            .fetchModels()
            .container
            .getProject()
            .androidProject
            ?.variants
            ?.first { variant -> variant.name == "debug" }
            ?: throw RuntimeException("could not find AndroidProject model")
}
