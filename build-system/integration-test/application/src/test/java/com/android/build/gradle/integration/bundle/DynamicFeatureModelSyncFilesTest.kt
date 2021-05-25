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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.ide.common.build.filebasedproperties.variant.VariantProperties
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

class DynamicFeatureModelSyncFilesTest {
    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
        subProject(":feature1") {
            plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
            android {
                defaultCompileSdk()
            }
        }
    }

    @Test
    fun testTestModuleModel() {
        val variantSyncFileModel = getTestModuleSyncFile()
        Truth.assertThat(variantSyncFileModel.variantCase)
                .isEqualTo(VariantProperties.VariantCase.DYNAMICFEATUREVARIANTPROPERTIES)
    }

    @Test
    fun testManifestPlaceholders() {
        project.getSubproject(":feature1").buildFile.appendText(
                """
                    android {
                        buildTypes {
                            debug {
                                manifestPlaceholders = ["label": "some_value"]
                            }
                        }
                    }
                """.trimIndent()
        )
        val variantSyncFileModel = getTestModuleSyncFile()
        val commonModel = variantSyncFileModel.dynamicFeatureVariantProperties.artifactOutputProperties
        Truth.assertThat(commonModel.manifestPlaceholdersCount).isEqualTo(1)
        Truth.assertThat(commonModel.manifestPlaceholdersMap["label"]).isEqualTo("some_value")

    }


    private fun getTestModuleSyncFile(): VariantProperties {
        val variant = getTestModuleVariant()
        Truth.assertThat(variant.mainArtifact.modelSyncFiles.size).isEqualTo(1)
        val appModelSync = variant.mainArtifact.modelSyncFiles.first()
        return appModelSync.syncFile.let { appModelSyncFile ->
            appModelSyncFile.delete()
            val result = project.executor().run(appModelSync.taskName)
            Truth.assertThat(result.failedTasks).isEmpty()
            Truth.assertThat(appModelSyncFile.exists()).isTrue()
            FileInputStream(appModelSyncFile).use {
                VariantProperties.parseFrom(it)
            }
        }
    }

    private fun getTestModuleVariant(): com.android.builder.model.v2.ide.Variant {
        val androidProject = project.modelV2()
            .fetchModels("debug")
            .container
            .getProject(":feature1")
            .androidProject
            ?: throw RuntimeException("No AndroidProject model for :feature1")

        return androidProject
            .variants
            .first { variant -> variant.name == "debug" }

    }
}
