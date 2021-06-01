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
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.logging.getCxxStructuredLogFolder
import com.android.build.gradle.internal.cxx.logging.readStructuredLogs
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxAbiModelFromJson
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.utils.cxx.streamCompileCommands
import com.google.gson.JsonArray
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*
import com.android.build.gradle.internal.cxx.string.StringDecoder

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

/**
 * Return a report of build outputs (*.so, *.o, *.a).
 */
fun GradleTestProject.goldenBuildProducts() : String {
    val fetchResult = modelV2().fetchNativeModules(NativeModuleParams(listOf(), listOf()))
    val hashToKey = fetchResult.cxxFileVariantSegmentTranslator()
    val projectFolder = recoverExistingCxxAbiModels().first().variant.module.project.rootBuildGradleFolder
    return projectFolder.walk()
            .filter { file -> file.extension in listOf("so", "o", "a") }
            .map { fetchResult.normalizer.normalize(it) }
            .map { hashToKey(it) }
            .sorted()
            .toList()
            .joinToString("\n")
}

/**
 * Output configuration flags for the given ABI.
 */
fun GradleTestProject.goldenConfigurationFlags(abi: Abi) : String {
    val fetchResult =
            modelV2().fetchNativeModules(NativeModuleParams(listOf("debug"), listOf(abi.tag)))
    val recoveredAbiModel = recoverExistingCxxAbiModels().single { it.abi == abi }
    val hashToKey = fetchResult.cxxFileVariantSegmentTranslator()
    return hashToKey(recoveredAbiModel.goldenConfigurationFlags())
}

private fun CxxAbiModel.goldenConfigurationFlags() : String {
    fun String.slash() : String = replace("\\", "/")
    fun File.slash() : String = toString().slash()
    return metadataGenerationCommandFile.readText()
            .slash()
            .replace(variant.module.project.rootBuildGradleFolder.slash(), "{PROJECT}")
            .replace(variant.module.ndkFolder.slash(), "{NDK}")
            .trim()
            .lines()
            .map { line ->
                if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                    val cmake = variant.module.cmake!!
                    line
                        .replace(cmake.cmakeExe!!.slash(), "{CMAKE}")
                        .replace(cmake.ninjaExe!!.slash(), "{NINJA}")
                        .replace("-GNinja", "-G{Generator}")
                        .replace("-GAndroid Gradle - Ninja", "-G{Generator}")
                } else line
            }
            .filter { !it.startsWith(" ") }
            .filter { !it.isBlank() }
            .filter { !it.startsWith("jvmArgs") }
            .filter { !it.startsWith("arguments") }
            .filter { !it.startsWith("Executable :") }
            .sorted()
            .joinToString("\n")
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

fun findIntermediatesSegment(file: File) : String? {
    val name = file.name
    if (name.endsWith("intermediates")) return file.name
    return findIntermediatesSegment(file.parentFile?:return null)
}

fun findConfigurationSegment(file: File) : String? {
    val abi = findAbiSegment(file) ?: return null
    val cxx = findCxxSegment(file) ?: findIntermediatesSegment(file) ?: return null
    val string = file.toString().replace("\\", "/")
    return string.substring(
            string.lastIndexOf(cxx) + cxx.length + 1,
            string.lastIndexOf(abi) - 1)
        .replace("/meta", "")
}

/**
 * Discover and build a map (list of pair) from output file subsegment to a human readable name
 * that can be used in tests. For example,
 *
 *      cmake/debug ==> {DEBUG}
 *      cmake/release ==> {RELEASE}
 */
private fun ModelBuilderV2.FetchResult<ModelContainerV2>.hashEquivalents() : List<Pair<String, String>> {
    val abis = container.infoMaps.values
            .flatMap { it.values }
            .mapNotNull { it.nativeModule }
            .flatMap { it.variants }
            .flatMap { it.abis.map { abi -> it to abi } }
    val segments = (abis.map { (variant, abi) ->
        findConfigurationSegment(abi.sourceFlagsFile)!! to "{${variant.name.toUpperCase(Locale.ROOT)}}"
    } + abis.map { (variant, abi) ->
        findConfigurationSegment(abi.symbolFolderIndexFile)!! to "{${variant.name.toUpperCase(Locale.ROOT)}}"
    }).distinct()
    val configurationAliases = segments
            .map { (segment, alias) ->
                "/.cxx/$segment" to "/.cxx/$alias"
            }
    val intermediatesCxxAliases = segments
            .map { (segment, alias) ->
                "/cxx/$segment" to "/$alias"
            }
    val intermediatesAliases = segments
            .map { (segment, alias) ->
                "/intermediates/$segment" to "/intermediates/$alias"
            }
    return configurationAliases + intermediatesCxxAliases + intermediatesAliases
}

