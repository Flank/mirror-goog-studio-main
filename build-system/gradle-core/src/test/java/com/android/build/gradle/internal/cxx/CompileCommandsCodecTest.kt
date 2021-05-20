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

package com.android.build.gradle.internal.cxx

import com.android.build.gradle.internal.cxx.configure.convertCMakeToCompileCommandsBin
import com.android.testutils.TestResources
import com.android.utils.cxx.CompileCommandsEncoder
import com.android.utils.cxx.compileCommandsFileIsCurrentVersion
import com.android.utils.cxx.extractFlagArgument
import com.android.utils.cxx.streamCompileCommandsV2
import com.android.utils.cxx.streamCompileCommands
import com.android.utils.cxx.stripArgsForIde
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompileCommandsCodecTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private fun testFile(testFileName: String): File {
        val resourceFolder = "/com/android/build/gradle/external/compile_commands/"
        return TestResources.getFile(
            CompileCommandsCodecTest::class.java, resourceFolder + testFileName
        )
    }

    @Test
    fun singleFile() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val originalSourceFile = File("my/source/file.cpp")
        val originalCompiler = File("clang.exe")
        val originalFlags = listOf("-a", "-b")
        val originalWorkingDirectory = File("my/working/directory")
        val originalOutput = File("my/output/file.o")
        val originalTarget = "my-target-name"
        CompileCommandsEncoder(out).use { encoder ->
            encoder.writeCompileCommand(
                originalSourceFile,
                originalCompiler,
                originalFlags,
                originalWorkingDirectory,
                originalOutput,
                originalTarget
            )
        }
        println("File size is ${out.length()}")
        // Safety check to make sure we don't write a whole final block
        assertThat(out.length()).isLessThan(1024)
        streamCompileCommandsV2(out) {
            assertThat(sourceFile).isEqualTo(originalSourceFile)
            assertThat(compiler).isEqualTo(originalCompiler)
            assertThat(flags).isEqualTo(originalFlags)
            assertThat(workingDirectory).isEqualTo(originalWorkingDirectory)
            assertThat(outputFile).isEqualTo(originalOutput)
            assertThat(target).isEqualTo(originalTarget)
            assertThat(sourceFileIndex).isEqualTo(0)
            assertThat(sourceFileCount).isEqualTo(1)
        }
    }

    // Check whether we can detect that this version of compile_commands.json.bin supports
    // output file streaming.
    @Test
    fun `check whether compile_commands json bin supports output file for version 2 file`() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val sourceFile = File("my/source/file.cpp")
        val compiler = File("clang.exe")
        val flags = listOf("-a", "-b")
        val workingDirectory = File("my/working/directory")
        val output = File("my/output/file.o")
        val target = "my-target"
        CompileCommandsEncoder(out).use { encoder ->
            encoder.writeCompileCommand(
                sourceFile,
                compiler,
                flags,
                workingDirectory,
                output,
                target
            )
        }
        assertThat(compileCommandsFileIsCurrentVersion(out)).isTrue()
    }

    @Test
    fun `check whether compile_commands json bin supports output file for version 1 file`() {
        val version1 = testFile("version_1_compile_commands.json.bin")
        assertThat(compileCommandsFileIsCurrentVersion(version1)).isFalse()
        streamCompileCommands(version1) {
                _,
                _,
                _,
                _ ->
        }
    }

    @Test
    fun `check validity of dolphin names`() {
        val dolphin = testFile("dolphin_compile_commands.json")
        val folder = tempFolder.newFolder()
        folder.mkdirs()
        val dolphinBin = File(folder, "dolphin_compile_commands.json.bin")

        convertCMakeToCompileCommandsBin(dolphin, dolphinBin)

        streamCompileCommandsV2(dolphinBin) {
            val sourceFileName = sourceFile.name
            val outputName = outputFile.name
            assertThat(outputName).isEqualTo("$sourceFileName.o")
        }
    }

    @Test
    fun stringLargerThanBufferSize() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val originalSourceFile = File("my/source/file.cpp")
        val originalCompiler = File("clang.exe")
        val originalFlags = listOf("-a", "-b")
        val originalWorkingDirectory = File("my/working/directory")
        val originalOutput = File("my/output/file.o")
        val originalTarget = "my-target-name"
        // Set the initial buffer size to 1 so that it has to grow
        // to be able to support the size of the strings passed in.
        CompileCommandsEncoder(out, initialBufferSize = 1).use { encoder ->
            encoder.writeCompileCommand(
                originalSourceFile,
                originalCompiler,
                originalFlags,
                originalWorkingDirectory,
                originalOutput,
                originalTarget
            )
        }
        println("File size is ${out.length()}")
        // Safety check to make sure we don't write a whole final block
        assertThat(out.length()).isLessThan(1024)
        streamCompileCommandsV2(out) {
            assertThat(sourceFile).isEqualTo(originalSourceFile)
            assertThat(compiler).isEqualTo(originalCompiler)
            assertThat(flags).isEqualTo(originalFlags)
            assertThat(workingDirectory).isEqualTo(originalWorkingDirectory)
            assertThat(outputFile).isEqualTo(originalOutput)
            assertThat(target).isEqualTo(originalTarget)
        }
    }

    @Test
    fun checkInterning() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val originalSourceFile1 = File("my/source/file-1.cpp")
        val originalSourceFile2 = File("my/source/file-2.cpp")
        val originalCompiler = File("clang.exe")
        val originalFlags = listOf("-a", "-b")
        val originalWorkingDirectory = File("my/working/directory")
        val originalOutput = File("my/output/file.o")
        val originalTarget = "my-target-name"
        CompileCommandsEncoder(out).use { encoder ->
            encoder.writeCompileCommand(
                originalSourceFile1,
                originalCompiler,
                originalFlags,
                originalWorkingDirectory,
                originalOutput,
                originalTarget
            )
            encoder.writeCompileCommand(
                originalSourceFile2,
                originalCompiler,
                originalFlags,
                originalWorkingDirectory,
                originalOutput,
                originalTarget
            )
        }

        var count = 0
        lateinit var lastCompiler: File
        var lastFlags = listOf("")
        var lastWorkingDirectory = File("")
        var lastOutput = File("")
        var lastTarget = ""
        streamCompileCommandsV2(out) {
            when (count) {
                0 -> {
                    assertThat(sourceFile).isEqualTo(originalSourceFile1)
                    // Record first
                    lastCompiler = compiler
                    lastFlags = flags
                    lastWorkingDirectory = workingDirectory
                    lastOutput = outputFile
                    lastTarget = target
                }
                1 -> {
                    assertThat(sourceFile).isEqualTo(originalSourceFile2)
                    // Check for reference equality
                    assertThat(compiler === lastCompiler).isTrue()
                    assertThat(flags === lastFlags).isTrue()
                    assertThat(workingDirectory === lastWorkingDirectory).isTrue()
                    assertThat(outputFile === lastOutput).isTrue()
                    assertThat(target === lastTarget).isTrue()
                }
                else -> error("Saw more than two files")
            }
            ++count
        }
        assertThat(count)
                .named("Did not process two files")
                .isEqualTo(2)
    }

    /**
     * Write a large set of files to make sure things don't break down at scale.
     */
    @Test
    fun stress() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val fileCount = 100000
        CompileCommandsEncoder(out).use { encoder ->
            repeat(fileCount) { i ->
                // Since each string is different, we expect no compression
                encoder.writeCompileCommand(
                    sourceFile = File("source-$i.cpp"),
                    compiler = File("compiler-$i"),
                    flags = listOf("flags-$i"),
                    workingDirectory = File("working-dir-$i"),
                    outputFile = File("output-file-$i"),
                    target = "target-$i"
                )
            }
        }
        println("File length ${out.length()}") // Latest observed size was 14.5M
        var streamedFileCount = 0
        streamCompileCommandsV2(out) {
            assertThat(sourceFile.path).isEqualTo("source-$streamedFileCount.cpp")
            assertThat(compiler.path).isEqualTo("compiler-$streamedFileCount")
            assertThat(flags).isEqualTo(listOf("flags-$streamedFileCount"))
            assertThat(outputFile.path).isEqualTo("output-file-$streamedFileCount")
            assertThat(target).isEqualTo("target-$streamedFileCount")
            ++streamedFileCount
        }
        assertThat(streamedFileCount).isEqualTo(fileCount)
    }

    @Test
    fun readInvalidFile() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        out.writeText("This is an invalid file")
        try {
            streamCompileCommands(out) { _, _, _, _ -> }
        }
        catch (e: Exception) {
            assertThat(e.message).endsWith("is not a valid C/C++ Build Metadata file")
            return
        }
        error("Expected an exception")
    }

    @Test
    fun testStripArgsForIde() {
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()

        assertThat(
            stripArgsForIde(
                "path/to/source",
                listOf("-abc", "-def", "foo", "bar", "path/to/source")
            )
        ).containsExactly("-abc", "-def", "foo", "bar").inOrder()

        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "-o", "blah")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "-o", "blah.o")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "--output", "blah.o")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "--output=blah.o")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "-MFblah")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(stripArgsForIde("", listOf("-abc", "-def", "foo", "bar", "-MF", "blah")))
            .containsExactly("-abc", "-def", "foo", "bar").inOrder()
        assertThat(
            stripArgsForIde(
                "",
                listOf("-abc", "-def", "foo", "bar", "-M", "-MM", "-MD", "-MG", "-MP", "-MMD", "-c")
            )
        ).containsExactly("-abc", "-def", "foo", "bar").inOrder()
    }

    @Test
    fun testStripArgsForIde_realData() {
        assertThat(
            stripArgsForIde(
          "src/main/cpp/native-lib.cpp",
          listOf(
            "-MMD",
            "-MP",
            "-MF",
            "app/src/main/cpp/native-lib.o.d",
            "-target",
            "i686-none-linux-android16",
            "-fdata-sections",
            "-ffunction-sections",
            "-fstack-protector-strong",
            "-funwind-tables",
            "-no-canonical-prefixes",
            "--sysroot",
            "/usr/local/google/home/tgeng/Android/Sdk/ndk/21.3.6528147/toolchains/llvm/prebuilt/linux-x86_64/sysroot",
            "-g",
            "-Wno-invalid-command-line-argument",
            "-Wno-unused-command-line-argument",
            "-D_FORTIFY_SOURCE=2",
            "-fno-exceptions",
            "-fno-rtti",
            "-fPIC",
            "-O0",
            "-UNDEBUG",
            "-fno-limit-debug-info",
            "-I/usr/local/google/home/tgeng/x/test-projects/NativeHeader2/app",
            "-DANDROID",
            "-Wformat",
            "-Werror=format-security",
            "-mstackrealign",
            "-c",
            "src/main/cpp/native-lib.cpp",
            "-o",
            "app/src/main/cpp/native-lib.o"))
        )
          .containsExactly(
            "-target",
            "i686-none-linux-android16",
            "-fdata-sections",
            "-ffunction-sections",
            "-fstack-protector-strong",
            "-funwind-tables",
            "-no-canonical-prefixes",
            "--sysroot",
            "/usr/local/google/home/tgeng/Android/Sdk/ndk/21.3.6528147/toolchains/llvm/prebuilt/linux-x86_64/sysroot",
            "-g",
            "-Wno-invalid-command-line-argument",
            "-Wno-unused-command-line-argument",
            "-D_FORTIFY_SOURCE=2",
            "-fno-exceptions",
            "-fno-rtti",
            "-fPIC",
            "-O0",
            "-UNDEBUG",
            "-fno-limit-debug-info",
            "-I/usr/local/google/home/tgeng/x/test-projects/NativeHeader2/app",
            "-DANDROID",
            "-Wformat",
            "-Werror=format-security",
              "-mstackrealign"
          ).inOrder()
    }

    @Test
    fun `extract output file`() {
        assertThat(
            extractFlagArgument(
            "-o", "--output",
            listOf(
                "-o",
                "app/src/main/cpp/native-lib.o")
                )
        ).isEqualTo("app/src/main/cpp/native-lib.o")
    }
}
