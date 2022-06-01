/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.OsType
import com.android.testutils.TestUtils
import org.junit.Rule
import org.junit.Test

class JavaCompileWithToolChainTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun basicTest() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
                org.gradle.java.installations.auto-detect=false
                org.gradle.java.installations.paths=${jdk8LocationInGradleFile}
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(8)
                    }
                }
            """.trimIndent()
        )

        var result = project.executor().withArgument("--info").run("assembleDebug")

        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Compiling with toolchain '${jdk8LocationFromStdout}'"
            )
        }

        TestFileUtils.searchAndReplace(
            project.gradlePropertiesFile,
            "org.gradle.java.installations.paths=${jdk8LocationInGradleFile}",
            "org.gradle.java.installations.paths=${jdk11LocationInGradleFile}"
        )

        TestFileUtils.searchAndReplace(
            project.buildFile,
            "languageVersion = JavaLanguageVersion.of(8)",
            "languageVersion = JavaLanguageVersion.of(11)"
        )

        result = project.executor().withArgument("--info").run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Compiling with toolchain '${jdk11LocationFromStdout}'"
            )
        }
    }

    companion object {
        enum class JdkVersion {
            JDK11,
            JDK8
        }

        private fun getPlatformSpecificJdkLocationSuffix(jdkVersion: JdkVersion): String {
            return when(OsType.getHostOs()) {
                OsType.LINUX -> "linux"
                OsType.WINDOWS ->
                    when(jdkVersion) {
                        JdkVersion.JDK8 -> "win64"
                        JdkVersion.JDK11 -> "win"
                    }
                OsType.DARWIN -> "mac/Contents/Home"
                else -> throw IllegalStateException("Unsupported operating system")
            }
        }

        private val jdk8Location =
            TestUtils.resolveWorkspacePath(
                "prebuilts/studio/jdk/${getPlatformSpecificJdkLocationSuffix(JdkVersion.JDK8)}")
                .toString()

        private val jdk11Location =
            TestUtils.resolveWorkspacePath(
                "prebuilts/studio/jdk/jdk11/${getPlatformSpecificJdkLocationSuffix(JdkVersion.JDK11)}")
                .toString()

        val jdk8LocationInGradleFile = jdk8Location.replace("\\", "/")
        val jdk11LocationInGradleFile = jdk11Location.replace("\\", "/")

        val jdk8LocationFromStdout = if (OsType.getHostOs() == OsType.WINDOWS) {
            jdk8Location.replace("/", "\\")
        } else {
            jdk8Location
        }

        val jdk11LocationFromStdout = if (OsType.getHostOs() == OsType.WINDOWS) {
            jdk11Location.replace("/", "\\")
        } else {
            jdk11Location
        }
    }
}