/**
 * Recover existing CxxAbiModels that were written to disk during the configure phase.
 * No new configures should be done.
 */
fun GradleTestProject.recoverExistingCxxAbiModels(): List<CxxAbiModel> {
    val modelFiles = buildDir.parentFile.walk().filter { file -> file.name == "build_model.json" }.toList()
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

/**
 * Build a translator that will convert from ".cxx/[some stuff in related to "debug" variant]/x86"
 * to .cxx/{DEBUG}/x86 so that baselines can be compared even with "stuff" changes (as it is for
 * configuration folding).
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2>.cxxFileVariantSegmentTranslator() =
        hashEquivalents().toTranslateFunction()

/**
 * Sort [NativeModule] with variant names in alphabetic order and ABI names in ordinal order
 * from [Abi].
 */
fun NativeModule.sorted() : NativeModule {
    val module = this
    fun sorted(variant : NativeVariant) = object : NativeVariant by variant {
        override val abis = variant.abis
                .sortedBy { abi -> Abi.getByName(abi.name)!!.ordinal }
    }
    return object : NativeModule by module {
        override val variants = module.variants
                    .map(::sorted)
                    .sortedBy { variant -> variant.name }

    }
}

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
fun ModelBuilderV2.FetchResult<ModelContainerV2>.dump(map:(NativeModule)->NativeModule): String {
    val sb = StringBuilder()
    withCxxFileNormalizer().apply {
        container.infoMaps.forEach { (_, modelMap) ->
            modelMap.forEach { (moduleName, modelInfo) ->
                sb.appendln("[$moduleName]")
                modelInfo.nativeModule?.let {
                    sb.appendln(
                        snapshotModel(
                            modelName = "NativeModule",
                            modelAction = { it },
                            project = this,
                            referenceProject = null
                        ) {
                            snapshotNativeModule()
                        }
                    )
                }
            }
        }
    }
    return sb.toString().trim().replace(System.lineSeparator(), "\n")
}

/**
 * Add a C/C++ path normalizer to this [ModelBuilderV2.FetchResult]
 */
fun ModelBuilderV2.FetchResult<ModelContainerV2>.withCxxFileNormalizer()
        : ModelBuilderV2.FetchResult<ModelContainerV2> {
    val cxxSegmentTranslator = cxxFileVariantSegmentTranslator()
    val normalizer = object : FileNormalizer by normalizer {
        override fun normalize(file: File) =
                cxxSegmentTranslator(normalizer.normalize(file))
    }
    return copy(normalizer = normalizer)
}

fun ModelBuilderV2.FetchResult<ModelContainerV2>.dump() = dump { it }

fun filterByVariantName(vararg names: String) : (NativeModule) -> NativeModule = { nativeModule:NativeModule ->
    object : NativeModule by nativeModule {
        override val variants = nativeModule.variants.filter { names.contains(it.name) }
    }
}

/**
 * Produce all combinations of elements from c1 and c2.
 */
fun cartesianOf(c1:Array<*>, c2:Array<*>) : Array<Array<*>> =
        c1.flatMap { e1 -> c2.map { e2 -> arrayOf(e1, e2) } }.toTypedArray()

/**
 * Produce all combinations of elements from c1, c2, and c3.
 */
fun cartesianOf(c1:Array<*>, c2:Array<*>, c3:Array<*>) : Array<Array<*>> =
        cartesianOf(c1, c2).flatMap { outer ->
            c3.map { e3 -> arrayOf(outer[0], outer[1], e3)}
        }.toTypedArray()

/**
 * Enable C/C++ structured logging for this project.
 */
fun enableCxxStructuredLogging(project : GradleTestProject) {
    val logFolder = getCxxStructuredLogFolder(project.rootProject.projectDir)
    logFolder.mkdirs()
}

/**
 * Given a [GradleTestProject], read structured log records of a
 * particular type (the type returned by [decode] function).
 */
inline fun <reified Encoded, Decoded> GradleTestProject.readStructuredLogs(
    crossinline decode: (Encoded, StringDecoder) -> Decoded) : List<Decoded> {
    val logFolder = getCxxStructuredLogFolder(rootProject.projectDir)
    return readStructuredLogs(logFolder, decode)
}



