/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.gradle_project

import com.android.build.gradle.integration.BazelIntegrationTestsSuite
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.TestUtils
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The build system under which the integration tests are running.
 */
internal enum class BuildSystem {
    GRADLE {
        override val localRepositories: List<Path>
            get() {
                val customRepo = System.getenv(GradleTestProject.ENV_CUSTOM_REPO)
                // TODO: support USE_EXTERNAL_REPO
                val repos = ImmutableList.builder<Path>()
                for (path in Splitter.on(File.pathSeparatorChar).split(customRepo)) {
                    repos.add(Paths.get(path))
                }
                return repos.build()
            }
    },
    BAZEL {
        override val localRepositories: List<Path>
            get() = BazelIntegrationTestsSuite.MAVEN_REPOS
    },
    ;

    abstract val localRepositories: List<Path>

    fun getCommonBuildScriptContent(
        withAndroidGradlePlugin: Boolean,
        withKotlinGradlePlugin: Boolean,
        withDeviceProvider: Boolean
    ): String {
        val script = StringBuilder()
        script.append("def commonScriptFolder = buildscript.sourceFile.parent\n")
        script.append(
            "apply from: \"\$commonScriptFolder/commonVersions.gradle\", to: rootProject.ext\n\n"
        )
        script.append("project.buildscript { buildscript ->\n")
        script.append(
            "    apply from: \"\$commonScriptFolder/commonLocalRepo.gradle\", to:buildscript\n"
        )
        if (withKotlinGradlePlugin) {
            // To get the Kotlin version
            script.append("    apply from: '../commonHeader.gradle'\n")
        }
        script.append("    dependencies {\n")
        if (withAndroidGradlePlugin) {
            script.append(
                "        classpath \"com.android.tools.build:gradle:\$rootProject.buildVersion\"\n"
            )
        }
        if (withKotlinGradlePlugin) {
            script.append(
                "        classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:\$rootProject.kotlinVersion\"\n"
            )
        }
        if (withDeviceProvider) {
            script.append(
                "        classpath 'com.android.tools.internal.build.test:devicepool:0.1'\n"
            )
        }
        script.append("    }\n")
        script.append("}")
        return script.toString()
    }

    companion object {
        fun get(): BuildSystem {
            return when {
                TestUtils.runningFromBazel() -> {
                    BAZEL
                }
                System.getenv(GradleTestProject.ENV_CUSTOM_REPO) != null -> {
                    GRADLE
                }
                else -> throw IllegalStateException("Tests must be run from the build system")
            }
        }
    }
}
