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

package com.android.utils.cxx

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompileCommandsCodecTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun singleFile() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val sourceFile = File("my/source/file.cpp")
        val compiler = File("clang.exe")
        val flags = listOf("-a", "-b")
        val workingDirectory = File("my/working/directory")
        CompileCommandsEncoder(out).use { encoder ->
            encoder.writeCompileCommand(
                    sourceFile,
                    compiler,
                    flags,
                    workingDirectory
            )
        }
        println("File size is ${out.length()}")
        // Safety check to make sure we don't write a whole final block
        assertThat(out.length()).isLessThan(1024)
        streamCompileCommands(out) {
                sourceFileStreamed,
                compilerStreamed,
                flagsStreamed,
                workingDirectoryStreamed ->
            assertThat(sourceFileStreamed).isEqualTo(sourceFile)
            assertThat(compilerStreamed).isEqualTo(compiler)
            assertThat(flagsStreamed).isEqualTo(flags)
            assertThat(workingDirectoryStreamed).isEqualTo(workingDirectory)
        }
    }

    @Test
    fun stringLargerThanBufferSize() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val sourceFile = File("my/source/file.cpp")
        val compiler = File("clang.exe")
        val flags = listOf("-a", "-b")
        val workingDirectory = File("my/working/directory")
        // Set the initial buffer size to 1 so that it has to grow
        // to be able to support the size of the strings passed in.
        CompileCommandsEncoder(out, initialBufferSize = 1).use { encoder ->
            encoder.writeCompileCommand(
                sourceFile,
                compiler,
                flags,
                workingDirectory
            )
        }
        println("File size is ${out.length()}")
        // Safety check to make sure we don't write a whole final block
        assertThat(out.length()).isLessThan(1024)
        streamCompileCommands(out) {
                sourceFileStreamed,
                compilerStreamed,
                flagsStreamed,
                workingDirectoryStreamed ->
            assertThat(sourceFileStreamed).isEqualTo(sourceFile)
            assertThat(compilerStreamed).isEqualTo(compiler)
            assertThat(flagsStreamed).isEqualTo(flags)
            assertThat(workingDirectoryStreamed).isEqualTo(workingDirectory)
        }
    }

    @Test
    fun checkInterning() {
        val folder = tempFolder.newFolder()
        val out = File(folder, "compile_commands.json.bin")
        val sourceFile1 = File("my/source/file-1.cpp")
        val sourceFile2 = File("my/source/file-2.cpp")
        val compiler = File("clang.exe")
        val flags = listOf("-a", "-b")
        val workingDirectory = File("my/working/directory")
        CompileCommandsEncoder(out).use { encoder ->
            encoder.writeCompileCommand(
                    sourceFile1,
                    compiler,
                    flags,
                    workingDirectory
            )
            encoder.writeCompileCommand(
                    sourceFile2,
                    compiler,
                    flags,
                    workingDirectory
            )
        }

        var count = 0
        lateinit var lastCompiler: File
        var lastFlags = listOf("")
        var lastWorkingDirectory = File("")
        streamCompileCommands(out) {
            sourceFileStreamed,
                    compilerStreamed,
                    flagsStreamed,
                    workingDirectoryStreamed ->
            when (count) {
                0 -> {
                    assertThat(sourceFileStreamed).isEqualTo(sourceFile1)
                    // Record first
                    lastCompiler = compilerStreamed
                    lastFlags = flagsStreamed
                    lastWorkingDirectory = workingDirectoryStreamed
                }
                1 -> {
                    assertThat(sourceFileStreamed).isEqualTo(sourceFile2)
                    // Check for reference equality
                    assertThat(compilerStreamed === lastCompiler).isTrue()
                    assertThat(flagsStreamed === lastFlags).isTrue()
                    assertThat(workingDirectoryStreamed === lastWorkingDirectory).isTrue()
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
                encoder.writeCompileCommand(
                        sourceFile = File("source-$i.cpp"),
                        compiler = File("compiler-$i"),
                        flags = listOf("flags-$i"),
                        workingDirectory = File("working-dir-$i")
                )
            }
        }
        println("File length ${out.length()}")
        var streamedFileCount = 0
        streamCompileCommands(out) { sourceFile,compiler,flags,workingDirectory ->
            assertThat(sourceFile.path).isEqualTo("source-$streamedFileCount.cpp")
            assertThat(compiler.path).isEqualTo("compiler-$streamedFileCount")
            assertThat(flags).isEqualTo(listOf("flags-$streamedFileCount"))
            assertThat(workingDirectory.path).isEqualTo("working-dir-$streamedFileCount")
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
        assertThat(stripArgsForIde(
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
            "/usr/local/google/home/tgeng/Android/Sdk/ndk/21.2.6472646/toolchains/llvm/prebuilt/linux-x86_64/sysroot",
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
            "app/src/main/cpp/native-lib.o")))
          .containsExactly(
            "-target",
            "i686-none-linux-android16",
            "-fdata-sections",
            "-ffunction-sections",
            "-fstack-protector-strong",
            "-funwind-tables",
            "-no-canonical-prefixes",
            "--sysroot",
            "/usr/local/google/home/tgeng/Android/Sdk/ndk/21.2.6472646/toolchains/llvm/prebuilt/linux-x86_64/sysroot",
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
}
