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

package com.android.build.gradle.integration.databinding


import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.testutils.apk.Apk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * <pre>
 * This test adds empty classes to keep and to discard to dynamic features modules and a library
 * module with databinding enabled, and checks the correctness of the apks.
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
 * Each module gets an empty class to keep and an empty class to remove, as well as a
 * proguard-rules.pro file telling it what to keep. libraryModule has that file set as a consumer
 * proguard rules file as well so the modules that depend on it also keep the files in it.
 * </pre>
 * This test is based on [DataBindingWithDynamicFeaturesTest] and modified for code shrinking.
 */

class DataBindingMinifyDynamicFeaturesTest {

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("databindingWithDynamicFeatures")
        .withDependencyChecker(false)
        .create()

    @Before
    fun setup() {
        val minifyEnable =
            """
            android {
                buildTypes {
                    minified.initWith(buildTypes.debug)
                    minified {
                        proguardFiles "proguard-rules.pro"
                    }
                }
            }
            """.trimIndent()

        val minifyBase =
            """
            android {
                buildTypes {
                    minified.initWith(buildTypes.debug)
                    minified {
                        minifyEnabled true
                        proguardFiles getDefaultProguardFile('proguard-android.txt'),
                        "proguard-rules.pro"
                    }
                }
            }
            """.trimIndent()


        val emptyClassKeep = { module: String ->
            """
                package $PROJECT_PACKAGE.$module;
                public class $EMPTY_CLASS_TO_KEEP {}
            """.trimIndent()
        }

        val emptyClassRemove = { module: String ->
            """
                package $PROJECT_PACKAGE.$module;
                public class $EMPTY_CLASS_TO_REMOVE {}
            """.trimIndent()
        }
        val proguardFile = { module: String ->
            """-keep public class $PROJECT_PACKAGE.$module.$EMPTY_CLASS_TO_KEEP"""
        }

        // Append minify setup to build files
        project.getSubproject("app").buildFile.appendText(minifyBase)
        project.getSubproject("featureA").buildFile.appendText(minifyEnable)
        project.getSubproject("featureB").buildFile.appendText(minifyEnable)
        project.getSubproject("libraryModule").buildFile.appendText(minifyEnable)

        // Create empty classes in each sub-project
        val subProjects = listOf("app", "featureA", "featureB", "libraryModule")

        for (subProject in subProjects) {
            project.getSubproject(subProject).mainSrcDir
                .resolve("${packageToDir(PROJECT_PACKAGE)}/$subProject/$EMPTY_CLASS_TO_KEEP.java")
                .also { it.parentFile.mkdirs() }
                .writeText(emptyClassKeep(subProject))
            project.getSubproject(subProject).mainSrcDir
                .resolve("${packageToDir(PROJECT_PACKAGE)}/$subProject/$EMPTY_CLASS_TO_REMOVE.java")
                .writeText(emptyClassRemove(subProject))
            project.getSubproject(subProject).file("proguard-rules.pro")
                .writeText(proguardFile(subProject))
        }

        // Declaring rules file to also be consumer rules so it also gets kept in dependencies.
        project.getSubproject("libraryModule").buildFile
            .appendText("\nandroid.buildTypes.minified.consumerProguardFiles \"proguard-rules.pro\"")
    }

