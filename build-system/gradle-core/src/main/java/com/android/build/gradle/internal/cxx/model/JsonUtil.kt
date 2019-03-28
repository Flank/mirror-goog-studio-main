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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.StringWriter

/**
 * Write the [CxxAbiModel] to Json string.
 */
fun CxxAbiModel.toJsonString(): String {
    return StringWriter()
        .also { writer -> GSON.toJson(toMutable(), writer) }
        .toString()
}

/**
 * Create a [CxxAbiModel] from Json string.
 */
fun createCxxAbiModelFromJson(json: String): CxxAbiModel {
    return GSON.fromJson(json, MutableCxxAbiModel::class.java)
}

private val GSON = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .registerTypeAdapter(Revision::class.java, RevisionTypeAdapter())
    .setPrettyPrinting()
    .create()

/**
 * [TypeAdapter] that converts between [Revision] and Json string.
 */
private class RevisionTypeAdapter : TypeAdapter<Revision>() {

    override fun write(writer: JsonWriter, revision: Revision) {
        writer.value(revision.toString())
    }

    override fun read(reader: JsonReader): Revision {
        return Revision.parseRevision(reader.nextString())
    }
}

/**
 * Private mutable implementation of [CxxModuleModel] that Gson can use to
 * read and write.
 */
private data class MutableCxxModuleModel(
    override var sdkFolder: File = File("."),
    override var isNativeCompilerSettingsCacheEnabled: Boolean = false,
    override var isBuildOnlyTargetAbiEnabled: Boolean = false,
    override var isSideBySideCmakeEnabled: Boolean = false,
    override var ideBuildTargetAbi: String? = null,
    override var isGeneratePureSplitsEnabled: Boolean = false,
    override var isUniversalApkEnabled: Boolean = false,
    override var splitsAbiFilters: Set<String> = setOf(),
    override var intermediatesFolder: File = File("."),
    override var gradleModulePathName: String = "",
    override var moduleRootFolder: File = File("."),
    override var buildFolder: File = File("."),
    override var makeFile: File = File("."),
    override var buildSystem: NativeBuildSystem = NativeBuildSystem.CMAKE,
    override var cmakeVersion: String? = null,
    override var ndkSymlinkFolder: File? = null,
    override var compilerSettingsCacheFolder: File = File("."),
    override var cxxFolder: File = File("."),
    override var ndkFolder: File = File("."),
    override var ndkVersion: Revision = Revision.parseRevision("0.0.0"),
    override var ndkSupportedAbiList: List<Abi> = listOf(),
    override var ndkDefaultAbiList: List<Abi> = listOf()
) : CxxModuleModel

private fun CxxModuleModel.toMutable() = MutableCxxModuleModel(
    sdkFolder,
    isNativeCompilerSettingsCacheEnabled,
    isBuildOnlyTargetAbiEnabled,
    isSideBySideCmakeEnabled,
    ideBuildTargetAbi,
    isGeneratePureSplitsEnabled,
    isUniversalApkEnabled,
    splitsAbiFilters,
    intermediatesFolder,
    gradleModulePathName,
    moduleRootFolder,
    buildFolder,
    makeFile,
    buildSystem,
    cmakeVersion,
    ndkSymlinkFolder,
    compilerSettingsCacheFolder,
    cxxFolder,
    ndkFolder,
    ndkVersion
)

/**
 * Private mutable implementation of [MutableCxxVariantModel] that Gson can
 * use to read and write.
 */
private data class MutableCxxVariantModel(
    override var module: MutableCxxModuleModel = MutableCxxModuleModel(),
    override var buildSystemArgumentList: List<String> = listOf(),
    override var cFlagList: List<String> = listOf(),
    override var cppFlagsList: List<String> = listOf(),
    override var variantName: String = "",
    override var soFolder: File = File("."),
    override var objFolder: File = File("."),
    override var jsonFolder: File = File("."),
    override var gradleBuildOutputFolder: File = File("."),
    override var isDebuggableEnabled: Boolean = false,
    override var validAbiList: List<Abi> = listOf()
) : CxxVariantModel

private fun CxxVariantModel.toMutable() =
    MutableCxxVariantModel(
        module.toMutable(),
        buildSystemArgumentList,
        cFlagList,
        cppFlagsList,
        variantName,
        soFolder,
        objFolder,
        jsonFolder,
        gradleBuildOutputFolder,
        isDebuggableEnabled,
        validAbiList
    )


/**
 * Private mutable implementation of [CxxAbiModel] that Gson can use
 * to read and write.
 */
private data class MutableCxxAbiModel(
    override var variant: MutableCxxVariantModel = MutableCxxVariantModel(),
    override var abi: Abi = Abi.X86,
    override var abiPlatformVersion: Int = 0,
    override var cxxBuildFolder: File = File("."),
    override var jsonFile: File = File("."),
    override var gradleBuildOutputFolder: File = File("."),
    override var objFolder: File = File("."),
    override var buildCommandFile: File = File("."),
    override var buildOutputFile: File = File("."),
    override var modelOutputFile: File = File("."),
    override var cmake: MutableCxxCmakeAbiModel? = null
) : CxxAbiModel

fun CxxAbiModel.toMutable(): CxxAbiModel = MutableCxxAbiModel(
    variant.toMutable(),
    abi,
    abiPlatformVersion,
    cxxBuildFolder,
    jsonFile,
    gradleBuildOutputFolder,
    objFolder,
    buildCommandFile,
    buildOutputFile,
    modelOutputFile,
    cmake?.toMutable()
)

/**
 * Private mutable implementation of [CxxCmakeAbiModel] that Gson can use
 * to read and write.
 */
private data class MutableCxxCmakeAbiModel(
    override var cmakeListsWrapperFile: File = File("."),
    override var toolchainWrapperFile: File = File("."),
    override var buildGenerationStateFile: File = File("."),
    override var cacheKeyFile: File = File("."),
    override var compilerCacheUseFile: File = File("."),
    override var compilerCacheWriteFile: File = File("."),
    override var toolchainSettingsFromCacheFile: File = File(".")
) : CxxCmakeAbiModel

private fun CxxCmakeAbiModel.toMutable() = MutableCxxCmakeAbiModel(
    cmakeListsWrapperFile,
    toolchainWrapperFile,
    buildGenerationStateFile,
    cacheKeyFile,
    compilerCacheUseFile,
    compilerCacheWriteFile,
    toolchainSettingsFromCacheFile
)

