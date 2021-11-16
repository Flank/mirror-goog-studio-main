/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.testutils.MavenRepoGenerator
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CheckJetifierTaskTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(setUpTestProject()).create()

    private fun setUpTestProject(): TestProject {
        val app = MinimalSubProject.app()
        val lib = MinimalSubProject.lib()
        return MultiModuleTestProject.builder()
            .subproject(app)
            .subproject(lib)
            .dependency("implementation", app, lib)
            .build()
    }

    private lateinit var resultFile: File

    @Before
    fun setUp() {
        resultFile = project.buildDir.resolve("result.json")
    }

    private fun addMavenRepo() {
        val mavenRepo = project.projectDir.resolve("mavenRepo")
        FileUtils.mkdirs(mavenRepo)
        MavenRepoGenerator(
            listOf(
                MavenRepoGenerator.Library(
                    "example:A:1.0",
                    "example:B:1.0",
                ),
                MavenRepoGenerator.Library(
                    "example:B:1.0",
                    "com.android.support:support-annotations:$SUPPORT_LIB_VERSION",
                ),
            )
        ).generate(mavenRepo.toPath())
    }

    private fun addSupportLibDependencies() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
            repositories {
                maven { url '../mavenRepo' }
            }
            dependencies {
                implementation 'example:A:1.0' // `A` transitively depends on a support library
                implementation 'com.android.support:collections:$SUPPORT_LIB_VERSION'
            }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("lib").buildFile,
            """
            repositories {
                maven { url '../mavenRepo' }
            }
            dependencies {
                implementation 'example:B:1.0' // B directly depends on a support library
                // Add the same dependency in app to check if the task can handle duplicates
                implementation 'com.android.support:collections:$SUPPORT_LIB_VERSION'
            }
            """.trimIndent()
        )
    }

    private fun runCheckJetifier() {
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .with(StringOption.IDE_CHECK_JETIFIER_RESULT_FILE, resultFile.path)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("checkJetifier")
    }

    @Test
    fun `support library dependencies present`() {
        addMavenRepo()
        addSupportLibDependencies()

        runCheckJetifier()
        checkSupportLibDetected()
    }

    @Test
    fun `support library dependencies not present`() {
        runCheckJetifier()

        val result = CheckJetifierResult.load(resultFile)
        assertThat(result.getDisplayString()).isEqualTo("")
    }

    @Test
    fun testConfigurationsThatExtendAreResolvedFirst() {
        addMavenRepo()
        addSupportLibDependencies()

        project.getSubproject("app").buildFile.appendText(
            """

            configurations {
                aaa {
                    canBeResolved(true)
                }
            }
            afterEvaluate {
                Configuration c = configurations.getByName("aaa")
                if (c.name >= "debugRuntimeClasspath" || c.name >= "debugAndroidTestRuntimeClasspath") {
                    throw new RuntimeException(
                        "This name should be less than AGP's, it s important for this test."
                    )
                }
                c.extendsFrom(configurations.getByName("debugRuntimeClasspath"))
                c.extendsFrom(configurations.getByName("debugAndroidTestRuntimeClasspath"))
            }
        """.trimIndent()
        )
        runCheckJetifier()
        checkSupportLibDetected()
    }

    private fun checkSupportLibDetected() {
        val result = CheckJetifierResult.load(resultFile)
        assertThat(result.getDisplayString()).isEqualTo(
            """
            example:A:1.0 (Project ':app', configuration 'debugAndroidTestCompileClasspath' -> example:A:1.0 -> example:B:1.0 -> com.android.support:support-annotations:28.0.0)
            com.android.support:collections:28.0.0 (Project ':app', configuration 'debugAndroidTestCompileClasspath' -> com.android.support:collections:28.0.0)
            example:B:1.0 (Project ':lib', configuration 'debugAndroidTestCompileClasspath' -> example:B:1.0 -> com.android.support:support-annotations:28.0.0)
            """.trimIndent()
        )
    }
}
