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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.ide.common.build.filebasedproperties.variant.VariantProperties
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

class LibraryModelSyncFilesTest {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {}
        }
    }

    @Test
    fun testLibraryModel() {
        val variantSyncFileModel = getLibrarySyncModel()
        Truth.assertThat(variantSyncFileModel.variantCase)
            .isEqualTo(VariantProperties.VariantCase.LIBRARYVARIANTPROPERTIES)
    }

    @Test
    fun testManifestPlaceholders() {
        project.getSubproject(":lib").buildFile.appendText(
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
        val variantSyncFileModel = getLibrarySyncModel()
        val artifactProperties = variantSyncFileModel.libraryVariantProperties.artifactOutputProperties
        Truth.assertThat(artifactProperties.manifestPlaceholdersCount).isEqualTo(1)
        Truth.assertThat(artifactProperties.manifestPlaceholdersMap["label"]).isEqualTo("some_value")
    }


    private fun getLibrarySyncModel(): VariantProperties {
        val variant = getLibraryVariant()
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

    private fun getLibraryVariant(): com.android.builder.model.v2.ide.Variant {
        val androidProject = project.modelV2()
            .fetchModels("debug")
            .container
            .getProject(":lib")
            .androidProject
            ?: throw RuntimeException("No AndroidProject model for :lib")
        return androidProject
            .variants
            .first { variant -> variant.name == "debug" }
    }
}
