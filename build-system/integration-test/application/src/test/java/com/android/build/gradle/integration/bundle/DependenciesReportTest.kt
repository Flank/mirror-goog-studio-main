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
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.truth.FileSubject
import com.android.tools.build.libraries.metadata.AppDependencies
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.collect.ImmutableList.toImmutableList
import org.junit.runners.JUnit4
import java.util.zip.ZipFile
import kotlin.test.fail
import com.google.common.truth.Truth.assertThat

/**
 * Tests that the resolved version of the dependencies are added to the bundle.
 */
@RunWith(JUnit4::class)
class DependenciesReportTest {

    val app =  MinimalSubProject.app("com.example.app")

    // Add both 1.0.0 and 1.0.1 so that androidx.core.core dependencies will be 1.0.0 and 1.0.1
    // We want to test that only the resolved 1.0.1 dependency gets added. Fragment implicitly tries
    // to pull in 1.0.0 of androidx.core.
    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":app", app)
            .dependency(app, "androidx.fragment:fragment:1.0.0")
            .dependency(app, "androidx.core:core:1.0.1")
            .build()
    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testDependenciesFile() {
        // test that androidx.core.core is only using 1.0.1 which will be the resolved version.
        project.executor().run(":app:bundleDebug")
        val bundle = getApkFolderOutput("debug").bundleFile
        FileSubject.assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseDelimitedFrom(it.getInputStream(dependenciesFile))
            val mavenLib = deps.libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .map { library-> library.mavenLibrary }
                .filter { library -> library.groupId.equals("androidx.core") && library.artifactId.equals("core") }
                .collect(toImmutableList())
            assertThat(mavenLib).hasSize(1)
            assertThat(mavenLib.get(0).version).isEqualTo("1.0.1")
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
