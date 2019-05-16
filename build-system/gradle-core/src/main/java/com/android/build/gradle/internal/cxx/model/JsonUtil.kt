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
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.cxx.services.CxxServiceRegistry
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import com.google.common.annotations.VisibleForTesting
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
 * Private data-backed implementation of [CxxProjectModel] that Gson can
 * use to read and write.
 */
@VisibleForTesting
data class CxxProjectModelData(
    override val compilerSettingsCacheFolder: File = File("."),
    override val cxxFolder: File = File("."),
    override val ideBuildTargetAbi: String? = null,
    override val isBuildOnlyTargetAbiEnabled: Boolean = false,
    override val isCmakeBuildCohabitationEnabled: Boolean = false,
    override val isNativeCompilerSettingsCacheEnabled: Boolean = false,
    override val rootBuildGradleFolder: File = File("."),
    override val sdkFolder: File = File(".")
) : CxxProjectModel

private fun CxxProjectModel.toData() = CxxProjectModelData(
    compilerSettingsCacheFolder = compilerSettingsCacheFolder,
    cxxFolder = cxxFolder,
    ideBuildTargetAbi = ideBuildTargetAbi,
    isBuildOnlyTargetAbiEnabled = isBuildOnlyTargetAbiEnabled,
    isCmakeBuildCohabitationEnabled = isCmakeBuildCohabitationEnabled,
    isNativeCompilerSettingsCacheEnabled = isNativeCompilerSettingsCacheEnabled,
    rootBuildGradleFolder = rootBuildGradleFolder,
    sdkFolder = sdkFolder
)

/**
 * Private data-backed implementation of [CxxModuleModel] that Gson can
 * use to read and write.
 */
// TODO Can the Cxx*Data classes be automated or otherwise removed while still
// TODO retaining JSON read/write? They're a pain to maintain.
@VisibleForTesting
data class CxxModuleModelData(
    override val buildSystem: NativeBuildSystem = NativeBuildSystem.CMAKE,
    override val cmake: CxxCmakeModuleModelData? = null,
    override val cmakeToolchainFile: File = File("."),
    override val cxxFolder: File = File("."),
    override val gradleModulePathName: String = "",
    override val intermediatesFolder: File = File("."),
    override val makeFile: File = File("."),
    override val moduleRootFolder: File = File("."),
    override val ndkDefaultAbiList: List<Abi> = listOf(),
    override val ndkFolder: File = File("."),
    override val ndkMetaAbiList: List<AbiInfo> = listOf(),
    override val ndkMetaPlatforms: NdkMetaPlatforms? = NdkMetaPlatforms(),
    override val ndkSupportedAbiList: List<Abi> = listOf(),
    override val ndkVersion: Revision = Revision.parseRevision("0.0.0"),
    override val project: CxxProjectModelData = CxxProjectModelData(),
    override val splitsAbiFilterSet: Set<String> = setOf(),
    override val stlSharedObjectMap: Map<Stl, Map<Abi, File>> = emptyMap()
) : CxxModuleModel {
    override val services: CxxServiceRegistry
        get() = throw RuntimeException("Cannot use services from deserialized CxxModuleModel")
}

private fun CxxModuleModel.toData() = CxxModuleModelData(
    buildSystem = buildSystem,
    cmake = cmake?.toData(),
    cmakeToolchainFile = cmakeToolchainFile,
    cxxFolder = cxxFolder,
    gradleModulePathName = gradleModulePathName,
    intermediatesFolder = intermediatesFolder,
    makeFile = makeFile,
    moduleRootFolder = moduleRootFolder,
    ndkDefaultAbiList = ndkDefaultAbiList,
    ndkFolder = ndkFolder,
    ndkMetaAbiList = ndkMetaAbiList,
    ndkMetaPlatforms = ndkMetaPlatforms,
    ndkSupportedAbiList = ndkSupportedAbiList,
    ndkVersion = ndkVersion,
    project = project.toData(),
    splitsAbiFilterSet = splitsAbiFilterSet,
    stlSharedObjectMap = stlSharedObjectMap
)

@VisibleForTesting
data class CxxCmakeModuleModelData(
    override val cmakeExe: File,
    override val foundCmakeVersion: Revision,
    override val ninjaExe: File
) : CxxCmakeModuleModel

private fun CxxCmakeModuleModel.toData() =
    CxxCmakeModuleModelData(
        cmakeExe = cmakeExe,
        foundCmakeVersion = foundCmakeVersion,
        ninjaExe = ninjaExe
    )

