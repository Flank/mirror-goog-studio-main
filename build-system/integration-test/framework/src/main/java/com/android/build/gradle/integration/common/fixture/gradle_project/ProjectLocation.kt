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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import java.io.File

class ProjectLocation(
    /**
     * The location of the project.
     */
    val projectDir: File,

    /**
     * The test location info
     */
    val testLocation: TestLocation
) {

    /**
     * Creates a [ProjectLocation] for a sub-project.
     *
     * @param gradlePath a gradle path relative to the current project.
     */
    fun createSubProjectLocation(gradlePath: String): ProjectLocation {
        val newDir = File(projectDir, gradlePath.replace(":", "/"))
        return ProjectLocation(
            newDir,
            testLocation
        )
    }
}

internal fun initializeProjectLocation(
    testClass: Class<*>,
    methodName: String?,
    projectName: String
): ProjectLocation {
    val testLocation = initializeTestLocation()

    val testDir = computeTestDir(testLocation.testsDir, testClass, methodName, projectName)

    return ProjectLocation(
        testDir,
        testLocation
    )
}

private fun computeTestDir(
    testOutDir: File,
    testClass: Class<*>,
    methodName: String?,
    projectName: String
): File {
    var testDir = testOutDir
    if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS
        && System.getenv("BUILDBOT_BUILDERNAME") == null
    ) {
        // On Windows machines, make sure the test directory's path is short enough to avoid
        // running into path too long exceptions. Typically, on local Windows machines,
        // OUT_DIR's path is long, whereas on Windows build bots, OUT_DIR's path is already
        // short (see https://issuetracker.google.com/69271554).
        // In the first case, let's move the test directory close to root (user home), and in
        // the second case, let's use OUT_DIR directly.
        var outDir = FileUtils.join(System.getProperty("user.home"), "android-tests")

        // when sharding is on, use a private sharded folder as tests can be split along
        // shards and files will get locked on Windows.
        if (System.getenv("TEST_TOTAL_SHARDS") != null) {
            outDir = FileUtils.join(outDir, System.getenv("TEST_SHARD_INDEX"))
        }

        testDir = File(outDir)
    }

    var classDir = testClass.simpleName
    var methodDir: String? = null

    // Create separate directory based on test method name if @Rule is used.
    // getMethodName() is null if this rule is used as a @ClassRule.
    if (methodName != null) {
        methodDir = methodName.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    // In Windows, make sure we do not exceed the limit for test class / name size.
    if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        var totalLen = classDir.length
        if (methodDir != null) {
            totalLen += methodDir.length
        }
        if (totalLen > GradleTestProject.MAX_TEST_NAME_DIR_WINDOWS) {
            val hash = Hashing.sha1()
                .hashString(classDir + methodDir, Charsets.US_ASCII)
                .toString()
            // take the first 10 characters of the method name, hopefully it will be enough
            // to disambiguate tests, and hash the rest.
            val testIdentifier = methodDir ?: classDir
            classDir =
                (testIdentifier.substring(0, Math.min(testIdentifier.length, 10))
                        + hash.substring(
                    0,
                    Math.min(
                        hash.length,
                        GradleTestProject.MAX_TEST_NAME_DIR_WINDOWS - 10
                    )
                ))
            methodDir = null
        }
    }
    testDir = File(testDir, classDir)
    if (methodDir != null) {
        testDir = File(testDir, methodDir)
    }
    return File(testDir, projectName)
}
