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

package com.android.build.gradle.internal.cxx.configure

import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.CompileCommandsCodecTest
import com.android.build.gradle.internal.cxx.StructuredLog
import com.android.build.gradle.internal.cxx.logging.text
import com.android.testutils.TestResources
import com.android.utils.cxx.CxxDiagnosticCode.COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND
import com.android.utils.cxx.CxxDiagnosticCode.OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME
import com.android.utils.cxx.compileCommandsFileIsCurrentVersion
import com.android.utils.cxx.streamCompileCommands
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class CompileCommandsTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    lateinit var structuredLog : StructuredLog

    private fun testFile(testFileName: String): File {
        val resourceFolder = "/com/android/build/gradle/external/compile_commands/"
        return TestResources.getFile(
            CompileCommandsCodecTest::class.java, resourceFolder + testFileName
        )
    }

    @Before
    fun before() {
        structuredLog = StructuredLog(tempFolder)
    }

    private fun createCompileCommandsFile(
        objectFile : String = "Externals/cpp-optparse/CMakeFiles/cpp-optparse.dir/OptionParser.cpp.o",
        objectFlag : String = "-o $objectFile"
    ) : File {
        val result = tempFolder.newFolder().resolve("compile_commands.json")
        result.writeText(
            """
            [{
              "directory": "directory",
              "command": "clang $objectFlag -c OptionParser.cpp",
              "file": "OptionParser.cpp"
            }]
        """.trimIndent())
        return result
    }

    private fun createCompileCommandsBinFile() : File {
        val result = tempFolder.newFolder().resolve("compile_commands.json.bin")
        result.delete()
        return result
    }

    private fun assertInvalidObjectFileNameHandled(objectFile : String) {
        val json = createCompileCommandsFile(objectFile)
        val bin = createCompileCommandsBinFile()
        structuredLog.clear()
        convertCMakeToCompileCommandsBin(json, bin)
        structuredLog.assertError(OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME)

    }

    private fun assertTargetNameFromObjectFile(objectFile : String, expectTarget : String) {
        val json = createCompileCommandsFile(objectFile)
        val bin = createCompileCommandsBinFile()
        structuredLog.clear()
        convertCMakeToCompileCommandsBin(json, bin)
        var count = 0
        streamCompileCommands(bin) {
            assertThat(target).isEqualTo(expectTarget)
            ++count
        }
        structuredLog.assertNoErrors()
        assertThat(count).isEqualTo(1)
    }


    @Test
    fun `basic test`() {
        val json = createCompileCommandsFile()
        val bin = createCompileCommandsBinFile()
        convertCMakeToCompileCommandsBin(json, bin)
        assertThat(bin.isFile).isTrue()
        structuredLog.assertNoErrors()
    }

    @Test
    fun `bin has same time stamp as json`() {
        val json = createCompileCommandsFile()
        val bin = createCompileCommandsBinFile()
        convertCMakeToCompileCommandsBin(json, bin)
        assertThat(bin.lastModified()).isEqualTo(json.lastModified())
        structuredLog.assertNoErrors()
    }

    private fun convertWindowsLikePathToFile(path : String) : File {
        if (SdkConstants.currentPlatform() != SdkConstants.PLATFORM_WINDOWS) {
            return File(path.replace("\\", "/"))
        }
        return File(path)
    }

    @Test
    fun `bug 196847363 Windows compile_command JSON uses mixed slashes`() {
        val json = tempFolder.newFolder().resolve("compile_commands.json")
        testFile("windows_compile_commands.json").copyTo(json)
        val bin = tempFolder.newFolder().resolve("compile_commands.json.bin")
        convertCMakeToCompileCommandsBin(json, bin, SdkConstants.PLATFORM_WINDOWS, ::convertWindowsLikePathToFile)

        val distinctFlags = mutableSetOf<List<String>>()
        var count = 0
        streamCompileCommands(bin) {
            distinctFlags.add(flags)
            ++count
        }
        assertThat(distinctFlags).hasSize(38)
        assertThat(count).isEqualTo(612)
        structuredLog.assertNoErrors()
    }

    @Test
    fun `no-op when up-to-date`() {
        val json = createCompileCommandsFile()
        val bin = createCompileCommandsBinFile()

        // Create first and set timestamps to a known value
        convertCMakeToCompileCommandsBin(json, bin)
        json.setLastModified(100L)
        bin.setLastModified(100L)

        // Creating again should be no-op
        assertThat(bin.lastModified()).isEqualTo(100)

        structuredLog.assertNoErrors()
    }

    @Test
    fun `old bin version is discarded`() {
        val json = createCompileCommandsFile()
        val bin = createCompileCommandsBinFile()

        convertCMakeToCompileCommandsBin(json, bin)

        // Copy in a v1 compile_commands.json.bin and set the timestamp
        // so that it looks valid.
        Files.delete(bin.toPath())
        testFile("version_1_compile_commands.json.bin").copyTo(bin)
        bin.setLastModified(json.lastModified())
        assertThat(compileCommandsFileIsCurrentVersion(bin)).isFalse()

        // Convert again, the V1 file should be replaced with current version
        convertCMakeToCompileCommandsBin(json, bin)
        assertThat(compileCommandsFileIsCurrentVersion(bin)).isTrue()

        structuredLog.assertNoErrors()
    }

    @Test
    fun `garbage bin is discarded`() {
        val json = createCompileCommandsFile()
        val bin = createCompileCommandsBinFile()

        convertCMakeToCompileCommandsBin(json, bin)

        // Replace bin with an unparseable garbage file.
        Files.delete(bin.toPath())
        bin.writer().use { writer ->
            writer.append("garbage file")
        }
        bin.setLastModified(json.lastModified())

        // Convert again, the garbage file should be updated
        structuredLog.clear()
        convertCMakeToCompileCommandsBin(json, bin)

        val actualMessages = structuredLog.loggingMessages()
            .map { it.text() }
            .filter { it.contains("compile_commands.json") }
            .joinToString("\n") { it.replace(bin.parent, "{bin-root}")}
            .replace("\\", "/")

        assertThat(actualMessages).isEqualTo("""
            C/C++: Deleting prior {bin-root}/compile_commands.json.bin because it was invalid
            C/C++: Exiting generation of {bin-root}/compile_commands.json.bin normally
        """.trimIndent())

        // Make sure the version was updated
        assertThat(compileCommandsFileIsCurrentVersion(bin)).isTrue()
    }

    @Test
    fun `missing -o flag is error`() {
        val json = createCompileCommandsFile(objectFlag = "-x")
        val bin = createCompileCommandsBinFile()
        convertCMakeToCompileCommandsBin(json, bin)

        // Make sure an error was emitted
        structuredLog.assertError(
            code = COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND)
    }

    @Test
    fun `check some invalid object-to-target translations`() {
        assertInvalidObjectFileNameHandled("invalid/object.o")
        assertInvalidObjectFileNameHandled("invalid.dir/object.o")
        assertInvalidObjectFileNameHandled("./object.o")
        assertInvalidObjectFileNameHandled("/object.o")
    }

    @Test
    fun `check some valid object-to-target translations`() {
        assertTargetNameFromObjectFile(
            "Externals/cpp-optparse/CMakeFiles/cpp-optparse.dir/OptionParser.cpp.o",
            expectTarget = "cpp-optparse"
        )
        assertTargetNameFromObjectFile(
            "CMakeFiles/cpp-optparse.dir/OptionParser.cpp.o",
            expectTarget = "cpp-optparse"
        )
        assertTargetNameFromObjectFile(
            "CMakeFiles/cpp-optparse.dir/src/OptionParser.cpp.o",
            expectTarget = "cpp-optparse"
        )
        assertTargetNameFromObjectFile(
            "CMakeFiles/cpp-optparse.dir/user.dir/OptionParser.cpp.o",
            expectTarget = "cpp-optparse"
        )
    }
}
