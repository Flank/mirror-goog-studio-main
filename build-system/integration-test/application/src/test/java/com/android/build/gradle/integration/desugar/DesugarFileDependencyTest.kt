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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.desugar.resources.ImplOfInterfaceWithDefaultMethod
import com.android.build.gradle.integration.desugar.resources.InterfaceWithDefaultMethod
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestInputsGenerator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files

/** Test desugaring for file dependencies, which lack the metadata for artifact transforms.  */
@RunWith(FilterableParameterized::class)
class DesugarFileDependencyTest(var tool: Tool) {

    enum class Tool {
        D8_WITH_ARTIFACT_TRANSFORMS,
        D8_WITHOUT_ARTIFACT_TRANSFORMS,
        R8,
        DESUGAR
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getParams(): Array<Tool> = Tool.values()
    }

    private val mainProject = MinimalSubProject.app("com.example.app").apply {
        appendToBuild("""
                android {
                    compileOptions {
                        sourceCompatibility 1.8
                        targetCompatibility 1.8
                    }
                }
                dependencies {
                    implementation files('libs/interface.jar', 'libs/impl.jar')
                }
                """.trimIndent()
        )
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(mainProject).create()

    @Before
    fun setUp() {
        if (tool == Tool.R8) {
            configureR8Desugaring(project)
        }
        addJars()
    }

    @Test
    fun checkBuilds() {
        executor().run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val assertThatClass = assertThat(apk)
                .hasClass("Lcom/android/build/gradle/integration/desugar/resources/ImplOfInterfaceWithDefaultMethod;")
                .that()
            if (tool == Tool.D8_WITH_ARTIFACT_TRANSFORMS) {
                //TODO(b/128599004): fix this case
                // This class will not be correctly desugared in the D8_WITH_ARTIFACT_TRANSFORMS
                // case with the current implementation, as ImplOfInterfaceWithDefaultMethod
                // will be desugared in an artifact transform without reference to the library
                // containing InterfaceWithDefaultMethod, and so will not be modified to have the
                // default method implementation.
                assertThatClass.doesNotHaveMethod("myDefaultMethod")
            } else {
                assertThatClass.hasMethod("myDefaultMethod")
            }
        }
    }

    private fun addJars() {
        val libs = project.file("libs").toPath()
        Files.createDirectory(libs)
        val interfaceLib = libs.resolve("interface.jar")
        val implLib = libs.resolve("impl.jar")

        TestInputsGenerator.pathWithClasses(interfaceLib, listOf(
            InterfaceWithDefaultMethod::class.java))
        TestInputsGenerator.pathWithClasses(implLib, listOf(
            ImplOfInterfaceWithDefaultMethod::class.java))

    }

    private fun executor(): GradleTaskExecutor {
        val enableD8Desugaring =
            tool == Tool.D8_WITH_ARTIFACT_TRANSFORMS || tool == Tool.D8_WITHOUT_ARTIFACT_TRANSFORMS
        return project.executor()
            .with(BooleanOption.ENABLE_D8_DESUGARING, enableD8Desugaring)
            .with(BooleanOption.ENABLE_R8, tool == Tool.R8)
            .with(BooleanOption.ENABLE_R8_DESUGARING, tool == Tool.R8)
            .with(
                BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM,
                tool == Tool.D8_WITH_ARTIFACT_TRANSFORMS
            )
    }

}
