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
import com.android.build.gradle.internal.cxx.services.CxxServiceRegistry
import com.android.build.gradle.internal.ndk.Stl
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
        .also { writer -> GSON.toJson(toData(), writer) }
        .toString()
}

/**
 * Create a [CxxAbiModel] from Json string.
 */
fun createCxxAbiModelFromJson(json: String): CxxAbiModel {
    return GSON.fromJson(json, CxxAbiModelData::class.java)
}

/**
 * Write model to JSON file.
 */
fun CxxAbiModel.writeJsonToFile() {
    modelOutputFile.parentFile.mkdirs()
    modelOutputFile.writeText(toJsonString())
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
 * Private data-backed implementation of [CxxModuleModel] that Gson can
 * use to read and write.
 */
// TODO Can the Cxx*Data classes be automated or otherwise removed while still
// TODO retaining JSON read/write? They're a pain to maintain.
private data class CxxModuleModelData(
    override val rootBuildGradleFolder: File = File("."),
    override val sdkFolder: File = File("."),
    override val isNativeCompilerSettingsCacheEnabled: Boolean = false,
    override val isBuildOnlyTargetAbiEnabled: Boolean = false,
    override val ideBuildTargetAbi: String? = null,
    override val splitsAbiFilterSet: Set<String> = setOf(),
    override val intermediatesFolder: File = File("."),
    override val gradleModulePathName: String = "",
    override val moduleRootFolder: File = File("."),
    override val makeFile: File = File("."),
    override val buildSystem: NativeBuildSystem = NativeBuildSystem.CMAKE,
    override val compilerSettingsCacheFolder: File = File("."),
    override val cxxFolder: File = File("."),
    override val ndkFolder: File = File("."),
    override val ndkVersion: Revision = Revision.parseRevision("0.0.0"),
    override val ndkSupportedAbiList: List<Abi> = listOf(),
    override val ndkDefaultAbiList: List<Abi> = listOf(),
    override val cmake: CxxCmakeModuleModel? = null,
    override val cmakeToolchainFile: File = File("."),
    override val stlSharedObjectMap: Map<Stl, Map<Abi, File>> = emptyMap()
) : CxxModuleModel {
    override val services: CxxServiceRegistry
        get() = throw RuntimeException("Cannot use services from deserialized CxxModuleModel")
}

private fun CxxModuleModel.toData() = CxxModuleModelData(
    rootBuildGradleFolder = rootBuildGradleFolder,
    sdkFolder = sdkFolder,
    isNativeCompilerSettingsCacheEnabled = isNativeCompilerSettingsCacheEnabled,
    isBuildOnlyTargetAbiEnabled = isBuildOnlyTargetAbiEnabled,
    ideBuildTargetAbi = ideBuildTargetAbi,
    splitsAbiFilterSet = splitsAbiFilterSet,
    intermediatesFolder = intermediatesFolder,
    gradleModulePathName = gradleModulePathName,
    moduleRootFolder = moduleRootFolder,
    makeFile = makeFile,
    buildSystem = buildSystem,
    compilerSettingsCacheFolder = compilerSettingsCacheFolder,
    cxxFolder = cxxFolder,
    ndkFolder = ndkFolder,
    ndkVersion = ndkVersion,
    ndkSupportedAbiList = ndkSupportedAbiList,
    ndkDefaultAbiList = ndkDefaultAbiList,
    cmake = cmake,
    stlSharedObjectMap = stlSharedObjectMap
)

/**
 * Private data-backed implementation of [CxxVariantModel] that Gson can
 * use to read and write.
 */
private data class CxxVariantModelData(
    override val module: CxxModuleModelData = CxxModuleModelData(),
    override val buildSystemArgumentList: List<String> = listOf(),
    override val cFlagList: List<String> = listOf(),
    override val cppFlagsList: List<String> = listOf(),
    override val variantName: String = "",
    override val soFolder: File = File("."),
    override val objFolder: File = File("."),
    override val jsonFolder: File = File("."),
    override val gradleBuildOutputFolder: File = File("."),
    override val isDebuggableEnabled: Boolean = false,
    override val validAbiList: List<Abi> = listOf(),
    override val buildTargetSet: Set<String> = setOf()
) : CxxVariantModel

private fun CxxVariantModel.toData() =
    CxxVariantModelData(
        module = module.toData(),
        buildSystemArgumentList = buildSystemArgumentList,
        cFlagList = cFlagList,
        cppFlagsList = cppFlagsList,
        variantName = variantName,
        soFolder = soFolder,
        objFolder = objFolder,
        jsonFolder = jsonFolder,
        gradleBuildOutputFolder = gradleBuildOutputFolder,
        isDebuggableEnabled = isDebuggableEnabled,
        validAbiList = validAbiList,
        buildTargetSet = buildTargetSet
    )

/**
 * Private data-backed implementation of [CxxAbiModel] that Gson can use
 * to read and write.
 */
private data class CxxAbiModelData(
    override val variant: CxxVariantModelData = CxxVariantModelData(),
    override val abi: Abi = Abi.X86,
    override val abiPlatformVersion: Int = 0,
    override val cxxBuildFolder: File = File("."),
    override val jsonFile: File = File("."),
    override val gradleBuildOutputFolder: File = File("."),
    override val objFolder: File = File("."),
    override val buildCommandFile: File = File("."),
    override val buildOutputFile: File = File("."),
    override val modelOutputFile: File = File("."),
    override val cmake: CxxCmakeAbiModelData? = null,
    override val jsonGenerationLoggingRecordFile: File = File("."),
    override val compileCommandsJsonFile: File = File(".")
) : CxxAbiModel

private fun CxxAbiModel.toData(): CxxAbiModel = CxxAbiModelData(
    variant = variant.toData(),
    abi = abi,
    abiPlatformVersion = abiPlatformVersion,
    cxxBuildFolder = cxxBuildFolder,
    jsonFile = jsonFile,
    gradleBuildOutputFolder = gradleBuildOutputFolder,
    objFolder = objFolder,
    buildCommandFile = buildCommandFile,
    buildOutputFile = buildOutputFile,
    modelOutputFile = modelOutputFile,
    jsonGenerationLoggingRecordFile = jsonGenerationLoggingRecordFile,
    compileCommandsJsonFile = compileCommandsJsonFile,
    cmake = cmake?.toData()
)

/**
 * Private data-backed implementation of [CxxCmakeAbiModel] that Gson can use
 * to read and write.
 */
private data class CxxCmakeAbiModelData(
    override val cmakeListsWrapperFile: File = File("."),
    override val toolchainWrapperFile: File = File("."),
    override val buildGenerationStateFile: File = File("."),
    override val cacheKeyFile: File = File("."),
    override val compilerCacheUseFile: File = File("."),
    override val compilerCacheWriteFile: File = File("."),
    override val toolchainSettingsFromCacheFile: File = File("."),
    override val cmakeWrappingBaseFolder: File = File(".")
) : CxxCmakeAbiModel

private fun CxxCmakeAbiModel.toData() = CxxCmakeAbiModelData(
    cmakeListsWrapperFile = cmakeListsWrapperFile,
    toolchainWrapperFile = toolchainWrapperFile,
    buildGenerationStateFile = buildGenerationStateFile,
    cacheKeyFile = cacheKeyFile,
    compilerCacheUseFile = compilerCacheUseFile,
    compilerCacheWriteFile = compilerCacheWriteFile,
    toolchainSettingsFromCacheFile = toolchainSettingsFromCacheFile

)

