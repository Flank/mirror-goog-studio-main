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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.FileSubject
import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.common.collect.ImmutableList.toImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.charset.Charset
import java.util.Base64
import java.util.zip.ZipFile
import kotlin.test.fail
import kotlin.test.assertFailsWith

/**
 * Tests DSL controlling addition of dependency information to bundles.
 */
@RunWith(JUnit4::class)
class DependenciesReportDslTest {
    val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.dynamicFeatures = [':feature']")
    val feature = MinimalSubProject.dynamicFeature("com.example.test")

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":app", app)
            .subproject(":feature", feature)
            .dependency(feature, "androidx.fragment:fragment:1.0.0")
            .dependency(app, "androidx.core:core:1.0.1")
            .dependency(feature, app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testDependenciesFileUnspecifiedDsl() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application />",
            "<dist:module> <dist:fusing dist:include=\"true\"/> " +
                "<dist:delivery> " +
                "<dist:install-time/> " +
                "</dist:delivery> " +
                "</dist:module> <application />")

        project.addUseAndroidXProperty()
        project.executor().run(":app:bundleRelease")
        val bundle = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseFrom(it.getInputStream(dependenciesFile))
	    println(deps.toString())
            val baseDependency = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .filter { library ->
                    library.mavenLibrary.groupId.equals("androidx.core") &&
                        library.mavenLibrary.artifactId.equals("core")
                }
                .collect(toImmutableList())

            val featureDependency = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .filter { library ->
                    library.mavenLibrary.groupId.equals("androidx.fragment") &&
                        library.mavenLibrary.artifactId.equals("fragment")
                }
                .collect(toImmutableList())

            assertThat(baseDependency).hasSize(1)
            assertThat(baseDependency.get(0).mavenLibrary.version).isEqualTo("1.0.1")
            assertThat(featureDependency).hasSize(1)
            assertThat(featureDependency.get(0).mavenLibrary.version).isEqualTo("1.0.0")
        }
    }

    @Test
    fun testDependenciesFileDslOn() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application />",
            "<dist:module> <dist:fusing dist:include=\"true\"/> " +
                "<dist:delivery> " +
                "<dist:install-time/> " +
                "</dist:delivery> " +
                "</dist:module> <application />")

        project.getSubproject(":app").buildFile.appendText(
            """
                android {
                    dependenciesInfo {
                        includeInBundle true
                    }
                }
            """
        )

        project.addUseAndroidXProperty()
        project.executor().run(":app:bundleRelease")
        val bundle = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseFrom(it.getInputStream(dependenciesFile))
            val baseDependency = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .filter { library ->
                    library.mavenLibrary.groupId.equals("androidx.core") &&
                        library.mavenLibrary.artifactId.equals("core")
                }
                .collect(toImmutableList())

            val featureDependency = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .filter { library ->
                    library.mavenLibrary.groupId.equals("androidx.fragment") &&
                        library.mavenLibrary.artifactId.equals("fragment")
                }
                .collect(toImmutableList())

            assertThat(baseDependency).hasSize(1)
            assertThat(baseDependency.get(0).mavenLibrary.version).isEqualTo("1.0.1")
            assertThat(featureDependency).hasSize(1)
            assertThat(featureDependency.get(0).mavenLibrary.version).isEqualTo("1.0.0")
        }
    }

    @Test
    fun testDependenciesFileDslOff() {
        TestFileUtils.searchAndReplace(
            File(project.getSubproject(":feature").mainSrcDir.parent, "AndroidManifest.xml"),
            "<application />",
            "<dist:module> <dist:fusing dist:include=\"true\"/> " +
                "<dist:delivery> " +
                "<dist:install-time/> " +
                "</dist:delivery> " +
                "</dist:module> <application />")

        project.getSubproject(":app").buildFile.appendText(
            """
                android {
                    dependenciesInfo {
                        includeInBundle false
                    }
                }
            """
        )

        project.addUseAndroidXProperty()
        project.executor().run(":app:bundleRelease")
        val bundle = getApkFolderOutput("release").bundleFile
        FileSubject.assertThat(bundle).exists()

        assertFailsWith(NullPointerException::class) {
            ZipFile(bundle).use {
                val deps = AppDependencies.parseFrom(it.getInputStream(
                    it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")))
            }
        }
    }

    private fun getApkFolderOutput(variantName: String): AppBundleVariantBuildOutput {
        val outputModels = project.model().fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel =
            outputModels.rootBuildModelMap[":app"]
                ?: fail("Failed to get output model for app module")

        return outputAppModel.getOutputByName(variantName)
    }
}

