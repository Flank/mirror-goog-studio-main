/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.cmake

import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

private const val RELATIVE_FILE_NAME = "CMakeLists.txt"
private const val ERROR_STRING =
    "CMake Error at %s:123:456. We had a reactor leak here now. Give us a few minutes to " +
            "lock it down. Large leak, very dangerous."

class MakeCmakeMessagePathsAbsolute {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun realWorld() {
        val errorString = "CMake Error at /Users/jomof/AndroidStudioProjects/MyApplication78" +
                "/app/src/main/cpp/CMakeLists.txt:13:"
        val makefile = temporaryFolder.newFile(RELATIVE_FILE_NAME)
        val actual = makeCmakeMessagePathsAbsolute(
            errorString,
            makefile.parentFile
        )
        Truth.assertThat(actual).isEqualTo(errorString)
    }

    @Test
    fun testMakefilePathCorrection() {
        val makefile = temporaryFolder.newFile(RELATIVE_FILE_NAME)
        val input = String.format(
            Locale.getDefault(),
            ERROR_STRING,
            RELATIVE_FILE_NAME
        )
        val expected = String.format(
            Locale.getDefault(),
            ERROR_STRING, makefile.absolutePath)
        val actual = makeCmakeMessagePathsAbsolute(
            input,
            makefile.parentFile
        )
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun testMakefilePathCorrectionForAbsolutePath() {
        val makefile = temporaryFolder.newFile(RELATIVE_FILE_NAME)
        val input = String.format(
            Locale.getDefault(),
            ERROR_STRING, makefile.absolutePath)
        val actual = makeCmakeMessagePathsAbsolute(
            input,
            makefile.parentFile
        )
        Assert.assertEquals(input, actual)
    }

    @Test
    fun testMakefilePathCorrectionForNonexistentFile() {
        val input = String.format(
            Locale.getDefault(),
            ERROR_STRING,
            RELATIVE_FILE_NAME
        )
        val actual = makeCmakeMessagePathsAbsolute(
            input,
            temporaryFolder.root
        )
        Assert.assertEquals(input, actual)
    }

    @Test
    fun testMakefilePathCorrectionOverMultipleLines() {
        val absoluteMakefile = temporaryFolder.newFile(RELATIVE_FILE_NAME)
        val otherErrorFile = "MissingErrorFile.txt"
        val ls = System.lineSeparator()
        val base = "$ERROR_STRING${ls}This line should not change at all$ls " +
                "$ERROR_STRING$ls$ls${ls}Another string that won't match the RegEx."

        val input = String.format(
            Locale.getDefault(), base, absoluteMakefile.name, otherErrorFile
        )
        val expected = String.format(
            Locale.getDefault(),
            base,
            absoluteMakefile.absolutePath,
            otherErrorFile
        )
        val actual = makeCmakeMessagePathsAbsolute(
            input,
            absoluteMakefile.parentFile
        )
        Assert.assertEquals(expected, actual)
    }
}