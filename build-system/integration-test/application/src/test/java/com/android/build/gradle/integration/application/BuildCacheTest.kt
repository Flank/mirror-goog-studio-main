/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.utils.FileUtils
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Integration test for build cache.  */
class BuildCacheTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        // Add a dependency on an external library (guava)
        TestFileUtils.appendToFile(
            project.buildFile,
            """|
               |dependencies {
               |    compile 'com.google.guava:guava:18.0'
               |}
               |""".trimMargin("|")
        )
    }

    @Test
    fun testBuildCacheEnabled() {
        val sharedBuildCacheDir = FileUtils.join(project.testDir, "shared", "build-cache")
        val privateBuildCacheDir = File(sharedBuildCacheDir, Version.ANDROID_GRADLE_PLUGIN_VERSION)

        // Make sure the parent directory of the shared build cache directory does not yet exist.
        // This is to test that the locking mechanism used by the build cache can work with
        // non-existent directories (and parent directories).
        assertThat(sharedBuildCacheDir.parentFile).doesNotExist()

        val executor = project.executor()
            .with(BooleanOption.ENABLE_BUILD_CACHE, true)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(StringOption.BUILD_CACHE_DIR, sharedBuildCacheDir.absolutePath)
        executor.run("clean", "assembleDebug")

        var cachedEntryDirs = privateBuildCacheDir.listFiles()
            .filter{ it.isDirectory } // Remove the lock files
            .filter { f -> !containsAapt(f) } // Remove aapt2 cache
            .toList()

        // only guava should be cached
        assertThat(cachedEntryDirs).hasSize(1)

        // Check the timestamps of the guava library's cached file to make
        // sure we actually copied one to the other and did not run pre-dexing twice to create the
        // two files
        val cachedGuavaDexFile = File(cachedEntryDirs[0], "output")
        val cachedGuavaTimestamp = cachedGuavaDexFile.lastModified()

        executor.run("clean", "assembleDebug")

        cachedEntryDirs = privateBuildCacheDir.listFiles()
            .filter{ it.isDirectory } // Remove the lock files
            .filter { f -> !containsAapt(f) } // Remove aapt2 cache
            .toList()
        assertThat(cachedEntryDirs).hasSize(1)
        // Assert that the cached file is unchanged
        assertThat(cachedGuavaDexFile).wasModifiedAt(cachedGuavaTimestamp)

        executor.run("cleanBuildCache")
        assertThat(sharedBuildCacheDir).exists()
        assertThat(privateBuildCacheDir).doesNotExist()
    }

    @Test
    fun testBuildCacheDisabled() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """|
               |android.defaultConfig.minSdkVersion $SUPPORT_LIB_MIN_SDK
               |
               |dependencies {
               |    compile "com.android.support:support-v13:${"$"}{rootProject.supportLibVersion}"
               |}
               |""".trimMargin("|")
        )

        val sharedBuildCacheDir = FileUtils.join(project.testDir, "shared", "build-cache")
        val privateBuildCacheDir = File(sharedBuildCacheDir, Version.ANDROID_GRADLE_PLUGIN_VERSION)
        assertThat(sharedBuildCacheDir.parentFile).doesNotExist()

        project.executor()
            .with(BooleanOption.ENABLE_BUILD_CACHE, false)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(StringOption.BUILD_CACHE_DIR, sharedBuildCacheDir.absolutePath)
            .run("clean", "assembleDebug")

        assertThat(sharedBuildCacheDir).doesNotExist()
        assertThat(privateBuildCacheDir).doesNotExist()
    }

    private fun containsAapt(dir: File): Boolean {
        return if (dir.isFile) {
            dir.name.contains("libaapt2_jni")
        } else {
            dir.listFiles().any{ containsAapt(it) }
        }
    }
}

