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

package com.android.build.gradle.integration.bundle

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.AndroidProject
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DynamicAppTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .withoutNdk()
        .create()

    private val bundleContent: Array<String> = arrayOf(
        "/BundleConfig.pb",
        "/base/dex/classes.dex",
        "/base/manifest/AndroidManifest.xml",
        "/base/res/layout/base_layout.xml",
        "/base/resources.pb",
        "/feature1/dex/classes.dex",
        "/feature1/manifest/AndroidManifest.xml",
        "/feature1/res/layout/feature_layout.xml",
        "/feature1/resources.pb")

    @Test
    @Throws(IOException::class)
    fun `test model contains feature information`() {
        val rootBuildModelMap = project.model()
            .allowOptionWarning(BooleanOption.USE_AAPT2_FROM_MAVEN)
            .fetchAndroidProjects()
            .rootBuildModelMap

        val appModel = rootBuildModelMap[":app"]
        Truth.assertThat(appModel).named("app model").isNotNull()
        Truth.assertThat(appModel!!.dynamicFeatures)
            .named("feature list in app model")
            .containsExactly(":feature1")

        val featureModel = rootBuildModelMap[":feature1"]
        Truth.assertThat(featureModel).named("feature model").isNotNull()
        Truth.assertThat(featureModel!!.projectType)
            .named("feature model type")
            .isEqualTo(AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE)
    }

    @Test
    fun `test bundleDebug task`() {
        project.execute("app:bundleDebug")

        //TODO: update model with this stuff?
        val bundleFile = FileUtils.join(project.getSubproject("app").buildDir,
            "outputs",
            "bundle",
            "debug",
            "bundleDebug", // FIXME with proper BuildableArtifact based output file path
            "bundle.aab")
        FileSubject.assertThat(bundleFile).exists()

        val zipFile = Zip(bundleFile)
        Truth.assertThat(zipFile.entries.map { it.toString() }).containsExactly(*bundleContent)

        // also test that the feature manifest contains the feature name.
        val manifestFile = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "merged_manifests",
            "debug",
            "processDebugManifest",
            "merged",
            "AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).isFile()
        FileSubject.assertThat(manifestFile).contains("android:splitName=\"feature1\"")
    }

    @Test
    fun `test bundleRelease task`() {
        project.execute("app:bundleRelease")

        //TODO: update model with this stuff?
        val bundleFile = FileUtils.join(project.getSubproject("app").buildDir,
            "outputs",
            "bundle",
            "release",
            "bundleRelease", // FIXME with proper BuildableArtifact based output file path
            "bundle.aab")
        FileSubject.assertThat(bundleFile).exists()

        val zipFile = Zip(bundleFile)
        Truth.assertThat(zipFile.entries.map { it.toString() }).containsExactly(*bundleContent)
    }

    @Test
    fun `test abiFilter with Bundle task`() {
        val appProject = project.getSubproject(":app")
        createAbiFile(appProject, SdkConstants.ABI_ARMEABI_V7A, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM64, "libbase.so")

        TestFileUtils.appendToFile(appProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        val featureProject = project.getSubproject(":feature1")
        createAbiFile(featureProject, SdkConstants.ABI_ARMEABI_V7A, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM64, "libfeature1.so")

        TestFileUtils.appendToFile(featureProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        project.execute("app:bundleDebug")

        //TODO: update model with this stuff?
        val bundleFile = FileUtils.join(project.getSubproject("app").buildDir,
            "outputs",
            "bundle",
            "debug",
            "bundleDebug", // FIXME with proper BuildableArtifact based output file path
            "bundle.aab")
        FileSubject.assertThat(bundleFile).exists()

        val zipFile = Zip(bundleFile)

        val bundleContentWithAbis = bundleContent.plus(listOf(
            "/base/native.pb",
            "/base/lib/${SdkConstants.ABI_ARMEABI_V7A}/libbase.so",
            "/feature1/native.pb",
            "/feature1/lib/${SdkConstants.ABI_ARMEABI_V7A}/libfeature1.so"))

        Truth.assertThat(zipFile.entries.map { it.toString() }).containsExactly(*bundleContentWithAbis)
    }

    private fun createAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)

        Files.write(File(abiFolder, libName).toPath(), "some content".toByteArray())
    }
}
