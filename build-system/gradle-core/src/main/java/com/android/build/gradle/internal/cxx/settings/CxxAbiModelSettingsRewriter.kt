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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_FIND_ROOT_PATH
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_BUILD_SCRIPT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_CFLAGS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_CPPFLAGS
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_PLATFORM
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_APPLICATION_MK
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_DEBUG
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_LIBS_OUT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_OUT
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_PROJECT_PATH
import com.android.build.gradle.internal.cxx.configure.getCmakeGenerator
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.configure.onlyKeepProperties
import com.android.build.gradle.internal.cxx.configure.onlyKeepUnknownArguments
import com.android.build.gradle.internal.cxx.configure.parseCmakeCommandLine
import com.android.build.gradle.internal.cxx.configure.removeBlankProperties
import com.android.build.gradle.internal.cxx.configure.removeSubsumedArguments
import com.android.build.gradle.internal.cxx.configure.toCmakeArgument
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.hashing.toBase36
import com.android.build.gradle.internal.cxx.hashing.update
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.shouldGeneratePrefabPackages
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CPP_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_C_FLAGS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_DEFAULT_BUILD_TYPE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PREFAB_PATH
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.cxx.CxxDiagnosticCode.NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION
import com.android.utils.tokenizeCommandLineToEscaped
import com.google.common.collect.Lists
import java.io.File
import java.security.MessageDigest

/**
 * If there is a CMakeSettings.json then replace relevant model values with settings from it.
 */
fun CxxAbiModel.rewriteCxxAbiModelWithCMakeSettings() : CxxAbiModel {

    val original = this
    if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
        val rewriteConfig by lazy {
            getCxxAbiRewriteModel()
        }

        val configuration by lazy {
            rewriteConfig.configuration
        }

        val cmakeModule = original.variant.module.cmake!!.copy(
                cmakeExe = configuration.cmakeExecutable.toFile()
        )
        val module = original.variant.module.copy(
                cmake = cmakeModule,
                cmakeToolchainFile = configuration.cmakeToolchain.toFile()!!
        )
        val variant = original.variant.copy(
                module = module
        )
        val cmakeAbi = original.cmake?.copy(
                effectiveConfiguration = configuration
        )
        return original.copy(
                variant = ({ variant })(),
                cmake = ({ cmakeAbi })(),
                cxxBuildFolder = ({ configuration.buildRoot.toFile()!! })(),
                buildSettings = ({ rewriteConfig.buildSettings }
                        )()
        )
    } else {
//        TODO(jomof) separate CMake-ness from macro expansion and add it to NDK build
//        return original.replaceWith(
//            cmake = { cmake },
//            variant = { variant },
//            cxxBuildFolder = { cxxBuildFolder },
//            buildSettings = { rewriteModel.buildSettings }
//        )
        return this
    }
}

/**
 * Turn a string into a File with null propagation.
 */
private fun String?.toFile() = if (this != null) File(this) else null

/**
 * Build the CMake command line arguments from [CxxAbiModel] and resolve macros in
 * CMakeSettings.json and BuildSettings.json
 */
