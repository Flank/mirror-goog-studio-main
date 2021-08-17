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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.fixture.model.getAndroidProject
import com.android.ide.model.sync.Variant
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream

class LibraryModelSyncFilesTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp()).create()

    @Test
    fun testLibraryModel() {
        val variantSyncFileModel = getLibrarySyncModel()
        Truth.assertThat(variantSyncFileModel.variantCase)
            .isEqualTo(Variant.VariantCase.VARIANT_NOT_SET)
    }


    private fun getLibrarySyncModel(): Variant {
        val variant = getLibraryVariant()
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

    private fun getLibraryVariant() =
        project.modelV2()
            .fetchModels("debug")
            .getAndroidProject(":lib")
            .variants
            .first { variant -> variant.name == "debug" }
}
