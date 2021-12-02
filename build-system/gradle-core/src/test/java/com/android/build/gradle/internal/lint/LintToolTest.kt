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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.testutils.truth.PathSubject.assertThat

import org.junit.Test

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class LintToolTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun initializeLintCacheDir() {
        val cacheDir = temporaryFolder.newFolder("lint-cache").toPath()

        val exampleCacheFile = cacheDir.resolve("exampleCacheFile.txt").also { Files.write(it, "content1".toByteArray()) }

        val lintTool = FakeObjectFactory.factory.newInstance(LintTool::class.java)

        lintTool.versionKey.set("version 1")
        lintTool.lintCacheDirectory.fileValue(cacheDir.toFile())

        assertThat(exampleCacheFile).hasContents("content1")
        // Check that initialization with no version present clears the directory
        lintTool.initializeLintCacheDir()
        assertThat(exampleCacheFile).doesNotExist()

        Files.write(exampleCacheFile, "content2".toByteArray())

        // Check that initializing with the same version doesn't clear the directory
        assertThat(exampleCacheFile).hasContents("content2")
        lintTool.initializeLintCacheDir()
        assertThat(exampleCacheFile).hasContents("content2")


        // Check that initializing with a different version clears the directory
        lintTool.versionKey.set("version 2")
        lintTool.initializeLintCacheDir()
        assertThat(exampleCacheFile).doesNotExist()

    }
}
