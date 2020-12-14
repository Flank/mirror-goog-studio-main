/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.options.StringOption
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class TextureTargetedAssetPackTest {

    private val textureTargetedAssetPackTestApp = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.texturetargetedassetpacktestapp")
            .appendToBuild(
                """android.assetPacks = [':level1']"""
            )
            .appendToBuild(
                """android.bundle.texture.enableSplit = true;
                   |android.bundle.texture.defaultFormat = "etc2";""".trimMargin()
            )

        val level1 = MinimalSubProject.assetPack()
            .appendToBuild(
                """assetPack {
                          |  packName = "level1"
                          |  dynamicDelivery {
                          |    deliveryType = "install-time"
                          |  }
                          |}""".trimMargin()
            )
            .withFile(
                "src/main/assets/commonFile.txt",
                """This is an asset file for level 1."""
            )
            .withFile(
                "src/main/assets/textures#tcf_astc/astc.txt",
                """ASTC texture"""
            )
            .withFile(
                "src/main/assets/textures#tcf_etc2/etc2.txt",
                """ETC2 texture"""
            )

        subproject(":app", app)
        subproject(":level1", level1)
    }
        .build()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(textureTargetedAssetPackTestApp)
        .create()

    @Test
    fun buildDebugApksForRecentAstcDevice() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")
        var jsonFile = getJsonFile(27, true)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // Fetch the build output model.
        var apkFolder = getApkFolderOutput("debug").apkFolder
        assertThat(apkFolder).isDirectory()

        // Verify the installed apks.
        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List")
            .containsExactly(
                "level1-master.apk",
                "level1-astc.apk",
                "base-master.apk",
                "base-xxhdpi.apk"
            )

        // Verify the content of the asset-pack apks.
        val level1MasterApk = File(apkFolder, "level1-master.apk")
        Zip(level1MasterApk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .contains("/assets/commonFile.txt")
        }

        val level1AstcApk = File(apkFolder, "level1-astc.apk")
        Zip(level1AstcApk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .contains("/assets/textures/astc.txt")
        }
    }

    @Test
    fun buildDebugApksForRecentEtc2Device() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")
        var jsonFile = getJsonFile(27, false)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // Fetch the build output model.
        var apkFolder = getApkFolderOutput("debug").apkFolder
        assertThat(apkFolder).isDirectory()

        // Verify the installed apks.
        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List")
            .containsExactly(
                "level1-master.apk",
                "level1-etc2.apk",
                "base-master.apk",
                "base-xxhdpi.apk"
            )

        // Verify the content of the asset-pack apks.
        val level1MasterApk = File(apkFolder, "level1-master.apk")
        Zip(level1MasterApk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .contains("/assets/commonFile.txt")
        }

        val level1Etc2Apk = File(apkFolder, "level1-etc2.apk")
        Zip(level1Etc2Apk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .contains("/assets/textures/etc2.txt")
        }
    }

    @Test
    fun buildStandaloneDebugApksForPreLDevice() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")
        var jsonFile = getJsonFile(18, false)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("app:$apkFromBundleTaskName")

        // Fetch the build output model.
        var apkFolder = getApkFolderOutput("debug").apkFolder
        assertThat(apkFolder).isDirectory()

        // Verify the installed standalone apk.
        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List")
            .containsExactly("standalone-etc2.apk")

        val standaloneEtc2Apk = File(apkFolder, "standalone-etc2.apk")
        Zip(standaloneEtc2Apk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                .containsAllOf(
                    "/assets/commonFile.txt",
                    "/assets/textures/etc2.txt"
                )
        }
    }

    private fun getApkFromBundleTaskName(name: String): String {
        // Query the model to get the task name.
        val syncModels = project.model()
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":app"] ?: fail("Failed to get sync model for :app module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.apkFromBundleTaskName
            ?: fail("Module App does not have apkFromBundle task name")
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model()
            .fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel = outputModels.rootBuildModelMap[":app"]
            ?: fail("Failed to get output model for :app module")

        return outputAppModel.getOutputByName(variantName)
    }

    private fun getJsonFile(api: Int, supportsAstc: Boolean): Path {
        val tempFile = Files.createTempFile("", "texture-target-asset-pack-app-test")
        val glExtension = if (supportsAstc) "GL_KHR_texture_compression_astc_ldr" else ""

        Files.write(
            tempFile, listOf(
                """{ "supportedAbis": [ "X86", "ARMEABI_V7A" ],
                  |  "supportedLocales": [ "en", "fr" ],
                  |  "screenDensity": 480,
                  |  "deviceFeatures": ["reqGlEsVersion=0x30000"],
                  |  "glExtensions": ["$glExtension", "GL_EXT_debug_marker"],
                  |  "sdkVersion": $api
                  |  }""".trimMargin()
            )
        )

        return tempFile
    }
}
