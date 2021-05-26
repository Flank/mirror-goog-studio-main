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
package com.android.build.gradle.integration.lint

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.stream.Collectors

/** Integration test for the new lint models.  */
@RunWith(FilterableParameterized::class)
class LintModelIntegrationTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withConfigurationCacheMaxProblems(4)
            .fromTestProject("lintKotlin")
            .dontOutputLogOnFailure()
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun checkLintModels() {
        // Check lint runs correctly before asserting about the model.
        getExecutor().expectFailure().run(":app:cleanLintDebug", ":app:lintDebug")
        getExecutor().expectFailure().run(":app:cleanLintDebug", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("8 errors, 6 warnings")

        val lintModelDir =
            project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintDebug")

        val models = Files.list(lintModelDir).use { stream -> stream.collect(Collectors.toList()) }

        assertThat(models.map { it.fileName.toString() }).containsExactly(
            "debug-androidTestArtifact-dependencies.xml",
            "debug-androidTestArtifact-libraries.xml",
            "debug-mainArtifact-dependencies.xml",
            "debug-mainArtifact-libraries.xml",
            "debug-testArtifact-dependencies.xml",
            "debug-testArtifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )

        val errors = mutableListOf<String>()
        val replacements = createReplacements(project.location)
        for (model in models) {
            val actual = Files.readAllLines(model).map { applyReplacements(it, replacements) }
            val expected = getExpectedModel(model.fileName.toString(), usePartialAnalysis)
            if (actual != expected){
                val diff: String = TestUtils.getDiff(
                    expected.toTypedArray(),
                    actual.toTypedArray()
                )
                if (System.getenv("GENERATE_MODEL_GOLDEN_FILES").isNullOrEmpty()) {
                    errors += "Unexpected lint model change for ${model.fileName}\n" +
                                "Run with env var GENERATE_MODEL_GOLDEN_FILES=true to regenerate\n" +
                                diff
                } else {
                    val fileToUpdate = TestUtils.resolveWorkspacePath("tools/base/build-system/integration-test/lint/src/test/resources/com/android/build/gradle/integration/lint/kotlinmodel/lintDebug/usePartialAnalysis_$usePartialAnalysis/${model.fileName}")
                    Files.write(fileToUpdate, actual)
                    errors += "Updated ${model.fileName} with \n$diff"
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw AssertionError(errors.joinToString("\n\n"))
        }
    }

    @Test
    fun checkLintModelsForShrinkable() {
        // Test should pass whether using partial analysis or not, but assume true to save time.
        Assume.assumeTrue(usePartialAnalysis)
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                        }
                    }
                }
            """.trimIndent()
        )
        // Check lint runs correctly before asserting about the model.
        getExecutor().expectFailure().run(":app:cleanLintDebug", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("8 errors, 6 warnings")

        val lintModelDir =
            project.getSubproject("app")
                .intermediatesDir.toPath()
                .resolve("incremental/lintDebug")
                .toFile()

        val projectModelFile = File(lintModelDir, "module.xml")
        assertThat(projectModelFile).isFile()
        assertThat(
            Files.readAllLines(projectModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project.location)) }
                .none { it.contains("neverShrinking") }
        ).isTrue()

        val variantModelFile = File(lintModelDir, "debug.xml")
        assertThat(variantModelFile).isFile()
        assertThat(
            Files.readAllLines(variantModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project.location)) }
                .any { it.contains("shrinking=\"true\"") }
        ).isTrue()
    }

    private val cacheReplace = Regex("""/[a-zA-Z0-9]{32}/""")

    private val localRepositories = GradleTestProject.localRepositories.map { it.toAbsolutePath().toString() }

    private fun applyReplacements(original: String, replacements: Map<String, String>): String {
        var normalized = original
        replacements.forEach { (from, to) -> normalized = normalized.replace(from, to) }
        return normalized.replace(cacheReplace, "/<digest>/")
    }

    private fun createReplacements(projectLocation: ProjectLocation): Map<String, String> {
        return Collections.unmodifiableMap(mutableMapOf<String, String>().apply {
            put(projectLocation.projectDir.absolutePath, "${"$"}{projectDir}")
            put(projectLocation.testLocation.androidSdkHome.absolutePath, "${"$"}{androidSdkUserHome}")
            put(project.androidSdkDir!!.absolutePath, "${"$"}{androidSdkDir}")
            put(projectLocation.testLocation.gradleCacheDir.absolutePath, "${"$"}{gradleCacheDir}")
            put(projectLocation.testLocation.gradleUserHome.toAbsolutePath().toString(), "${"$"}{gradleUserHome}")
            for (repository in localRepositories) {
                put(repository, "${"$"}{mavenRepo}")
            }
            put(Version.ANDROID_GRADLE_PLUGIN_VERSION, "${"$"}androidGradlePluginVersion")
            put(File.separator, "/")
            put(File.pathSeparator, ":")
        })
    }


    private fun getExpectedModel(name: String, usePartialAnalysis: Boolean): List<String> {
        val resource =
            Resources.getResource(
                this::class.java,
                "kotlinmodel/lintDebug/usePartialAnalysis_$usePartialAnalysis/$name"
            )
        return Resources.readLines(resource, StandardCharsets.UTF_8)
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
