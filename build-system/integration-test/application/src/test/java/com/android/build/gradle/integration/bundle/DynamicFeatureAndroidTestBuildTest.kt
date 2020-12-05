/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import org.junit.Rule
import org.junit.Test

/**
 *  Test to make sure we subtract dependency artifacts for android test for dynamic features
 *  correctly.
 */
class DynamicFeatureAndroidTestBuildTest {

    /**
     *       Base <------------ Middle DF <------------- DF <--------- Android Test DF
     *      /    \              /       \                |               /        \   \
     *     v      v            v         v               v              v          \   \
     *  appLib  sharedLib   midLib   sharedMidLib    featureLib    testFeatureLib   \   \
     *              ^                      ^_______________________________________/   /
     *              |________________________________________________________________/
     *
     *  DF has a feature-on-feature dep on Middle DF, both depend on Base, Android Test DF is an
     *  android test variant for DF.
     *
     *  Base depends on appLib and sharedLib.
     *  Middle DF depends on midLib and sharedMidLib.
     *  DF depends on featureLib.
     *  DF also has an android test dependency on testFeatureLib, shared and sharedMidLib.
     */
    private val sharedLib =
        MinimalSubProject.lib("com.example.sharedLib")
            .withFile("src/main/res/raw/shared_lib_file.txt", "shared lib file")

    private val appLib =
        MinimalSubProject.lib("com.example.appLib")
            .withFile("src/main/res/raw/app_lib_file.txt", "app lib file")

    private val featureLib =
        MinimalSubProject.lib("com.example.featureLib")
            .withFile("src/main/res/raw/main_feature_lib_file.txt", "feature lib file")

    private val middleFeatureLib =
        MinimalSubProject.lib("com.example.middleFeatureLib")
            .withFile("src/main/res/raw/middle_main_feature_lib_file.txt", "feature lib file")

    private val sharedMiddleFeatureLib =
        MinimalSubProject.lib("com.example.sharedMiddleFeatureLib")
            .withFile("src/main/res/raw/shared_middle_feature_lib_file.txt", "feature lib file")

    private val testFeatureLib =
        MinimalSubProject.lib("com.example.testFeatureLib")
            .withFile("src/main/res/raw/test_feature_lib_file.txt", "test feature lib file")

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
            """
                            android {
                                dynamicFeatures = [':feature', ':middleFeature']
                            }
                            """)
        .withFile("src/main/res/raw/base_file.txt", "base file")


    private val middleFeature = MinimalSubProject.dynamicFeature("com.example.middleFeature")
        .withFile("src/main/res/raw/middle_feature_file.txt", "mid feature file")

    private val feature = MinimalSubProject.dynamicFeature("com.example.feature")
        .withFile("src/main/res/raw/main_feature_file.txt", "feature file")
        .withFile("src/androidTest/res/raw/android_test_feature_file.txt", "hello")

    private val app =
        MinimalSubProject.app("com.example.app")

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":sharedLib", sharedLib)
            .subproject(":appLib", appLib)
            .subproject(":featureLib", featureLib)
            .subproject(":middleFeature", middleFeature)
            .subproject(":middleFeatureLib", middleFeatureLib)
            .subproject(":sharedMiddleFeatureLib", sharedMiddleFeatureLib)
            .subproject(":testFeatureLib", testFeatureLib)
            .subproject(":baseModule", baseModule)
            .subproject(":feature", feature)
            .dependency(feature, baseModule)
            .dependency(middleFeature, baseModule)
            .dependency(feature, middleFeature)
            .dependency(middleFeature, middleFeatureLib)
            .dependency(middleFeature, sharedMiddleFeatureLib)
            .dependency(baseModule, sharedLib)
            .androidTestDependency(feature, sharedLib)
            .dependency(baseModule, appLib)
            .dependency(feature, featureLib)
            .androidTestDependency(feature, testFeatureLib)
            .androidTestDependency(feature, sharedMiddleFeatureLib)
            .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(testApp)
            .create()

    @Test
    fun testAndroidTestBuildCorrectArtifacts() {
        // TODO: move to a connected test
        project.executor().run(":feature:processDebugAndroidTestResources")

        val androidTestFeatureR = project.getSubproject("feature")
            .getIntermediateFile("runtime_symbol_list", "debugAndroidTest", "R.txt")
        // R.txt should contain everything the AT feature depends on, but not libraries only the app
        // or the feature depend on.
        assertThat(androidTestFeatureR).contains("android_test_feature_file")
        assertThat(androidTestFeatureR).contains("shared_lib_file")
        assertThat(androidTestFeatureR).contains("test_feature_lib_file")
        assertThat(androidTestFeatureR).contains("shared_middle_feature_lib_file")
        assertThat(androidTestFeatureR).doesNotContain("app_lib_file")
        assertThat(androidTestFeatureR).doesNotContain("main_feature_lib_file")
        assertThat(androidTestFeatureR).doesNotContain("main_feature_file")
        assertThat(androidTestFeatureR).doesNotContain("base_file")
        assertThat(androidTestFeatureR).doesNotContain("middle_feature_file")
        assertThat(androidTestFeatureR).doesNotContain("middle_main_feature_lib_file")

        // The R.jar should only contain things that are ONLY in the AT feature subtree. It should
        // not contain any libs that the app or feature depend on.
        val androidTestFeatureRJar = project.getSubproject("feature").getIntermediateFile(
            "compile_and_runtime_not_namespaced_r_class_jar", "debugAndroidTest", "R.jar")
        Zip(androidTestFeatureRJar).use {
            ZipFileSubject.assertThat(it).contains("com/example/feature/test/R\$raw.class")
            ZipFileSubject.assertThat(it).contains("com/example/testFeatureLib/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/shared_lib/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/appLib/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/feature_lib/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/baseModule/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/feature/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/middleFeature/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/middleFeatureLib/R\$raw.class")
            ZipFileSubject.assertThat(it).doesNotContain("com/example/sharedMiddleFeatureLib/R\$raw.class")
        }
    }
}
