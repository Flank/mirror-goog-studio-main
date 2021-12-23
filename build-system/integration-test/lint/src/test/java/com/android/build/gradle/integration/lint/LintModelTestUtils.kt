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

package com.android.build.gradle.integration.lint

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.TestUtils
import com.google.common.io.Resources
import com.google.common.truth.Truth
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.stream.Collectors

fun checkLintModels(
    project: GradleTestProject,
    lintModelDir: Path,
    modelSnapshotResourceRelativePath: String,
    vararg expectedModelFiles: String
) {
    val models = Files.list(lintModelDir).use { stream -> stream.collect(Collectors.toList()) }

    Truth.assertThat(models.map { it.fileName.toString() }).containsExactly(
        *expectedModelFiles
    )

    val errors = mutableListOf<String>()
    val replacements = createReplacements(project)
    for (model in models) {
        val actual = Files.readAllLines(model).map { applyReplacements(it, replacements) }
        val expected = getExpectedModel("$modelSnapshotResourceRelativePath/${model.fileName}")
        if (actual != expected){
            val diff: String = TestUtils.getDiff(
                expected.toTypedArray(),
                actual.toTypedArray()
            )
            errors += if (System.getenv("GENERATE_MODEL_GOLDEN_FILES").isNullOrEmpty()) {
                "Unexpected lint model change for ${model.fileName}\n" +
                        "Run with env var GENERATE_MODEL_GOLDEN_FILES=true to regenerate\n" +
                        diff
            } else {
                val fileToUpdate = TestUtils.resolveWorkspacePath("tools/base/build-system/integration-test/lint/src/test/resources/com/android/build/gradle/integration/lint/$modelSnapshotResourceRelativePath/${model.fileName}")
                Files.write(fileToUpdate, actual)
                "Updated ${model.fileName} with \n$diff"
            }
        }
    }
    if (errors.isNotEmpty()) {
        throw AssertionError(errors.joinToString("\n\n"))
    }
}

private val cacheReplace = Regex("""/[a-zA-Z0-9]{32}/""")

private val localRepositories = GradleTestProject.localRepositories.map { it.toAbsolutePath().toString() }

fun applyReplacements(original: String, replacements: Map<String, String>): String {
    var normalized = original
    replacements.forEach { (from, to) -> normalized = normalized.replace(from, to) }
    return normalized.replace(cacheReplace, "/<digest>/")
}

fun createReplacements(project: GradleTestProject): Map<String, String> {
    return Collections.unmodifiableMap(mutableMapOf<String, String>().apply {
        put(project.location.projectDir.absolutePath, "${"$"}{projectDir}")
        put(project.location.testLocation.androidSdkHome.absolutePath, "${"$"}{androidSdkUserHome}")
        put(project.androidSdkDir!!.absolutePath, "${"$"}{androidSdkDir}")
        put(project.location.testLocation.gradleCacheDir.absolutePath, "${"$"}{gradleCacheDir}")
        put(project.location.testLocation.gradleUserHome.toAbsolutePath().toString(), "${"$"}{gradleUserHome}")
        put("android-${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}",
            "android-${"$"}{androidHighestKnownStableApi}")
        put("""targetSdkVersion="${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}"""",
            """targetSdkVersion="${"$"}{androidHighestKnownStableApi}"""")
        for (repository in localRepositories) {
            put(repository, "${"$"}{mavenRepo}")
        }
        put(Version.ANDROID_GRADLE_PLUGIN_VERSION, "${"$"}androidGradlePluginVersion")
        put(File.separator, "/")
        put(File.pathSeparator, ":")
        put("kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib:${"$"}{kotlinVersion}")
        put("kotlin-stdlib/${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib/${"$"}{kotlinVersion}")
        put("kotlin-stdlib-${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib-${"$"}{kotlinVersion}")
        put("kotlin-stdlib-common:${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib-common:${"$"}{kotlinVersion}")
        put("kotlin-stdlib-common/${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib-common/${"$"}{kotlinVersion}")
        put("kotlin-stdlib-common-${TestUtils.KOTLIN_VERSION_FOR_TESTS}", "kotlin-stdlib-common-${"$"}{kotlinVersion}")
    })
}

private fun getExpectedModel(name: String): List<String> {
    val resource = Resources.getResource(LintModelIntegrationTest::class.java, name)
    return Resources.readLines(resource, StandardCharsets.UTF_8)
}
