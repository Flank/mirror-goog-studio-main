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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class ResourcesOverridingTest(private val precompileDependenciesResources: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "precompileDependenciesResources_{0}")
        @JvmStatic
        fun params() = listOf(
            arrayOf(true),
            arrayOf(false)
        )
    }

    private val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
        .withFile(
            "src/main/res/raw/shared_between_app_and_local_lib",
            "fromPublishedLib"
        )
        .withFile(
            "src/main/res/raw/shared_between_local_and_published_lib",
            "fromPublishedLib"
        )

    private val localLib = MinimalSubProject.lib("com.example.localLib")
        .withFile(
            "src/main/res/raw/shared_between_local_and_published_lib",
            "fromLocalLib"
        )
        .withFile(
            "src/main/res/raw/shared_between_app_and_local_lib",
            "fromLocalLib"
        )

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/raw/shared_between_app_and_local_lib",
            "fromApp"
        )
        .withFile(
            "src/main/res/raw/shared_between_app_and_local_lib",
            "fromApp"
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":publishedLib", publishedLib)
            .subproject(":localLib", localLib)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    /**
     * app -> localLib -> publishedLib
     */
    @Test
    fun testLocalLibraryDependingOnRemoteLibrary() {
        TestFileUtils.appendToFile(
            project.getSubproject("localLib").buildFile,
            """
                repositories {flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies { implementation name: 'publishedLib-release', ext:'aar' }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                repositories {flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies { api project(':localLib') }
            """.trimIndent()
        )

        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":publishedLib:assembleRelease")
        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":app:assembleDebug")

        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_app_and_local_lib",
                "fromApp"
            )
        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_local_and_published_lib",
                "fromLocalLib"
            )
    }

    /**
     * app -> localLib
     *     -> publishedLib
     */
    @Test
    fun testAppDependingOnLocalLibraryAndRemoteLibrary() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                repositories {flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies {
                    implementation project(':localLib')
                    implementation name: 'publishedLib-release', ext:'aar'
                }
            """.trimIndent()
        )

        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":publishedLib:assembleRelease")
        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":app:assembleDebug")

        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_app_and_local_lib",
                "fromApp"
            )
        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_local_and_published_lib",
                "fromLocalLib"
            )
    }

    /**
     * app -> publishedLib
     *     -> localLib
     */
    @Test
    fun testAppDependingOnRemoteLibraryAndLocalLibrary() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                dependencies {
                    implementation name: 'publishedLib-release', ext:'aar'
                    implementation project(':localLib')
                }
            """.trimIndent()
        )

        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":publishedLib:assembleRelease")
        project.executor()
            .with(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES, precompileDependenciesResources)
            .run(":app:assembleDebug")

        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_app_and_local_lib",
                "fromApp"
            )
        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_local_and_published_lib",
                "fromPublishedLib"
            )
        assertThatApk(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent(
                "res/raw/shared_between_app_and_local_lib",
                "fromApp"
            )
    }
}
