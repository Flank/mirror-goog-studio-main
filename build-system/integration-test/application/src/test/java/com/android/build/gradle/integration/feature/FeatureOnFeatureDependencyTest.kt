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
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test checking that a dynamic-feature test can reference the base application code.
 */
class FeatureOnFeatureDependencyTest {

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
            """
                        android {
                            dynamicFeatures = [':feature1', ':middleFeature', ':leafFeature']
                            defaultConfig.minSdkVersion 14
                        }
                        """
        )
    private val feature1 = createFeatureSplitWithGsonDep("com.example.feature1")
    private val middleFeature = createFeatureSplit("com.example.middleFeature")
    private val leafFeature = createFeatureSplitWithGsonDep("com.example.leafFeature")

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("android.defaultConfig.minSdkVersion 14")

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
            .build()

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(testApp)
        .create()

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
            "packaged_manifests",
            "debug",
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

    private fun createFeatureSplit(packageName: String) = MinimalSubProject.dynamicFeature(packageName)

    private fun getMergedManifestFile(project: GradleTestProject): File {
        return project.getIntermediateFile(
            "packaged_manifests",
            "debug",
            "AndroidManifest.xml")
    }
}
