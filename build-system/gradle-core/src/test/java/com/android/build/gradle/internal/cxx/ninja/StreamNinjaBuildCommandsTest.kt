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

package com.android.build.gradle.internal.cxx.ninja

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.utils.TokenizedCommandLineMap
import com.android.utils.cxx.STRIP_FLAGS_WITHOUT_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_ARG
import com.android.utils.cxx.STRIP_FLAGS_WITH_IMMEDIATE_ARG
import com.google.common.truth.Truth.assertThat
import com.google.gson.stream.JsonReader
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class StreamNinjaBuildCommandsTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val bazelFolderBase = "tools/base/build-system/gradle-core/src/test/data/ninja-build-samples"

    private fun locate(subFolder : String) : File {
        val base = TestUtils.resolveWorkspacePath(bazelFolderBase).toFile()
        return base.resolve(subFolder)
    }

    @Test
    fun `dolphin via CMake 3 18 1`() {
        val originalWorkingFolder = File("/Users/jomof/projects/studio-main/out/build/base/build-system/integration-test/native/build/tmp/junit2760794059956044528/junit4505799956879287491/src/Source/Android/app/.cxx/RelWithDebInfo/4z4p6154/arm64-v8a",)
        val buildNinja = locate("dolphin/3.18.1/build.ninja")
        val commands = parseCompileCommandsJson(buildNinja.resolveSibling("compile_commands.json"))
        val sawOutputs = mutableSetOf<String>()
        streamNinjaBuildCommands(buildNinja) {
            val out = expand("\$out")
            if (out.contains(".o")) {
                sawOutputs += out
                val prior = commands.getValue(out)
                val current = parseCommand(
                    expand(command),
                    expand("\$in"),
                    originalWorkingFolder.path)
                assertThat(current.flags).isEqualTo(prior.flags)
                assertThat(current.sourceFile).isEqualTo(prior.sourceFile)
                assertThat(current.outputFile).isEqualTo(prior.outputFile)
                assertThat(current.compiler).isEqualTo(prior.compiler)
                assertThat(current.workingDirectory).isEqualTo(prior.workingDirectory)
                assertThat(current).isEqualTo(prior)
            }
        }
        val unseenOutputs = commands.keys - sawOutputs
        assertThat(unseenOutputs).isEmpty()
    }

    @Test
    fun `dolphin via CMake 3 10 2`() {
        val originalWorkingFolder = File("/Users/jomof/projects/studio-main/out/build/base/build-system/integration-test/native/build/tmp/junit2760794059956044528/junit4505799956879287491/src/Source/Android/app/.cxx/RelWithDebInfo/4z4p6154/arm64-v8a",)
        val buildNinja = locate("dolphin/3.10.2/build.ninja")
        val commands = parseCompileCommandsJson(buildNinja.resolveSibling("compile_commands.json"))
        val sawOutputs = mutableSetOf<String>()
        streamNinjaBuildCommands(buildNinja) {
            val out = expand("\$out")
            if (out.contains(".o")) {
                sawOutputs += out
                val prior = commands.getValue(out)
                val current = parseCommand(
                    expand(command),
                    expand("\$in"),
                    originalWorkingFolder.path)
                assertThat(current.flags).isEqualTo(prior.flags)
                assertThat(current.sourceFile).isEqualTo(prior.sourceFile)
                assertThat(current.outputFile).isEqualTo(prior.outputFile)
                assertThat(current.compiler).isEqualTo(prior.compiler)
                assertThat(current.workingDirectory).isEqualTo(prior.workingDirectory)
                assertThat(current).isEqualTo(prior)
            }
        }
        val unseenOutputs = commands.keys - sawOutputs
        assertThat(unseenOutputs).isEmpty()
    }

    private val tokenMap =
        TokenizedCommandLineMap<Command>(
            raw = false,
            platform = SdkConstants.PLATFORM_DARWIN) {
                tokens,
                sourceFile ->
            tokens.removeTokenGroup(
                sourceFile,
                0,
                filePathSlashAgnostic = true)

            for (flag in STRIP_FLAGS_WITH_ARG) {
                tokens.removeTokenGroup(flag, 1)
            }
            for (flag in STRIP_FLAGS_WITH_IMMEDIATE_ARG) {
                tokens.removeTokenGroup(flag, 0, matchPrefix = true)
            }
            for (flag in STRIP_FLAGS_WITHOUT_ARG) {
                tokens.removeTokenGroup(flag, 0)
            }
        }

    private fun parseCommand(
        command : String,
        sourceFile : String,
        workingDirectory : String
    ) : Command {
        return tokenMap.computeIfAbsent(command, sourceFile) {
            // Find the output file (for example, probably something.o)
            val outputFile =
                it.removeTokenGroup("-o", 1, returnFirstExtra = true) ?:
                it.removeTokenGroup("--output=", 0, matchPrefix = true, returnFirstExtra = true) ?:
                it.removeTokenGroup("--output", 1, returnFirstExtra = true)
            val tokenList = it.toTokenList()
            Command(tokenList[0], tokenList.subList(1, tokenList.size), outputFile!!, sourceFile, workingDirectory)
        }
    }

    private data class Command(
        val compiler : String,
        val flags : List<String>,
        val outputFile : String,
        val sourceFile : String,
        val workingDirectory : String
    )

    private fun parseCompileCommandsJson(json : File) : Map<String, Command> {
        val result = mutableMapOf<String, Command>()
        JsonReader(json.reader(StandardCharsets.UTF_8)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                lateinit var directory: String
                lateinit var command: String
                lateinit var sourceFile: String
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "directory" -> directory = reader.nextString()
                        "command" -> command = reader.nextString()
                        "file" -> sourceFile = reader.nextString()
                        // swallow other optional fields
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val parsed = parseCommand(command, sourceFile, directory)
                result[parsed.outputFile] = parsed
            }
            reader.endArray()
        }
        return result
    }
}