    @Test
    fun assembleMinified() {
        project.executor().run("assembleMinified")

        val minifiedApk = "minified"

        val aApk: Apk = project.getSubproject("featureA")
            .getApk(minifiedApk)
        val bApk: Apk = project.getSubproject("featureB")
            .getApk(minifiedApk)

        TruthHelper.assertThat(aApk).exists()
        TruthHelper.assertThat(bApk).exists()

        val baseApk: Apk = project.getSubproject("app")
            .getApk(minifiedApk)
        TruthHelper.assertThat(baseApk).exists()

        val featureAClasses = listOf(
            regularClass(LIBRARY_MODULE, EMPTY_CLASS_TO_KEEP),
            regularClass(FEATURE_A, EMPTY_CLASS_TO_KEEP))

        val featureBClasses = listOf(
            regularClass(FEATURE_B, EMPTY_CLASS_TO_KEEP))

        val baseClasses = listOf(
            regularClass(BASE, EMPTY_CLASS_TO_KEEP),
            MERGED_MAPPER)

        val shrunkClasses = listOf(
            brClass(LIBRARY_MODULE),
            bindingClass(FEATURE_A, FEATURE_A_ACTIVITY),
            bindingClass(FEATURE_A, FEATURE_A_ACTIVITY_IMPL),
            regularClass(FEATURE_A, EMPTY_CLASS_TO_REMOVE),
            brClass(FEATURE_A),
            bindingClass(FEATURE_B, FEATURE_B_ACTIVITY),
            bindingClass(FEATURE_B, FEATURE_B_ACTIVITY_IMPL),
            regularClass(FEATURE_B, EMPTY_CLASS_TO_REMOVE),
            brClass(FEATURE_B),
            bindingClass(BASE, BASE_ACTIVITY),
            bindingClass(BASE, BASE_ACTIVITY_IMPL),
            regularClass(BASE, EMPTY_CLASS_TO_REMOVE),
            brClass(BASE_ADAPTERS),
            brClass(BASE),
            brClass(BASE_ADAPTERS),
            DATA_BINDING_COMPONENT,
            regularClass(LIBRARY_MODULE, EMPTY_CLASS_TO_REMOVE))

        featureAClasses.forEach {
            TruthHelper.assertThat(aApk).containsClass(it)
            TruthHelper.assertThat(baseApk).doesNotContainClass(it)
            TruthHelper.assertThat(bApk).doesNotContainClass(it)
        }

        featureBClasses.forEach {
            TruthHelper.assertThat(bApk).containsClass(it)
            TruthHelper.assertThat(baseApk).doesNotContainClass(it)
            TruthHelper.assertThat(aApk).doesNotContainClass(it)
        }

        baseClasses.forEach {
            TruthHelper.assertThat(baseApk).containsClass(it)
            TruthHelper.assertThat(aApk).doesNotContainClass(it)
            TruthHelper.assertThat(bApk).doesNotContainClass(it)
        }

        shrunkClasses.forEach {
            TruthHelper.assertThat(aApk).doesNotContainClass(it)
            TruthHelper.assertThat(bApk).doesNotContainClass(it)
            TruthHelper.assertThat(baseApk).doesNotContainClass(it)
        }
    }

    private fun brClass(pkg: String): String {
        return "L${packageToDir(pkg)}/BR;"
    }

    private fun bindingClass(pkg: String, klass: String): String {
        return "L${packageToDir(pkg)}/databinding/$klass;"
    }

    private fun regularClass(pkg: String, klass: String): String {
        return "L${packageToDir(pkg)}/$klass;"
    }

    private fun packageToDir(pkg: String): String {
        return pkg.split(".").joinToString("/")
    }

    private val PROJECT_PACKAGE = "com.example"
    private val BASE = "$PROJECT_PACKAGE.app"
    private val FEATURE_A = "$PROJECT_PACKAGE.featureA"
    private val FEATURE_B = "$PROJECT_PACKAGE.featureB"
    private val MERGED_MAPPER = "Landroid/databinding/DataBinderMapperImpl;"
    private val DATA_BINDING_COMPONENT = "Landroid/databinding/DataBindingComponent;"
    private val LIBRARY_MODULE = "$PROJECT_PACKAGE.libraryModule"
    private val FEATURE_A_ACTIVITY = "ActivityMainBinding"
    private val FEATURE_A_ACTIVITY_IMPL = "ActivityMainBindingImpl"
    private val FEATURE_B_ACTIVITY = "FeatureBMainBinding"
    private val FEATURE_B_ACTIVITY_IMPL = "FeatureBMainBindingImpl"
    private val BASE_ACTIVITY = "AppLayoutBinding"
    private val BASE_ACTIVITY_IMPL = "AppLayoutBindingImpl"
    private val BASE_ADAPTERS = "com.android.databinding.library.baseAdapters"
    private val EMPTY_CLASS_TO_KEEP = "EmptyClassToKeep"
    private val EMPTY_CLASS_TO_REMOVE = "EmptyClassToRemove"
}