private fun CxxAbiModel.getCxxAbiRewriteModel() : RewriteConfiguration {
    val allSettings = gatherCMakeSettingsFromAllLocations()
            .expandInheritEnvironmentMacros(this)
    val resolver = SettingsEnvironmentNameResolver(allSettings.environments)

    // Accumulate configuration values with later values replacing earlier values
    // when not null.
    fun SettingsConfiguration.accumulate(configuration : SettingsConfiguration?) : SettingsConfiguration {
        if (configuration == null) return this
        return SettingsConfiguration(
                name = configuration.name ?: name,
                description = configuration.description ?: description,
                generator = configuration.generator ?: generator,
                configurationType = configurationType,
                inheritEnvironments = configuration.inheritEnvironments,
                buildRoot = configuration.buildRoot ?: buildRoot,
                installRoot = configuration.installRoot ?: installRoot,
                cmakeCommandArgs = configuration.cmakeCommandArgs ?: cmakeCommandArgs,
                cmakeToolchain = configuration.cmakeToolchain ?: cmakeToolchain,
                cmakeExecutable = configuration.cmakeExecutable ?: cmakeExecutable,
                buildCommandArgs = configuration.buildCommandArgs ?: buildCommandArgs,
                ctestCommandArgs = configuration.ctestCommandArgs ?: ctestCommandArgs,
                variables = variables + configuration.variables
        )
    }

    fun SettingsConfiguration.accumulate(arguments : List<CommandLineArgument>) : SettingsConfiguration {
        return copy(
                configurationType =
                when (arguments.getCmakeProperty(CMAKE_BUILD_TYPE)) {
                    null -> configurationType
                    else -> null
                },
                cmakeToolchain =
                when (arguments.getCmakeProperty(CMAKE_TOOLCHAIN_FILE)) {
                    null -> cmakeToolchain
                    else -> null
                },
                generator = arguments.getCmakeGenerator() ?: generator,
                variables = variables +
                        arguments.onlyKeepProperties().map {
                            SettingsConfigurationVariable(it.propertyName, it.propertyValue)
                        },
                cmakeCommandArgs = arguments.onlyKeepUnknownArguments().let { unknowns ->
                    if (unknowns.isEmpty()) cmakeCommandArgs
                    else (cmakeCommandArgs ?: "") + unknowns.joinToString(" ") { it.sourceArgument }
                }
        )
    }

    fun getSettingsConfiguration(configurationName : String) : SettingsConfiguration? {
        val configuration = allSettings.configurations
                .firstOrNull { it.name == configurationName } ?: return null
        return reifyRequestedConfiguration(resolver, configuration)
    }

    // First, set up the traditional environment. If the user has also requested a specific
    // CMakeSettings.json environment then values from that will overwrite these.
    val combinedConfiguration = getSettingsConfiguration(TRADITIONAL_CONFIGURATION_NAME)!!
            .accumulate(getSettingsConfiguration(variant.cmakeSettingsConfiguration))
    val configuration = combinedConfiguration
            .accumulate(combinedConfiguration.getCmakeCommandLineArguments())
            .accumulate(variant.buildSystemArgumentList.toCmakeArguments())

    // Translate to [CommandLineArgument]. Be sure that user variables from build.gradle get
    // passed after settings variables
    val configurationArguments = configuration.getCmakeCommandLineArguments()

    val hashInvariantCommandLineArguments =
            configurationArguments.removeSubsumedArguments().removeBlankProperties()

    // Compute a hash of the command-line arguments
    val digest = MessageDigest.getInstance("SHA-256")
    hashInvariantCommandLineArguments.forEach { argument ->
        digest.update(argument.sourceArgument)
    }
    val configurationHash = digest.toBase36()

    // All arguments
    val all = getCmakeCommandLineArguments() + configurationArguments

    // Fill in the ABI and configuration hash properties
    fun String.reify() = reifyString(this) { tokenMacro ->
        when(tokenMacro) {
            NDK_ABI.qualifiedName -> abi.tag
            NDK_CONFIGURATION_HASH.qualifiedName -> configurationHash.substring(0, 8)
            NDK_FULL_CONFIGURATION_HASH.qualifiedName -> configurationHash
            else -> resolver.resolve(tokenMacro, configuration.inheritEnvironments)
        }
    }!!

    val arguments = all.map { argument ->
        argument.sourceArgument.reify().toCmakeArgument()
    }.removeSubsumedArguments().removeBlankProperties()

    val expandedBuildSettings = BuildSettingsConfiguration(
            environmentVariables = buildSettings.environmentVariables.map {
                EnvironmentVariable(
                        name = it.name.reify(),
                        value = it.value?.reify()
                )
            }
    )

    return RewriteConfiguration(
            buildSettings = expandedBuildSettings,
            configuration = SettingsConfiguration(
                    name = configuration.name,
                    description = "Composite reified CMakeSettings configuration",
                    generator = arguments.getCmakeGenerator(),
                    configurationType = configuration.configurationType,
                    inheritEnvironments = configuration.inheritEnvironments,
                    buildRoot = configuration.buildRoot?.reify(),
                    installRoot = configuration.installRoot?.reify(),
                    cmakeToolchain = arguments.getCmakeProperty(CMAKE_TOOLCHAIN_FILE),
                    cmakeCommandArgs = configuration.cmakeCommandArgs?.reify(),
                    cmakeExecutable = configuration.cmakeExecutable?.reify(),
                    buildCommandArgs = configuration.buildCommandArgs?.reify(),
                    ctestCommandArgs = configuration.ctestCommandArgs?.reify(),
                    variables = arguments.mapNotNull {
                        when (it) {
                            is CommandLineArgument.DefineProperty -> SettingsConfigurationVariable(
                                    it.propertyName,
                                    it.propertyValue
                            )
                            else -> null
                        }
                    }
            )
    )
}

fun SettingsConfiguration.getCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    if (configurationType != null) {
        result += "-D$CMAKE_BUILD_TYPE=$configurationType".toCmakeArgument()
    }
    if (cmakeToolchain != null) {
        result +="-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchain".toCmakeArgument()
    }
    result += variables.map { (name, value) -> "-D$name=$value".toCmakeArgument() }
    if (buildRoot != null) {
        result += "-B$buildRoot".toCmakeArgument()
    }
    if (generator != null) {
        result += "-G$generator".toCmakeArgument()
    }

    if (cmakeCommandArgs != null) {
        result += parseCmakeCommandLine(cmakeCommandArgs)
    }
    return result.removeSubsumedArguments().removeBlankProperties()
}

