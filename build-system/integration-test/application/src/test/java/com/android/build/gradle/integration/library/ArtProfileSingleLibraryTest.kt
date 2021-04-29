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
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.models.AndroidProject
import com.android.testutils.apk.AndroidArchive
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.HumanReadableProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ArtProfileSingleLibraryTest {

    companion object {
        const val aarEntryName = SdkConstants.FN_ART_PROFILE
        const val apkEntryName = "assets/dexopt/${SdkConstants.FN_BINARY_ART_PROFILE}"

        fun checkAndroidArtifact(
                tempFolder: TemporaryFolder,
                target: AndroidArchive,
                entryName: String,
                expected: (ByteArray) -> Unit) {
            target.getEntry(entryName)?.let {
                val tempFile = tempFolder.newFile()
                Files.newInputStream(it).use { inputStream ->
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                expected(tempFile.readBytes())
            } ?: fail("Entry $entryName is null")
        }
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testSingleLibraryComposeMerging() {

        project.getSubproject(":app").buildFile.appendText(
                """
                    android {
                        defaultConfig {
                            minSdkVersion = 28
                        }
                    }
                """.trimIndent()
        )

        val library = project.getSubproject(":lib")
        val androidAssets = library.mainSrcDir.parentFile
        androidAssets.mkdir()

        val singleFileContent =
                """
                    HSPLcom/google/Foo;->method(II)I
                    HSPLcom/google/Foo;->method-name-with-hyphens(II)I
                """.trimIndent()

        File(androidAssets,
                SdkConstants.FN_ART_PROFILE).writeText(
                singleFileContent
        )

        val result = project.executor()
                .with(BooleanOption.ENABLE_ART_PROFILES, true)
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                .run(":lib:bundleReleaseAar", ":app:assembleRelease")
        Truth.assertThat(result.failedTasks).isEmpty()

        val libFile = FileUtils.join(
                project.getSubproject(":lib").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.LIBRARY_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_ART_PROFILE,
        )
        Truth.assertThat(libFile.readText()).isEqualTo(singleFileContent)

        // check packaging.
        project.getSubproject(":lib").getAar("release") {
            checkAndroidArtifact(tempFolder, it, aarEntryName) { fileContent ->
                Truth.assertThat(fileContent).isEqualTo(singleFileContent.toByteArray())
            }
        }

        val mergedFile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_ART_PROFILE,
        )
        Truth.assertThat(mergedFile.readText()).isEqualTo(singleFileContent)
        Truth.assertThat(
                HumanReadableProfile(mergedFile) {
                    fail(it)
                }
        ).isNotNull()

        val binaryProfile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.BINARY_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_BINARY_ART_PROFILE,
        )
        Truth.assertThat(
                ArtProfile(ByteArrayInputStream(binaryProfile.readBytes()))
        ).isNotNull()

        // check packaging.
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE).also {
            checkAndroidArtifact(tempFolder, it, apkEntryName) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
        }
    }
}
