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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.common.collect.ImmutableList.toImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.zip.ZipFile
import kotlin.test.fail

/**
 * Tests DSL controlling addition of dependency information to bundles.
 */
@RunWith(JUnit4::class)
class DependenciesReportDslTest {
    val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.dynamicFeatures = [':feature']")
    val feature = MinimalSubProject.dynamicFeature("com.example.test").apply {
        replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution"
                    |        package="com.example.test">
                    |    <dist:module> <dist:fusing dist:include="true"/>
                    |        <dist:delivery>
                    |           <dist:install-time/>
                    |        </dist:delivery>
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))
    }

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":app", app)
            .subproject(":feature", feature)
            .dependency(feature, "androidx.fragment:fragment:1.0.0")
            .dependency(app, "androidx.core:core:1.0.1")
            .dependency(feature, app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp)
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
        // http://b/149978740
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=2")
        .create()

    @Test
    fun testDependenciesFileUnspecifiedDsl() {
        project.addUseAndroidXProperty()
        project.executor().run(":app:bundleRelease")
        val bundle = getApkFolderOutput("release").bundleFile
        assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseFrom(it.getInputStream(dependenciesFile))
            val mavenDependencyGroupIds = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .map { library -> library.mavenLibrary.groupId }
                .collect(toImmutableList())

            assertThat(mavenDependencyGroupIds).contains("androidx.core")
            assertThat(mavenDependencyGroupIds).contains("androidx.fragment")
        }
    }

    @Test
    fun testDependenciesFileDslOn() {
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
        assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseFrom(it.getInputStream(dependenciesFile))
            val mavenDependencyGroupIds = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .map { library -> library.mavenLibrary.groupId }
                .collect(toImmutableList())

            assertThat(mavenDependencyGroupIds).contains("androidx.core")
            assertThat(mavenDependencyGroupIds).contains("androidx.fragment")
        }
    }

    @Test
    fun testDependenciesFileDslOff() {
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
        assertThat(bundle).exists()
        Zip(bundle).use {
            ZipFileSubject.assertThat(it).doesNotContain("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
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
