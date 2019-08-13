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

package com.android.build.gradle.tasks

import com.google.common.truth.Truth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Assert.fail

import com.android.builder.utils.FileCache
import com.android.utils.FileUtils
import java.io.File
import java.io.IOException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Unit test for [CleanBuildCache].  */
class CleanBuildCacheTest {

    @Rule
    @JvmField
    var testDir = TemporaryFolder()

    @Test
    @Throws(IOException::class)
    fun test() {
        val projectDir = testDir.newFolder()
        val buildCacheDir = testDir.newFolder()

        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        FileUtils.mkdirs(File(buildCacheDir, "some_cache_entry"))

        val task = project.tasks.create("cleanBuildCache", CleanBuildCache::class.java)
        try {
            task.clean()
            fail("Expected Exception when buildCache is not initialized.")
        } catch (exception: Exception) {
            assertThat(exception.message).contains("buildCache")
        }

        task.setBuildCache(FileCache.getInstanceWithMultiProcessLocking(buildCacheDir))
        task.clean()
        assertThat(buildCacheDir).doesNotExist()

        // Clean one more time to see if any exception occurs when buildCacheDir does not exist
        task.clean()
        assertThat(buildCacheDir).doesNotExist()
    }
}