/**
 * Private data-backed implementation of [CxxVariantModel] that Gson can
 * use to read and write.
 */
@VisibleForTesting
internal data class CxxVariantModelData(
    override val buildSystemArgumentList: List<String> = listOf(),
    override val buildTargetSet: Set<String> = setOf(),
    override val cFlagsList: List<String> = listOf(),
    override val cppFlagsList: List<String> = listOf(),
    override val gradleBuildOutputFolder: File = File("."),
    override val isDebuggableEnabled: Boolean = false,
    override val jsonFolder: File = File("."),
    override val module: CxxModuleModelData = CxxModuleModelData(),
    override val objFolder: File = File("."),
    override val soFolder: File = File("."),
    override val validAbiList: List<Abi> = listOf(),
    override val variantName: String = ""
) : CxxVariantModel

private fun CxxVariantModel.toData() =
    CxxVariantModelData(
        buildSystemArgumentList = buildSystemArgumentList,
        buildTargetSet = buildTargetSet,
        cFlagsList = cFlagsList,
        cppFlagsList = cppFlagsList,
        gradleBuildOutputFolder = gradleBuildOutputFolder,
        isDebuggableEnabled = isDebuggableEnabled,
        jsonFolder = jsonFolder,
        module = module.toData(),
        objFolder = objFolder,
        soFolder = soFolder,
        validAbiList = validAbiList,
        variantName = variantName
    )

/**
 * Private data-backed implementation of [CxxAbiModel] that Gson can use
 * to read and write.
 */
@VisibleForTesting
internal data class CxxAbiModelData(
    override val abi: Abi = Abi.X86,
    override val abiPlatformVersion: Int = 0,
    override val buildCommandFile: File = File("."),
    override val buildOutputFile: File = File("."),
    override val cmake: CxxCmakeAbiModelData? = null,
    override val cxxBuildFolder: File = File("."),
    override val info: AbiInfo = AbiInfo(),
    override val jsonFile: File = File("."),
    override val jsonGenerationLoggingRecordFile: File = File("."),
    override val modelOutputFile: File = File("."),
    override val objFolder: File = File("."),
    override val soFolder: File = File("."),
    override val variant: CxxVariantModelData = CxxVariantModelData()
) : CxxAbiModel

private fun CxxAbiModel.toData(): CxxAbiModel = CxxAbiModelData(
    abi = abi,
    abiPlatformVersion = abiPlatformVersion,
    buildCommandFile = buildCommandFile,
    buildOutputFile = buildOutputFile,
    cmake = cmake?.toData(),
    cxxBuildFolder = cxxBuildFolder,
    jsonFile = jsonFile,
    jsonGenerationLoggingRecordFile = jsonGenerationLoggingRecordFile,
    modelOutputFile = modelOutputFile,
    objFolder = objFolder,
    soFolder = soFolder,
    variant = variant.toData()
)

/**
 * Private data-backed implementation of [CxxCmakeAbiModel] that Gson can use
 * to read and write.
 */
@VisibleForTesting
internal data class CxxCmakeAbiModelData(
    override val buildGenerationStateFile: File,
    override val cacheKeyFile: File,
    override val cmakeArtifactsBaseFolder: File,
    override val cmakeListsWrapperFile: File,
    override val cmakeWrappingBaseFolder: File,
    override val compileCommandsJsonFile: File,
    override val compilerCacheUseFile: File,
    override val compilerCacheWriteFile: File,
    override val toolchainSettingsFromCacheFile: File,
    override val toolchainWrapperFile: File
) : CxxCmakeAbiModel

private fun CxxCmakeAbiModel.toData() = CxxCmakeAbiModelData(
    buildGenerationStateFile = buildGenerationStateFile,
    cacheKeyFile = cacheKeyFile,
    cmakeArtifactsBaseFolder = cmakeArtifactsBaseFolder,
    cmakeListsWrapperFile = cmakeListsWrapperFile,
    cmakeWrappingBaseFolder = cmakeWrappingBaseFolder,
    compileCommandsJsonFile = compileCommandsJsonFile,
    compilerCacheUseFile = compilerCacheUseFile,
    compilerCacheWriteFile = compilerCacheWriteFile,
    toolchainSettingsFromCacheFile = toolchainSettingsFromCacheFile,
    toolchainWrapperFile = toolchainWrapperFile
)

