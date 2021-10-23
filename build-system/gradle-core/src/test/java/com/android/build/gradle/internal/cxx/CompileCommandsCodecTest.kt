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


import com.android.SdkConstants
import com.android.build.gradle.internal.cxx.configure.convertCMakeToCompileCommandsBin
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_CONTENT
import com.android.build.gradle.internal.cxx.io.compareFileContents
import com.android.testutils.TestResources
import com.android.testutils.TestUtils
import com.android.utils.cxx.COMPILE_COMMANDS_CODEC_VERSION
import com.android.utils.cxx.CompileCommandsEncoder
import com.android.utils.cxx.compileCommandsFileIsCurrentVersion
import com.android.utils.cxx.extractFlagArgument
import com.android.utils.cxx.hasBug201754404
import com.android.utils.cxx.readCompileCommandsVersionNumber
import com.android.utils.cxx.streamCompileCommandsV1
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
        if (TestUtils.runningFromBazel()) {
            val resourceFolder = "/com/android/build/gradle/external/compile_commands/"
            return TestResources.getFile(
                CompileCommandsCodecTest::class.java, "$resourceFolder$testFileName"
            )
        }
        val workspaceSubfolder = "tools/base/build-system/gradle-core/src/test/resources/com/android/build/gradle/external/compile_commands/"
        val path = TestUtils.getWorkspaceRoot().resolve("$workspaceSubfolder$testFileName")
        return path.toFile()
    }

    /**
     * The purpose of this test is to detect incompatible changes that don't have a corresponding
     * change to [COMPILE_COMMANDS_CODEC_VERSION].
     *
     * - If you get a failure like "Expected path/to/version_$XYZ_compile_commands.json.bin to
     *   exist. Did you forget to check it in?" then it means that you increased
     *   [COMPILE_COMMANDS_CODEC_VERSION] in this CL. The expected file has been created and should
     *   be added to the CL. The test will pass when you run it again.
     *
     * - If one of the asserts inside the "Make sure we can stream all 2+ versions" block of code
     *   fires, then there is a behavior change. If it's expected (for example, a bug fix) then the
     *   test should be modified to check the version number and assert based on specific version.
     *
     * - If you get an error like "Content of path/to/version_$XYZ_compile_commands.json.bin is not
     *   the same when recreated" then something changed in the binary output even though it seems
     *   compatible according to the current decoding logic. The safest course is to increase
     *   [COMPILE_COMMANDS_CODEC_VERSION]. This check is not done one Windows because the binary
     *   can be different due to path handling.
     */
    @Test
    fun `confirm current version binary compatibility`() {
        fun versionFile(version : Int) = testFile(
            "version_${version}_compile_commands.json.bin")
        fun createJsonBinForCurrentVersion() = convertCMakeToCompileCommandsBin(
                testFile("dolphin_compile_commands.json"),
                versionFile(COMPILE_COMMANDS_CODEC_VERSION))

        val versionRecord = versionFile(COMPILE_COMMANDS_CODEC_VERSION)
        if (!versionRecord.exists()) {
            createJsonBinForCurrentVersion()
            error("Expected $versionRecord to exist. Did you forget to check it in?")
        }

        // Make sure we can stream all 2+ versions
        for (version in 2 .. COMPILE_COMMANDS_CODEC_VERSION) {
            val versionRecord = versionFile(version)
            val readVersion = readCompileCommandsVersionNumber(versionRecord)
            assertThat(readVersion).isEqualTo(version)
            val distinctSourceFiles = mutableSetOf<String>()
            val distinctCompilers= mutableSetOf<String>()
            val distinctFlags = mutableSetOf<List<String>>()
            val distinctOutputFiles = mutableSetOf<String>()
            val distinctTargets = mutableSetOf<String>()
            streamCompileCommands(versionRecord) {
                distinctSourceFiles += sourceFile.path
                distinctCompilers += compiler.path
                distinctFlags += flags
                distinctOutputFiles += outputFile.path
                distinctTargets += target
            }
            assertThat(distinctSourceFiles.size).isEqualTo(832)
            assertThat(distinctCompilers.size).isEqualTo(2)
            assertThat(distinctFlags.size).isEqualTo(31)
            assertThat(distinctTargets.size).isEqualTo(59)
            assertThat(distinctOutputFiles.size).isEqualTo(832)
        }

        // Recreate the file in the expected location. It will be the same as before unless a
        // code change has triggered a binary difference.
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) return
        val recreationOfVersionRecord =
            tempFolder.newFolder().resolve("recreation_compile_commands.json.bin")
        recreationOfVersionRecord.parentFile.mkdirs()
        convertCMakeToCompileCommandsBin(
            testFile("dolphin_compile_commands.json"),
            recreationOfVersionRecord)
        val comparison = compareFileContents(versionRecord, recreationOfVersionRecord)
        assertThat(comparison)
            .named("Content of $versionRecord is not the same when recreated")
            .isEqualTo(SAME_CONTENT)
    }

    @Test
    fun `confirm prior AGP versions can be read`() {
        fun versionFile(version : String) = testFile(
            "agp_${version}_compile_commands.json.bin")

        // TODO(201754404) replace "7.1.0-beta01" with release version "7.1.0". It is expected to
        // have hasBug201754404 == false since the version number was increased.
        val binVersionToAgpVersionsMap = listOf(
            1 to listOf("4.2.0", "4.2.1", "4.2.2"),
            2 to listOf("7.0.0", "7.0.1", "7.0.2", "7.0.3", "7.1.0-beta01"),
            3 to listOf("7.2.0-alpha02")
        )
        val hasBug201754404 = setOf("7.1.0-beta01")

        // Make sure we can stream all 2+ versions
        for ((binVersion, agpVersions) in binVersionToAgpVersionsMap) {
            for(agpVersion in agpVersions) {
                val versionRecord = versionFile(agpVersion)
                assertThat(hasBug201754404(versionRecord))
                    .named(agpVersion)
                    .isEqualTo(hasBug201754404.contains(agpVersion))
                val readVersion = readCompileCommandsVersionNumber(versionRecord)
                assertThat(readVersion).isEqualTo(binVersion)
                val distinctSourceFiles = mutableSetOf<String>()
                val workingDirectories = mutableSetOf<String>()
                val distinctCompilers = mutableSetOf<String>()
                val distinctFlags = mutableSetOf<List<String>>()

                if (readVersion === 1) {
                    streamCompileCommandsV1(versionRecord) {
                        sourceFile:File,
                        compiler:File,
                        flags:List<String>,
                        workingDirectory:File ->
                        distinctSourceFiles += sourceFile.path
                        distinctCompilers += compiler.path
                        distinctFlags += flags
                        workingDirectories += workingDirectory.path
                    }
                } else {

                    val distinctOutputFiles = mutableSetOf<String>()
                    val distinctTargets = mutableSetOf<String>()
                    streamCompileCommands(versionRecord) {
                        distinctSourceFiles += sourceFile.path
                        distinctCompilers += compiler.path
                        distinctFlags += flags
                        workingDirectories += workingDirectory.path
                        distinctOutputFiles += outputFile.path
                        distinctTargets += target
                    }
                    assertThat(distinctTargets.size).isEqualTo(1)
                    assertThat(distinctOutputFiles.size).isEqualTo(1)
                }
                assertThat(distinctSourceFiles.size).isEqualTo(1)
                assertThat(distinctCompilers.size).isEqualTo(1)
                assertThat(distinctFlags.size).isEqualTo(1)
                assertThat(workingDirectories.size).isEqualTo(1)

            }
        }
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
        streamCompileCommands(out) {
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
    fun `check whether compile_commands json bin supports output file for current version`() {
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
        streamCompileCommandsV1(version1) {
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

        streamCompileCommands(dolphinBin) {
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
        streamCompileCommands(out) {
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
        streamCompileCommands(out) {
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
        streamCompileCommands(out) {
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
            streamCompileCommandsV1(out) { _, _, _, _ -> }
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
