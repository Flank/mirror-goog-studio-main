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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.Scanner
import java.util.regex.Pattern

/**
 * Integration test to check that AGP isn't leaking lint class loaders, even when buildSrc changes.
 */
class LintClassLoaderTest {

    companion object {
        const val CREATING_LINT_CLASS_LOADER_LOG_LINE = "Android Lint: Creating lint class loader.*"
    }

    // Set a unique heapSize for each test to ensure each test uses a fresh gradle daemon without an
    // existing lint class loader.
    @get:Rule
    val project = createGradleTestProject("project", heapSize = "2001M")

    @Test
    fun testForSingleLintClassLoader() {
        val buildSrcBuildGradle = project.file("buildSrc/build.gradle")
        Files.createDirectory(buildSrcBuildGradle.toPath().parent)
        buildSrcBuildGradle.writeText(//language=groovy
            """
            plugins {
                id('java')
            }
        """.trimIndent())
        val buildSrcClass = project.file("buildSrc/src/main/java/com/example/MyPlugin.java")
        Files.createDirectories(buildSrcClass.toPath().parent)
        buildSrcClass.writeText(//language=java
            """
            package com.example;

            public class MyPlugin {
                public static int myConstant = 1;
            }
        """.trimIndent())

        // Check that we create exactly one lint class loader when running several lint tasks with
        // a new gradle daemon.
        val result = project.executor().withArgument("--info").run("lintDebug", "lintRelease")
        assertThat(result.stdout.countMatches(CREATING_LINT_CLASS_LOADER_LOG_LINE)).isEqualTo(1)
        buildSrcClass.writeText(buildSrcClass.readText().replace('1', '2'))
        val result2 = project.executor().withArgument("--info").run("clean","lintDebug")
        assertThat(result2.stdout.countMatches(CREATING_LINT_CLASS_LOADER_LOG_LINE)).isEqualTo(0)
    }
}

/**
 * Returns the number of times that string appears in the [Scanner]
 */
fun Scanner.countMatches(string: String): Int {
    val pattern = Pattern.compile(string)
    var count = 0
    while(hasNextLine()) {
        val line = nextLine()
        val matcher = pattern.matcher(line)
        while (matcher.find()) {
            count += 1
        }
    }
    return count
}

