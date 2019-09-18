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

package com.android.build.gradle.integration.feature

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.testutils.truth.FileSubject.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test checking that a dynamic-feature test can reference the base application code.
 */
@RunWith(FilterableParameterized::class)
class FeatureOnFeatureDependencyTest(val multiApkMode: MultiApkMode) {

    enum class MultiApkMode {
        DYNAMIC_APP, INSTANT_APP
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getConfigurations(): Collection<Array<MultiApkMode>> =
            listOf(arrayOf(MultiApkMode.DYNAMIC_APP), arrayOf(MultiApkMode.INSTANT_APP))
    }

    private val baseModule =
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP ->
                MinimalSubProject.app("com.example.baseModule")
                    .appendToBuild(
                        """
                        android {
                            dynamicFeatures = [':feature1', ':middleFeature', ':leafFeature']
                            defaultConfig.minSdkVersion 14
                        }
                        """
                    )
            MultiApkMode.INSTANT_APP ->
                MinimalSubProject.feature("com.example.baseModule")
                    .appendToBuild(
                        """
                        android {
                            baseFeature true
                            defaultConfig.minSdkVersion 14
                        }
                        """
                    )
        }

    private val feature1 = createFeatureSplitWithGsonDep("com.example.feature1")
    private val middleFeature = createFeatureSplit("com.example.middleFeature")
    private val leafFeature = createFeatureSplitWithGsonDep("com.example.leafFeature")

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("android.defaultConfig.minSdkVersion 14")

    private val instantApp = MinimalSubProject.instantApp()

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":baseModule", baseModule)
            .subproject(":feature1", feature1)
            .subproject(":middleFeature", middleFeature)
            .subproject(":leafFeature", leafFeature)
            .dependency(feature1, baseModule)
            .dependency(middleFeature, baseModule)
            .dependency(leafFeature, baseModule)
            .dependency(middleFeature, feature1)
            .dependency(leafFeature, middleFeature)
            .let {
                when (multiApkMode) {
                    MultiApkMode.DYNAMIC_APP -> it
                    MultiApkMode.INSTANT_APP ->
                        it
                            .subproject(":app", app)
                            .subproject(":instantApp", instantApp)
                            .dependency(app, baseModule)
                            .dependency(app, feature1)
                            .dependency(app, middleFeature)
                            .dependency(app, leafFeature)
                            .dependency(instantApp, baseModule)
                            .dependency(instantApp, feature1)
                            .dependency(instantApp, middleFeature)
                            .dependency(instantApp, leafFeature)
                            .dependency("application", baseModule, app)
                            .dependency("feature", baseModule, feature1)
                            .dependency("feature", baseModule, middleFeature)
                            .dependency("feature", baseModule, leafFeature)
                }
            }
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun buildApp() {
        project.execute("clean", "assembleDebug")
    }

    @Test
    fun includeUsesSplitInFeatureManifestForMiddleFeature() {
        project.execute("clean", "assembleDebug")

        val manifestFile = getMergedManifestFile(project.getSubproject(":middleFeature"))

        assertThat(manifestFile).exists()
        assertThat(manifestFile)
            .containsAllOf(
                "<uses-split android:name=\"feature1\" />")

        // Ensure we're not somehow including the wrong dependencies
        assertThat(manifestFile)
            .doesNotContain(
                "<uses-split android:name=\"leafFeature\" />")
        assertThat(manifestFile)
            .doesNotContain(
                "<uses-split android:name=\"baseModule\" />")
    }

    @Test
    fun includeUsesSplitInFeatureManifestForLeafFeature() {
        project.execute("clean", "assembleDebug")

        val manifestFile = getMergedManifestFile(project.getSubproject(":leafFeature"))

        assertThat(manifestFile).exists()
        assertThat(manifestFile)
            .containsAllOf(
                "<uses-split android:name=\"feature1\" />",
                "<uses-split android:name=\"middleFeature\" />")

    }

    @Test
    fun includeUsesSplitInBundleManifest() {
        project.execute("clean", "assembleDebug")

        val manifestFile = project.getSubproject(":leafFeature").getIntermediateFile(
            "bundle_manifest",
            if (multiApkMode == MultiApkMode.INSTANT_APP) "debugFeature" else "debug",
            "bundle-manifest",
            "AndroidManifest.xml")

        assertThat(manifestFile).exists()
        assertThat(manifestFile)
            .containsAllOf(
                "<uses-split android:name=\"feature1\" />",
                "<uses-split android:name=\"middleFeature\" />")
    }

    @Test
    fun doesNotPropagateUsesSplitsWhenMergingDependencyManifests() {
        project.execute("clean", "assembleDebug")

        val feature1Manifest = getMergedManifestFile(project.getSubproject(":feature1"))
        assertThat(feature1Manifest).exists()
        assertThat(feature1Manifest).doesNotContain("uses-split")

        val baseManifest = getMergedManifestFile(project.getSubproject(":baseModule"))
        assertThat(baseManifest).exists()
        assertThat(baseManifest).doesNotContain("uses-split")
    }

    private fun createFeatureSplitWithGsonDep(packageName: String) =
        createFeatureSplit(packageName)
            .appendToBuild(
                """
                dependencies {
                    implementation group: 'com.google.code.gson', name: 'gson', version: '2.8.5'
                }"""
            )

    private fun createFeatureSplit(packageName: String) =
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP -> MinimalSubProject.dynamicFeature(packageName)
            MultiApkMode.INSTANT_APP -> MinimalSubProject.feature(packageName)
        }

    private fun getMergedManifestFile(project: GradleTestProject): File {
        return project.getIntermediateFile(
            "merged_manifests",
            if (multiApkMode == MultiApkMode.INSTANT_APP) "debugFeature" else "debug",
            "AndroidManifest.xml")
    }
}