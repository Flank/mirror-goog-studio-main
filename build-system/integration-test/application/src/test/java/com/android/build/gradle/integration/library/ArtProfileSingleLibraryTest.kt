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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.models.AndroidProject
import com.android.testutils.apk.AndroidArchive
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ArtProfileSingleLibraryTest {

    companion object {
        const val entryName = "${SdkConstants.FN_ANDROID_PRIVATE_ASSETS}/${SdkConstants.FN_ART_PROFILE}"

        fun checkAndroidArtifact(tempFolder: TemporaryFolder, target: AndroidArchive, expected: String) {
            target.getEntry(entryName)?.let {
                val tempFile = tempFolder.newFile()
                Files.newInputStream(it).use { inputStream ->
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                Truth.assertThat(tempFile.readText()).isEqualTo(expected)
            } ?: fail("Entry $entryName is null")
        }
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testSingleLibraryComposeMerging() {
        val library = project.getSubproject(":lib")
        val androidAssets = File(library.mainSrcDir.parentFile, SdkConstants.FN_ANDROID_PRIVATE_ASSETS)
        androidAssets.mkdir()

        val singleFileContent =
                """
                    line 1
                    line 2
                """.trimIndent()

        File(androidAssets,
                SdkConstants.FN_ART_PROFILE).writeText(
                singleFileContent
        )

        val result = project.executor()
                .with(BooleanOption.ENABLE_ART_PROFILES, true)
                .run(":lib:bundleDebugAar", ":app:assembleDebug")
        Truth.assertThat(result.failedTasks).isEmpty()

        val libFile = FileUtils.join(
                project.getSubproject(":lib").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.LIBRARY_ART_PROFILE.getFolderName(),
                "debug",
                SdkConstants.FN_ART_PROFILE,
        )
        Truth.assertThat(libFile.readText()).isEqualTo(singleFileContent)

        // check packaging.
        project.getSubproject(":lib").getAar("debug") {
            checkAndroidArtifact(tempFolder, it, singleFileContent)
        }

        val mergedFile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
                "debug",
                SdkConstants.FN_ART_PROFILE,
        )
        Truth.assertThat(mergedFile.readText()).isEqualTo(singleFileContent)

        // check packaging.
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG).also {
            checkAndroidArtifact(tempFolder, it, singleFileContent)
        }
    }


}
