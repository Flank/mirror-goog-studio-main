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

package com.android.build.gradle.integration.packaging

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.getVariantBuildInformationByName
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Streams;
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class BundleOptionsTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
                .appendToBuild("android.defaultConfig.versionCode 1")
                .withFile("src/main/assets/textures#tcf_atc/texture.dat", byteArrayOf(0x3))
                .withFile("src/main/assets/textures#tcf_etc1/texture.dat", byteArrayOf(0x4))
                .withFile("src/main/assets/textures/texture.dat", byteArrayOf(0x5))
                .withFile("src/main/jniLibs/armeabi/abi.so", byteArrayOf(0xA))
                .withFile("src/main/jniLibs/x86/abi.so", byteArrayOf(0x8, 0x6))
                .withFile("src/main/res/raw-hdpi/density", byteArrayOf(0x1))
                .withFile("src/main/res/raw-xhdpi/density", byteArrayOf(0x2))
                .withFile("src/main/res/raw-en/language", byteArrayOf(0xE))
                .withFile("src/main/res/raw-fr/language", byteArrayOf(0xF))
        ).create()

    @Test
    fun bundleSplitOptionsTest() {
        val apks = generateApks()
        assertThat(apks).containsAllOf("base-x86.apk", "base-hdpi.apk", "base-atc.apk")

        project.buildFile.appendText("\nandroid.bundle.abi.enableSplit=false", StandardCharsets.UTF_8)
        val apksNoAbiSplit = generateApks()
        assertThat(apksNoAbiSplit).doesNotContain("base-x86.apk")
        assertThat(apksNoAbiSplit).contains("base-hdpi.apk")


        project.buildFile.appendText("\nandroid.bundle.density.enableSplit=false", StandardCharsets.UTF_8)
        val apksNoDensitySplit = generateApks()
        assertThat(apksNoDensitySplit).doesNotContain("base-x86.apk")
        assertThat(apksNoDensitySplit).doesNotContain("base-hdpi.apk")


        project.buildFile.appendText("\nandroid.bundle.texture.enableSplit=false", StandardCharsets.UTF_8)
        val apksNoTextureSplit = generateApks()
        assertThat(apksNoTextureSplit).doesNotContain("base-etc1_rgb8.apk")
        assertThat(apksNoTextureSplit).doesNotContain("base-atc.apk")


        // TODO: Support support for language splits?
        //project.buildFile.appendText("\nandroid.bundle.language.enableSplit=false", StandardCharsets.UTF_8)
        //val apksNoLanguageSplit = generateApks()
        //assertThat(apksNoLanguageSplit).doesNotContain("standalone-hdpi.apk")
    }

    @Test
    fun testBundleModels() {

        fun checkPostBundleModel(postBundleModel: File) {
            assertThat(postBundleModel.exists()).isTrue()
            val bundleInfo = BuiltArtifactsLoaderImpl.loadFromFile(postBundleModel)
            assertThat(bundleInfo!!.elements).hasSize(1)
            val bundleFile = bundleInfo.elements.first().outputFile
            assertThat(bundleFile).isNotNull()
            assertThat(File(bundleFile).exists()).isTrue()
        }

        val projectModel = project.executeAndReturnModel(":bundleDebug")
        // check that model files are present.
        val debugBuildInformation =
            projectModel.onlyModel.getVariantBuildInformationByName("debug")
        assertThat(debugBuildInformation.bundleTaskName).isNotNull()
        checkPostBundleModel(File(debugBuildInformation.bundleTaskOutputListingFile!!))

        val mainArtifact = projectModel.onlyModel.getVariantByName("debug").mainArtifact
        assertThat(mainArtifact.bundleTaskOutputListingFile).isNotNull()
        val postBundleModel = File(mainArtifact.bundleTaskOutputListingFile!!)
        checkPostBundleModel(postBundleModel)
    }

    private fun generateApks(): Set<String> {
        project.executor().run(":makeApkFromBundleForDebug")
        return FileUtils.createZipFilesystem(
            project.getIntermediateFile(
                "apks_from_bundle",
                "debug",
                "bundle.apks"
            ).toPath()
        ).use { apks ->
            Streams.concat(
                Files.list(apks.getPath("splits/")),
                Files.list(apks.getPath("standalones/"))).use {
                    it.map{ it.fileName.toString() }.collect(ImmutableSet.toImmutableSet())
                }
        }
    }
}
