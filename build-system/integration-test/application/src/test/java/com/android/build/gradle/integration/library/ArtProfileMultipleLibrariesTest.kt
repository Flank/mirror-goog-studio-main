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
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.library.ArtProfileSingleLibraryTest.Companion.aarEntryName
import com.android.build.gradle.integration.library.ArtProfileSingleLibraryTest.Companion.apkEntryName
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.models.AndroidProject
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.HumanReadableProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ArtProfileMultipleLibrariesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(
        HelloWorldLibraryApp(
            mapOf(
                ":app" to EmptyAndroidTestApp(),
                ":lib1" to HelloWorldApp.noBuildFile("com.example.lib1"),
                ":lib2" to HelloWorldApp.noBuildFile("com.example.lib2"),
                ":lib3" to HelloWorldApp.noBuildFile("com.example.lib3"),
            )
        )
    ).create()

    @Test
    fun testMultipleLibraryComposeMerging() {

        project.getSubproject(":app").buildFile.appendText(
                """
                    android {
                        defaultConfig {
                            minSdkVersion = 28
                        }
                    }
                """.trimIndent()
        )

        for (i in 1..3) {
            val library = project.getSubproject(":lib$i")
            val androidAssets =
                    File(library.mainSrcDir.parentFile, SdkConstants.FN_ANDROID_PRIVATE_ASSETS)
            androidAssets.mkdir()

            File(androidAssets,
                    SdkConstants.FN_ART_PROFILE).writeText(
                    """
                        HSPLcom/google/Foo$i;->method(II)I
                        HSPLcom/google/Foo$i;->method-name-with-hyphens(II)I
                    """.trimIndent()
            )
        }

        val result = project.executor()
                .with(BooleanOption.ENABLE_ART_PROFILES, true)
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                .run(
                    ":lib1:bundleReleaseAar",
                    ":lib2:bundleReleaseAar",
                    ":lib3:bundleReleaseAar",
                    ":app:assembleRelease",
                )
        Truth.assertThat(result.failedTasks).isEmpty()

        var finalFileContent = ""
        for (i in 1..3) {
            val libFile = FileUtils.join(
                    project.getSubproject(":lib$i").buildDir,
                    AndroidProject.FD_INTERMEDIATES,
                    InternalArtifactType.LIBRARY_ART_PROFILE.getFolderName(),
                    "release",
                    SdkConstants.FN_ART_PROFILE,
            )
            val expectedFileContent =
                    """
                        HSPLcom/google/Foo$i;->method(II)I
                        HSPLcom/google/Foo$i;->method-name-with-hyphens(II)I
                    """.trimIndent()
            finalFileContent = finalFileContent.plus(expectedFileContent.plus("\n"))

            Truth.assertThat(libFile.readText()).isEqualTo(expectedFileContent)

            // check packaging.
            project.getSubproject(":lib$i").getAar("release") {
                ArtProfileSingleLibraryTest.checkAndroidArtifact(tempFolder, it, aarEntryName) { fileContent ->
                    Truth.assertThat(fileContent).isEqualTo(expectedFileContent.toByteArray())
                }
            }
        }

        val mergedFile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                AndroidProject.FD_INTERMEDIATES,
                InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_ART_PROFILE,
        )
        Truth.assertThat(mergedFile.readText()).isEqualTo(finalFileContent)
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
            ArtProfileSingleLibraryTest.checkAndroidArtifact(tempFolder, it, apkEntryName) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
        }
    }
}
