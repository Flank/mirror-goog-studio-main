/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("R8Tool")

package com.android.builder.dexing

import com.android.SdkConstants
import com.android.ide.common.blame.MessageReceiver
import com.android.tools.r8.ClassFileConsumer
import com.android.tools.r8.CompatProguardCommandBuilder
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.DataResourceConsumer
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.origin.Origin
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Converts the specified inputs, according to the configuration, and writes dex or classes to
 * output path.
 */
fun runR8(
    inputs: Collection<Path>,
    output: Path,
    libraries: Collection<Path>,
    toolConfig: ToolConfig,
    proguardConfig: ProguardConfig,
    mainDexListConfig: MainDexListConfig,
    messageReceiver: MessageReceiver
) {
    val r8CommandBuilder = CompatProguardCommandBuilder(true, D8DiagnosticsHandler(messageReceiver))

    if (toolConfig.minSdkVersion < 21) {
        // specify main dex related options only when minSdkVersion is below 21
        r8CommandBuilder
            .addMainDexRulesFiles(mainDexListConfig.mainDexRulesFiles)
            .addMainDexListFiles(mainDexListConfig.mainDexListFiles)

        if (mainDexListConfig.mainDexRules.isNotEmpty()) {
            r8CommandBuilder.addMainDexRules(mainDexListConfig.mainDexRules, Origin.unknown())
        }
    }

    r8CommandBuilder
        .addProguardConfigurationFiles(
                proguardConfig.proguardConfigurationFiles.filter { Files.isRegularFile(it) }
        )
        .addProguardConfiguration(proguardConfig.proguardConfigurations, Origin.unknown())

    if (proguardConfig.proguardMapInput != null
            && Files.exists(proguardConfig.proguardMapInput)) {
        r8CommandBuilder.addProguardConfiguration(
                listOf("-applymapping " + proguardConfig.proguardMapInput.toString()),
                Origin.unknown()
        )
    }

    if (proguardConfig.proguardMapOutput != null) {
        Files.createDirectories(proguardConfig.proguardMapOutput.parent)
        r8CommandBuilder.setProguardMapOutputPath(proguardConfig.proguardMapOutput)
    }

    val compilationMode =
            if (toolConfig.isDebuggable) CompilationMode.DEBUG else CompilationMode.RELEASE
    val outputType =
            if (toolConfig.r8OutputType == R8OutputType.CLASSES) {
                OutputMode.ClassFile
            } else {
                OutputMode.DexIndexed
            }
    @Suppress("UsePropertyAccessSyntax")
    r8CommandBuilder
        .setDisableMinification(toolConfig.disableMinification)
        .setDisableTreeShaking(toolConfig.disableTreeShaking)
        .setDisableDesugaring(toolConfig.disableDesugaring)
        .setMinApiLevel(toolConfig.minSdkVersion)
        .setMode(compilationMode)
        .setOutput(output, outputType)

    // By default, R8 will maintain Java data resources. Wrap the consumer to ignore them.
    val outputConsumer = r8CommandBuilder.programConsumer
    when (outputConsumer) {
        is ClassFileConsumer ->
            r8CommandBuilder.programConsumer =
                    object : ClassFileConsumer.ForwardingConsumer(outputConsumer) {
                        override fun getDataResourceConsumer(): DataResourceConsumer? {
                            return null
                        }
                    }
        is DexIndexedConsumer ->
            r8CommandBuilder.programConsumer =
                    object : DexIndexedConsumer.ForwardingConsumer(outputConsumer) {
                        override fun getDataResourceConsumer(): DataResourceConsumer? {
                            return null
                        }
                    }
    }

    fun pathsAdder(paths: Collection<Path>, consumer: (Path) -> Any) {
        for (path in paths) {
            when {
                Files.isRegularFile(path) -> consumer(path)
                Files.isDirectory(path) -> Files.walk(path).use {
                    it
                        .filter { p -> p.toString().endsWith(SdkConstants.DOT_CLASS) }
                        .forEach { consumer(it) }
                }
                else -> throw IOException("Unexpected file format: " + path.toString())
            }
        }
    }

    pathsAdder(inputs, { p -> r8CommandBuilder.addProgramFiles(p) })
    pathsAdder(libraries, { p -> r8CommandBuilder.addLibraryFiles(p) })

    val logger: Logger = Logger.getLogger("R8")
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("*** Using R8 to process code ***")
        logger.fine("Main dex list config: " + mainDexListConfig)
        logger.fine("Proguard config: " + proguardConfig)
        logger.fine("Tool config: " + toolConfig)
        logger.fine("Program classes: " + inputs)
        logger.fine("Library classes: " + libraries)
    }

    R8.run(r8CommandBuilder.build())
}

enum class R8OutputType {
    DEX,
    CLASSES,
}

/** Main dex related parameters for the R8 tool. */
data class MainDexListConfig(
    val mainDexRulesFiles: Collection<Path> = listOf(),
    val mainDexListFiles: Collection<Path> = listOf(),
    val mainDexRules: List<String> = listOf()
)

/** Proguard-related parameters for the R8 tool. */
data class ProguardConfig(
    val proguardConfigurationFiles: List<Path>,
    val proguardMapOutput: Path?,
    val proguardMapInput: Path?,
    val proguardConfigurations: List<String>
)

/** Configuration parameters for the R8 tool. */
data class ToolConfig(
    val minSdkVersion: Int,
    val isDebuggable: Boolean,
    val disableTreeShaking: Boolean,
    val disableDesugaring: Boolean,
    val disableMinification: Boolean,
    val r8OutputType: R8OutputType
)