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
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.common.collect.ImmutableList.toImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Base64
import java.util.zip.ZipFile
import kotlin.test.fail

/**
 * Tests that the resolved version of the dependencies are added to the bundle.
 */
@RunWith(JUnit4::class)
class DependenciesReportTest {

    val app =  MinimalSubProject.app("com.example.app")
                  .withFile("local.jar", TestInputsGenerator.jarWithClasses(listOf()))

    val lib =  MinimalSubProject.lib("com.example.lib")
                  .withFile("local_in_lib.jar", TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/LibClass")))

    // Add both 1.0.0 and 1.0.1 so that androidx.core.core dependencies will be 1.0.0 and 1.0.1
    // We want to test that only the resolved 1.0.1 dependency gets added. Fragment implicitly tries
    // to pull in 1.0.0 of androidx.core.
    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":app", app)
            .dependency(app, "androidx.fragment:fragment:1.0.0")
            .dependency(app, "androidx.core:core:1.0.1")
            .fileDependency(app, "local.jar")
            .subproject(":lib", lib)
            .dependency(app, lib)
            .dependency(lib, "androidx.core:core:1.0.1")
            .dependency(lib, "androidx.collection:collection:1.0.0")
            .fileDependency(lib, "local_in_lib.jar")
            .build()
    @get:Rule
    val project = GradleTestProject.builder()
        // b/149978740
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .fromTestApp(testApp).create()

    @Test
    fun testDependenciesFile() {
        project.addUseAndroidXProperty()
        // test that androidx.core.core is only using 1.0.1 which will be the resolved version.
        project.executor().run(":app:bundleRelease")
        val bundle = getApkFolderOutput("release").bundleFile
        assertThat(bundle).exists()
        ZipFile(bundle).use {
            val dependenciesFile = it.getEntry("BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")
            val deps = AppDependencies.parseFrom(it.getInputStream(dependenciesFile))
            val libraryList = deps.libraryList.toList()
            val mavenLib = libraryList.stream()
                .filter { library -> library.hasMavenLibrary() }
                .filter { library -> library.mavenLibrary.groupId.equals("androidx.core") &&
                                     library.mavenLibrary.artifactId.equals("core") }
                .collect(toImmutableList())
            val fileLib = libraryList.stream()
                .filter { library -> !library.hasMavenLibrary() }
                .collect(toImmutableList())
            assertThat(mavenLib).hasSize(1)
            assertThat(mavenLib.get(0).mavenLibrary.version).isEqualTo("1.0.1")
            val base64EncodedDigest = Base64.getEncoder().encodeToString(mavenLib.get(0).digests.sha256.toByteArray())
            assertThat(base64EncodedDigest).isEqualTo("sakFIsIsrYxft6T5Ekk9vN5GPGo3tBSN+5QjdjRg+Zg=")
            assertThat(fileLib).hasSize(2)
            assertThat(fileLib.get(0).digests.sha256).isNotEmpty()
            assertThat(fileLib.get(1).digests.sha256).isNotEmpty()

            val moduleDependenciesList = deps.moduleDependenciesList
            assertThat(moduleDependenciesList).hasSize(1)
            assertThat(moduleDependenciesList.first().moduleName).isEqualTo("base")
            // This collection should include every Library on which a Project has a direct dependency
            val directModuleDependencies = moduleDependenciesList.first().dependencyIndexList
                .map { depIndex -> libraryList[depIndex] }

            // 3 artifacts -- androidx.core, androidx.fragment, and androidx.collection
            assertThat(directModuleDependencies.filter { lib -> lib.hasMavenLibrary() }).hasSize(3)

            // 2 jars -- local.jar and local_in_lib.jar
            assertThat(directModuleDependencies.filter { lib -> !lib.hasMavenLibrary() }).hasSize(2)

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
