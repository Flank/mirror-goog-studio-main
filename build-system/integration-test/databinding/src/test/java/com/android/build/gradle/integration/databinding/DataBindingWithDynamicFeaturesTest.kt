/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.Apk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * <pre>
 * This test builds dynamic features modules and a library module with databinding enabled,
 * and checks the correctness of the apks.
 *
 * Diagram:
 *  +------------------>-------------+
 *  |                                |
 * app <-+--<-- featureA ------>-----+-----> libraryModule
 *       |
 *       +--<-- featureB
 *
 * More explicitly
 * featureA and featureB depend on app
 * app and featureA depend on the libraryModule
 *
 * </pre>
 */

@RunWith(FilterableParameterized::class)
class DataBindingWithDynamicFeaturesTest(private val useAndroidX : Boolean) {
    companion object {
        @Parameterized.Parameters(name = "useAndroidX_{0}")
        @JvmStatic
        fun params() = listOf(true, false)

        data class DataBindingClass(private val support : String, private val androidX : String) {
            fun get(useAndroidX: Boolean) = if(useAndroidX) {
                androidX
            } else {
                support
            }
        }
    }

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("databindingWithDynamicFeatures")
        .addGradleProperties(BooleanOption.USE_ANDROID_X.propertyName + "=" + useAndroidX)
        .withDependencyChecker(false)
        .create()

    @Test
    fun checkApkContainsDataBindingClasses() {
        project.executor().run("clean", "assembleDebug")
        val aApk: Apk = project.getSubproject("featureA")
            .getApk(GradleTestProject.ApkType.DEBUG)
        val bApk: Apk = project.getSubproject("featureB")
            .getApk(GradleTestProject.ApkType.DEBUG)

        assertThat(aApk).exists()
        assertThat(bApk).exists()

        val baseApk: Apk = project.getSubproject("app")
            .getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(baseApk).exists()

        val featureAClasses = listOf(
                bindingClass(FEATURE_A, FEATURE_A_ACTIVITY),
                bindingClass(FEATURE_A, FEATURE_A_ACTIVITY_IMPL),
                brClass(LIBRARY_MODULE),
                brClass(FEATURE_A))

        val featureBClasses = listOf(
                bindingClass(FEATURE_B, FEATURE_B_ACTIVITY),
                bindingClass(FEATURE_B, FEATURE_B_ACTIVITY_IMPL),
                brClass(FEATURE_B))

        val baseClasses = listOf(
                brClass(BASE_ADAPTERS.get(useAndroidX)),
                brClass(BASE),
                DATA_BINDING_COMPONENT.get(useAndroidX),
                MERGED_MAPPER.get(useAndroidX),
                brClass(BASE_ADAPTERS.get(useAndroidX)),
                bindingClass(BASE, BASE_ACTIVITY),
                bindingClass(BASE, BASE_ACTIVITY_IMPL))
        featureAClasses.forEach {
            assertThat(aApk).containsClass(it)
            assertThat(baseApk).doesNotContainClass(it)
            assertThat(bApk).doesNotContainClass(it)
        }

        featureBClasses.forEach {
            assertThat(bApk).containsClass(it)
            assertThat(baseApk).doesNotContainClass(it)
            assertThat(aApk).doesNotContainClass(it)
        }

        baseClasses.forEach {
            assertThat(baseApk).containsClass(it)
            assertThat(aApk).doesNotContainClass(it)
            assertThat(bApk).doesNotContainClass(it)
        }
    }

    private fun brClass(pkg: String): String {
        return "L${packageToDir(pkg)}/BR;"
    }

    private fun bindingClass(pkg: String, klass: String): String {
        return "L${packageToDir(pkg)}/databinding/$klass;"
    }

    private fun packageToDir(pkg: String): String {
        return pkg.split(".").joinToString("/")
    }

    private val PROJECT_PACKAGE = "com.example"
    private val BASE = "$PROJECT_PACKAGE.app"
    private val FEATURE_A = "$PROJECT_PACKAGE.featureA"
    private val FEATURE_B = "$PROJECT_PACKAGE.featureB"
    private val MERGED_MAPPER = DataBindingClass(
            support = "Landroid/databinding/DataBinderMapperImpl;",
            androidX = "Landroidx/databinding/DataBinderMapperImpl;")
    private val DATA_BINDING_COMPONENT = DataBindingClass(
            support = "Landroid/databinding/DataBindingComponent;",
            androidX = "Landroidx/databinding/DataBindingComponent;")
    private val LIBRARY_MODULE = "$PROJECT_PACKAGE.libraryModule"
    private val FEATURE_A_ACTIVITY = "ActivityMainBinding"
    private val FEATURE_A_ACTIVITY_IMPL = "ActivityMainBindingImpl"
    private val FEATURE_B_ACTIVITY = "FeatureBMainBinding"
    private val FEATURE_B_ACTIVITY_IMPL = "FeatureBMainBindingImpl"
    private val BASE_ACTIVITY = "AppLayoutBinding"
    private val BASE_ACTIVITY_IMPL = "AppLayoutBindingImpl"
    private val BASE_ADAPTERS = DataBindingClass(
            support = "com.android.databinding.library.baseAdapters",
            androidX = "androidx.databinding.library.baseAdapters")
}
