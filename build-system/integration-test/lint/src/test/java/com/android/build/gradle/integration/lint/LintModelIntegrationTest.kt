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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import com.android.testutils.TestUtils
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.AssertionError
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.stream.Collectors

/** Integration test for the new lint models.  */
class LintModelIntegrationTest {

    @get:Rule
    val project: GradleTestProject =
        LintInvocationType.NEW_LINT_MODEL.testProjectBuilder(4)
            .fromTestProject("lintKotlin")
            .dontOutputLogOnFailure()
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun checkLintModels() {
        // Check lint runs correctly before asserting about the model.
        project.executor().expectFailure().run("clean", ":app:lintDebug")
        project.executor().expectFailure().run("clean", ":app:lintDebug")
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
            val expected = getExpectedModel(model.fileName.toString())
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
                    val fileToUpdate = TestUtils.getWorkspaceFile("tools/base/build-system/integration-test/lint/src/test/resources/com/android/build/gradle/integration/lint/kotlinmodel/lintDebug/${model.fileName}")
                    fileToUpdate.writeText(actual.joinToString("\n"))
                    errors += "Updated ${model.fileName} with \n$diff"
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw AssertionError(errors.joinToString("\n\n"))
        }
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
            put(
                "gradle=\"${Version.ANDROID_GRADLE_PLUGIN_VERSION}\"",
                "gradle=\"${"$"}androidGradlePluginVersion\"")
            put(File.separator, "/")
            put(File.pathSeparator, ":")
        })
    }


    private fun getExpectedModel(name: String): List<String> {
        val resource = Resources.getResource(this::class.java, "kotlinmodel/lintDebug/$name")
        return Resources.readLines(resource, StandardCharsets.UTF_8)
    }

}
