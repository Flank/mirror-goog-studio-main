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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.utils.cxx.streamCompileCommands
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets

data class CompileCommandsJsonEntry(val directory: String, val file: String, val command: String)
data class CompileCommandsJsonBinEntry(
    val sourceFile: String,
    val compiler: String,
    val flags: List<String>,
    val workingDirectory: String
)

fun File.readCompileCommandsJsonBinEntries(normalizer: FileNormalizer): List<CompileCommandsJsonBinEntry> {
    val result = mutableListOf<CompileCommandsJsonBinEntry>()
    streamCompileCommands(this) { sourceFile: File, compiler: File, flags: List<String>, workingDirectory: File ->
        result.add(
            CompileCommandsJsonBinEntry(
                normalizer.normalize(sourceFile),
                normalizer.normalize(compiler),
                flags,
                normalizer.normalize(workingDirectory)
            )
        )
    }
    return result
}

fun File.readCompileCommandsJsonEntries(): List<CompileCommandsJsonEntry> = reader().use {
    Gson().fromJson<List<CompileCommandsJsonEntry>>(
        it,
        object : TypeToken<List<CompileCommandsJsonEntry>>() {}.type
    )
}

fun File.readAsFileIndex(): List<File> = readLines(StandardCharsets.UTF_8).map { File(it) }
fun File.readAsFileIndex(normalizer: FileNormalizer): List<String> =
    readLines(StandardCharsets.UTF_8).map { normalizer.normalize(File(it)) }

/**
 * Dumps the content of [NativeModule] as a string. The dump format is like the following
 *
 * ```
 *   [HelloWorld.app]:
 *   > NativeModule
 *     ...
 *   < NativeModule
 * ```
 *
 * where `...` is the common model dumper [NativeModule.writeToBuilder]. The dumper normalizes
 * any file paths relative to well-known directories, for example, project root, SDK root. This
 * makes test easier to write since these directories generally change across platforms and test
 * runs. The normalization is done by [FileNormalizer].
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>>.dump(): String {
    val sb = StringBuilder()
    container.infoMaps.forEach { (_, modelMap) ->
        modelMap.forEach { moduleName, (nativeModule: NativeModule, _) ->
            sb.appendln("[$moduleName]")
            sb.appendln(
                dump(NativeModule::class.java, normalizer) {
                    nativeModule.writeToBuilder(this)
                }
            )
        }
    }
    return sb.toString().trim().replace(System.lineSeparator(), "\n")
}