fun CxxAbiModel.getCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    result += "-H${variant.module.makeFile.parentFile}".toCmakeArgument()
    result += "-B${resolveMacroValue(NDK_BUILD_ROOT)}".toCmakeArgument()

    result += if (variant.module.cmake!!.minimumCmakeVersion.isCmakeForkVersion()) {
        "-GAndroid Gradle - Ninja".toCmakeArgument()
    } else {
        "-GNinja".toCmakeArgument()
    }
    result += "-D$CMAKE_BUILD_TYPE=${resolveMacroValue(NDK_DEFAULT_BUILD_TYPE)}".toCmakeArgument()
    result += "-D$CMAKE_TOOLCHAIN_FILE=${resolveMacroValue(NDK_CMAKE_TOOLCHAIN)}".toCmakeArgument()
    result += "-D$CMAKE_CXX_FLAGS=${resolveMacroValue(NDK_CPP_FLAGS)}".toCmakeArgument()
    result += "-D$CMAKE_C_FLAGS=${resolveMacroValue(NDK_C_FLAGS)}".toCmakeArgument()

    if (shouldGeneratePrefabPackages()) {
        // This can be passed a few different ways:
        // https://cmake.org/cmake/help/latest/command/find_package.html#search-procedure
        //
        // <PACKAGE_NAME>_ROOT would probably be best, but it's not supported until 3.12, and we support
        // CMake 3.6.
        result += "-D$CMAKE_FIND_ROOT_PATH=${resolveMacroValue(NDK_PREFAB_PATH)}".toCmakeArgument()
    }

    return result.removeSubsumedArguments().removeBlankProperties()
}

fun CxxAbiModel.getFinalCmakeCommandLineArguments() : List<CommandLineArgument> {
    val result = mutableListOf<CommandLineArgument>()
    result += getCmakeCommandLineArguments()
    result += cmake!!.effectiveConfiguration.getCmakeCommandLineArguments()
    return result.removeSubsumedArguments().removeBlankProperties()
}

/**
 * This is "our" version of the command-line arguments for ndk-build.
 * The user may override or enhance with arguments from build.gradle.
 */
fun CxxAbiModel.getNdkBuildCommandLine(): List<String> {
    val makeFile =
            if (variant.module.makeFile.isDirectory) {
                File(variant.module.makeFile, "Android.mk")
            } else variant.module.makeFile
    val applicationMk = File(makeFile.parent, "Application.mk").takeIf { it.exists() }

    val result: MutableList<String> = Lists.newArrayList()
    result.add("$NDK_PROJECT_PATH=null")
    result.add("$APP_BUILD_SCRIPT=$makeFile")
    // NDK_APPLICATION_MK specifies the Application.mk file.
    applicationMk?.let {
        result.add("$NDK_APPLICATION_MK=" + it.absolutePath)
    }
    if (shouldGeneratePrefabPackages()) {
        if (variant.module.ndkVersion.major < 21) {
            // These cannot be automatically imported prior to NDK r21 which started handling
            // NDK_GRADLE_INJECTED_IMPORT_PATH, but the user can add that search path explicitly
            // for older releases.
            // TODO(danalbert): Include a link to the docs page when it is published.
            // This can be worked around on older NDKs, but it's too verbose to include in the
            // warning message.
            warnln(
                    NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION,
                    "Prefab packages cannot be automatically imported until NDK r21."
            )
        }
        result.add("NDK_GRADLE_INJECTED_IMPORT_PATH=" + prefabFolder.toString())
    }

    // APP_ABI and NDK_ALL_ABIS work together. APP_ABI is the specific ABI for this build.
    // NDK_ALL_ABIS is the universe of all ABIs for this build. NDK_ALL_ABIS is set to just the
    // current ABI. If we don't do this, then ndk-build will erase build artifacts for all abis
    // aside from the current.
    result.add("APP_ABI=" + abi.tag)
    result.add("NDK_ALL_ABIS=" + abi.tag)
    if (variant.isDebuggableEnabled) {
        result.add("$NDK_DEBUG=1")
    } else {
        result.add("$NDK_DEBUG=0")
    }
    result.add("$APP_PLATFORM=android-$abiPlatformVersion")
    result.add("$NDK_OUT=${variant.intermediatesFolder}/obj")
    result.add("$NDK_LIBS_OUT=${variant.intermediatesFolder}/lib")

    // Related to issuetracker.google.com/69110338. Semantics of APP_CFLAGS and APP_CPPFLAGS
    // is that the flag(s) are unquoted. User may place quotes if it is appropriate for the
    // target compiler. User in this case is build.gradle author of
    // externalNativeBuild.ndkBuild.cppFlags or the author of Android.mk.
    for (flag in variant.cFlagsList) {
        result.add("$APP_CFLAGS+=$flag")
    }
    for (flag in variant.cppFlagsList) {
        result.add("$APP_CPPFLAGS+=$flag")
    }
    result.addAll(variant.buildSystemArgumentList)
    return result
}

/*
* Returns the Ninja build commands from CMakeSettings.json.
* Returns an empty string if it does not exist.
*/
fun CxxAbiModel.getBuildCommandArguments(): List<String> {
    return cmake?.effectiveConfiguration?.buildCommandArgs?.tokenizeCommandLineToEscaped()
            ?: emptyList()
}

/**
 * This model contains the inner models of [CxxAbiModel] that are rewritten during
 * clean/build for CMake builds.
 */
private class RewriteConfiguration(
        val buildSettings: BuildSettingsConfiguration,
        val configuration: SettingsConfiguration
)
