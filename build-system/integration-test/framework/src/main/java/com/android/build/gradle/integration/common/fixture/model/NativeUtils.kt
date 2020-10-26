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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModelFromJson
import com.android.build.gradle.internal.cxx.model.soFolder
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.utils.cxx.streamCompileCommands
import com.google.gson.JsonArray
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

data class CompileCommandsJsonBinEntry(
        val sourceFile: String,
        val compiler: String,
        val workingDir: String,
        val flags: List<String>
)

fun File.readAsFileIndex(): List<File> = readLines(StandardCharsets.UTF_8).map { File(it) }
fun File.readAsFileIndex(normalizer: FileNormalizer): List<String> =
        readLines(StandardCharsets.UTF_8).map { normalizer.normalize(File(it)) }

fun File.readCompileCommandsJsonBin(normalizer: FileNormalizer): List<CompileCommandsJsonBinEntry> {
    val entries = mutableListOf<CompileCommandsJsonBinEntry>()
    streamCompileCommands(this) { sourceFile: File, compiler: File, flags: List<String>, workingDirectory: File ->
        var jsonArray = JsonArray()
        flags.forEach(jsonArray::add)
        jsonArray = normalizer.normalize(jsonArray).asJsonArray
        val normalizedFlags = (0 until jsonArray.size()).map { jsonArray.get(it).asString }
        entries.add(
                CompileCommandsJsonBinEntry(
                        normalizer.normalize(sourceFile),
                        normalizer.normalize(compiler),
                        normalizer.normalize(workingDirectory),
                        normalizedFlags
                )
        )
    }
    return entries
}

fun File.dumpCompileCommandsJsonBin(normalizer: FileNormalizer): String =
        readCompileCommandsJsonBin(normalizer)
                .sortedBy { it.toString() }
                .joinToString("\n\n") { (sourceFile: String, compiler: String, workingDir: String, flags: List<String>) ->
                    """
            sourceFile: $sourceFile
            compiler:   $compiler
            workingDir: $workingDir
            flags:      $flags
        """.trimIndent()
                }

private val abiTags = Abi.values().map { abi -> abi.tag }.toSet()

fun findAbiSegment(file: File) : String? {
    val name = file.name
    if (name in abiTags) return file.name
    return findAbiSegment(file.parentFile?:return null)
}

fun findCxxSegment(file: File) : String? {
    val name = file.name
    if (name.endsWith("cxx")) return file.name
    return findCxxSegment(file.parentFile?:return null)
}

fun findConfigurationSegment(file: File) : String? {
    val abi = findAbiSegment(file) ?: return null
    val cxx = findCxxSegment(file) ?: return null
    val string = file.toString()
    return string.substring(string.lastIndexOf(cxx) + cxx.length + 1, string.lastIndexOf(abi) - 1).replace("\\", "/")
}

private fun ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>>.hashEquivalents() : List<Pair<String, String>> =
        container.infoMaps.values
                .asSequence()
                .map { it.values }
                .flatten()
                .map { it.model.variants }
                .flatten()
                .map { it.abis.map { abi -> "{${it.name.toUpperCase(Locale.ROOT)}}" to findConfigurationSegment(abi.sourceFlagsFile) } }
                .flatten()
                .filter { it.second != null }
                .map { it.first to it.second!! }
                .distinct()
                .toList()

/**
 * Recover existing CxxAbiModels that were written to disk during the configure phase.
 * No new configures should be done.
 */
fun GradleTestProject.recoverExistingCxxAbiModels(): List<CxxAbiModel> {
    val abis = modelV2().fetchNativeModules(listOf(), listOf())
            .container.infoMaps.values
            .flatMap { it.values }
            .flatMap { it.model.variants }
            .flatMap { it.abis }
    val modelFiles = abis
            .map { abi -> abi.sourceFlagsFile.parentFile.resolve("build_model.json") }
            .distinct()
    val models = modelFiles.filter { it.isFile }
            .map { createCxxAbiModelFromJson(it.readText()) }
            .distinct()
    if (models.isEmpty()) error("Could not recover any CxxAbiModels, did configure run?")
    return models
}

/**
 * Mainly a Java helper to avoid writing tons of filter code.
 */
fun GradleTestProject.getSoFolderFor(abi : Abi) =
    recoverExistingCxxAbiModels().singleOrNull { it.abi == abi }?.soFolder


private fun List<Pair<String, String>>.toTranslateFunction() = run {
    { value : String ->
        var result = value
        for((old, new) in this) {
            result = result.replace(old, new)
        }
        result
    }
}

fun ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>>.hashToKeyTranslator() =
        hashEquivalents().map { it.second to it.first }.toTranslateFunction()

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
fun ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>>.dump(map:(NativeModule)->NativeModule): String {
    val sb = StringBuilder()
    val hashTranslator = hashToKeyTranslator()
    val normalizer = object : FileNormalizer by normalizer {
        override fun normalize(file: File) = hashTranslator(normalizer.normalize(file))
    }
    container.infoMaps.forEach { (_, modelMap) ->
        modelMap.forEach { moduleName, (nativeModule: NativeModule, _) ->
            sb.appendln("[$moduleName]")
            sb.appendln(
                    dump(NativeModule::class.java, normalizer) {
                        map(nativeModule).writeToBuilder(this)
                    }
            )
        }
    }
    return sb.toString().trim().replace(System.lineSeparator(), "\n")
}

fun ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>>.dump() = dump { it }

fun filterByVariantName(vararg names: String) : (NativeModule) -> NativeModule = { nativeModule:NativeModule ->
    object : NativeModule by nativeModule {
        override val variants = nativeModule.variants.filter { names.contains(it.name) }
    }
}
