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

@file:JvmName("DesugarLibUtils")

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.Provider
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream

private const val DESUGAR_LIB_CONFIGURATION_NAME = "_internal_desugar_jdk_libs"
private const val DESUGAR_LIB_CONFIG_CONFIGURATION_NAME = "_internal_desugar_jdk_libs_configuration"
private const val DESUGAR_LIB_CONFIG_FILE = "desugar.json"
// The output of L8 invocation, which is the dex output of desugar_jdk_libs.jar
const val DESUGAR_LIB_DEX = "_internal-desugar-jdk-libs-dex"
// The output of DesugarLibConfigExtractor which unzips desugar_jdk_libs_configuration.jar
const val DESUGAR_LIB_CONFIG = "_internal-desugar-jdk-libs-config"

/**
 * Returns a file collection which contains desugar_jdk_libs.jar
 */
fun getDesugarLibJarFromMaven(project: Project): FileCollection {
    val existingConfig = project.configurations.findByName(DESUGAR_LIB_CONFIGURATION_NAME)
    if (existingConfig != null) {
        return getArtifactCollection(existingConfig)
    }
    val config = createDesugarLibConfiguration(project)
    return getArtifactCollection(config)
}


/**
 * Returns a file collection which contains desugar_jdk_libs.jar's dex file generated
 * by artifact transform
 */
fun getDesugarLibDexFromTransform(variantScope: VariantScope): FileCollection {
    val apiDesugarEnabled = variantScope.globalScope.extension.compileOptions.javaApiDesugaringEnabled
    val langDesugarEnabled = variantScope.java8LangSupportType == VariantScope.Java8LangSupport.D8
            || variantScope.java8LangSupportType == VariantScope.Java8LangSupport.R8
    if (apiDesugarEnabled == null || apiDesugarEnabled == false || !langDesugarEnabled) {
        return variantScope.globalScope.project.files()
    }

    val project = variantScope.globalScope.project
    val configuration = project.configurations.findByName(DESUGAR_LIB_CONFIGURATION_NAME)
        ?: createDesugarLibConfiguration(project)
    return getDesugarLibDexFromTransform(configuration)
}

/**
 * Returns a provider which represents the content of desugar.json file extracted from
 * desugar_jdk_libs_configuration.jar
 */
fun getDesugarLibConfig(project: Project): Provider<String> {
    val configuration = project.configurations.findByName(DESUGAR_LIB_CONFIG_CONFIGURATION_NAME)
        ?: createDesugarLibConfigConfiguration(project)

    registerDesugarLibConfigTransform(project)

    return getDesugarLibConfigFromTransform(configuration).elements.map{ locations ->
        if (locations.isEmpty()) {
            null
        } else {
            val dir = locations.map { it.asFile }.first()
            Files.walk(dir.toPath()).use { paths ->
                paths.filter{ it.toFile().name == DESUGAR_LIB_CONFIG_FILE }.findAny()
            }.let { configFile ->
                String(Files.readAllBytes(configFile.get()), StandardCharsets.UTF_8)
            }
        }
    }
}

private fun createDesugarLibConfiguration(project: Project): Configuration {
    val configuration = project.configurations.create(DESUGAR_LIB_CONFIGURATION_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The desugar_jdk_libs for desugaring Java Api."
    }

    project.dependencies.add(
        configuration.name,
        mapOf(
            "group" to "com.android.tools",
            "name" to "desugar_jdk_libs",
            "version" to "1.0.0"
        )
    )
    return configuration
}
private fun createDesugarLibConfigConfiguration(project: Project): Configuration {
    val configuration = project.configurations.create(DESUGAR_LIB_CONFIG_CONFIGURATION_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The desugar_jdk_libs_configuration for desugaring Java Api."
    }

    project.dependencies.add(
        configuration.name,
        mapOf(
            "group" to "com.android.tools",
            "name" to "desugar_jdk_libs_configuration",
            "version" to "0.1.0"
        )
    )
    return configuration
}

private fun getDesugarLibDexFromTransform(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_DEX
            )
        }
    }.artifacts.artifactFiles
}

private fun getDesugarLibConfigFromTransform(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_CONFIG
            )
        }
    }.artifacts.artifactFiles
}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles

private fun registerDesugarLibConfigTransform(project: Project) {
    project.dependencies.registerTransform(DesugarLibConfigExtractor::class.java) { spec ->
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_CONFIG)
    }
}

/**
 * Unzips the desugar_jdk_libs_configuration.jar
 */
abstract class DesugarLibConfigExtractor : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val outputDir = outputs.dir(inputFile.nameWithoutExtension)
        ZipInputStream(inputFile.inputStream().buffered()).use { zipInputStream ->
            while(true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val destinationFile = outputDir.resolve(entry.name).toPath()
                Files.createDirectories(destinationFile.parent)
                Files.newOutputStream(destinationFile).buffered().use { output ->
                    ByteStreams.copy(zipInputStream, output)
                }
            }
        }
    }
}