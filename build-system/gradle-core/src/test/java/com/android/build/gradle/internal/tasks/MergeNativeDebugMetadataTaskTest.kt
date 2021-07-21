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

package com.android.build.gradle.internal.tasks

import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [MergeNativeDebugMetadataTask].
 */
class MergeNativeDebugMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var x86NativeLib: File
    private lateinit var armeabiNativeLib: File
    private lateinit var outputFile: File

    @Before
    fun setUp() {
        // create input dirs with fake native debug metadata files
        val inputDir1 = temporaryFolder.newFolder("inputDir1")
        x86NativeLib = FileUtils.join(inputDir1, "x86", "foo.so.dbg")
        FileUtils.createFile(x86NativeLib, "foo")
        assertThat(x86NativeLib).exists()
        val inputDir2 = temporaryFolder.newFolder("inputDir2")
        armeabiNativeLib = FileUtils.join(inputDir2, "armeabi", "bar.so.dbg")
        FileUtils.createFile(armeabiNativeLib, "bar")
        assertThat(armeabiNativeLib).exists()

        outputFile = temporaryFolder.newFile()
    }

    @Test
    fun testBasic() {
        MergeNativeDebugMetadataTask.mergeFiles(listOf(x86NativeLib, armeabiNativeLib), outputFile)
        ZipFileSubject.assertThat(outputFile) {
            it.containsFileWithContent("x86/foo.so.dbg", "foo")
            it.containsFileWithContent("armeabi/bar.so.dbg", "bar")
        }
    }
}
