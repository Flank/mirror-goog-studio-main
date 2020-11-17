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
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Path

/**
 * The location for test-related files.
 */
class TestLocation(
    /**
     * The build directory of the target that that built the test.
     *
     * This is mostly used to compute a few things like gradle user home, jacoco, etc...
     */
    val buildDir: File,

    /**
     * the root project for all the tests files (for all tests)
     */
    val testsDir: File,

    /**
     * An SDK location directly under [buildDir]
     *
     * FIXME figure this out
     */
    val androidSdkHome: File,

    /**
     * Gradle user home folder for this test
     */
    val gradleUserHome: Path,

    /**
     * the location of the Gradle cache directory
     */
    val gradleCacheDir: File
)

fun initializeTestLocation() : TestLocation {
    val buildDir = when {
        System.getenv("TEST_TMPDIR") != null -> {
            File(System.getenv("TEST_TMPDIR"))
        }
        else -> {
            throw IllegalStateException("unable to determine location for BUILD_DIR")
        }
    }

    val outDir = File(buildDir, "tests")

    val gradleUserHome = getGradleUserHome(buildDir)

    return TestLocation(
        buildDir,
        outDir,
        File(buildDir, "ANDROID_SDK_HOME"),
        gradleUserHome,
        FileUtils.join(gradleUserHome.toFile(), "caches", "transforms-3")
    )
}

private fun getGradleUserHome(buildDir: File): Path {
    if (TestUtils.runningFromBazel()) {
        return BazelIntegrationTestsSuite.GRADLE_USER_HOME
    }
    // Use a temporary directory, so that shards don't share daemons. Gradle builds are not
    // hermetic anyway and Gradle does not clean up test runfiles, so use the same home
    // across invocations to save disk space.
    var gradleUserHome = buildDir.toPath().resolve("GRADLE_USER_HOME")
    val worker = System.getProperty("org.gradle.test.worker")
    if (worker != null) {
        gradleUserHome = gradleUserHome.resolve(worker)
    }
    return gradleUserHome
}

