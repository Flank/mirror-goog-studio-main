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

import com.android.build.gradle.internal.dependency.ATTR_L8_MIN_SDK
import com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_CORE_LIBRARY_DESUGARING
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
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipInputStream

private const val DESUGAR_LIB_NAME = "com.android.tools:desugar_jdk_libs:"
private const val DESUGAR_LIB_CONFIG_NAME = "com.android.tools:desugar_jdk_libs_configuration:"
private const val DESUGAR_LIB_CONFIG_FILE = "desugar.json"
// The output of L8 invocation, which is the dex output of desugar_jdk_libs.jar
const val DESUGAR_LIB_DEX = "_internal-desugar-jdk-libs-dex"
// The output of DesugarLibConfigExtractor which unzips desugar_jdk_libs_configuration.jar
const val DESUGAR_LIB_CONFIG = "_internal-desugar-jdk-libs-config"

/**
 * Returns a file collection which contains desugar_jdk_libs.jar
 */
fun getDesugarLibJarFromMaven(project: Project): FileCollection {
    val configuration = getDesugarLibConfiguration(project)
    return getArtifactCollection(configuration)
}

/**
 * Returns a file collection which contains desugar_jdk_libs.jar's dex file generated
 * by artifact transform
 */
fun getDesugarLibDexFromTransform(variantScope: VariantScope): FileCollection {
    if (!variantScope.isCoreLibraryDesugaringEnabled) {
        return variantScope.globalScope.project.files()
    }

    val configuration = getDesugarLibConfiguration(variantScope.globalScope.project)
    return getDesugarLibDexFromTransform(configuration, variantScope.minSdkVersion.featureLevel)
}

/**
 * Returns a provider which represents the content of desugar.json file extracted from
 * desugar_jdk_libs_configuration.jar
 */
fun getDesugarLibConfig(project: Project): Provider<String> {
    val configuration = project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!

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

/**
 * Returns the configuration of core library to be desugared and throws runtime exception if the
 * user didn't add any dependency to that configuration.
 *
 * Note: this method is only used when core library desugaring is enabled.
 */
private fun getDesugarLibConfiguration(project: Project): Configuration {
    val configuration = project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!
    configuration.defaultDependencies {
        throw RuntimeException("$CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration contains no " +
                "dependencies. If you intend to enable core library desugaring, please add " +
                "dependencies to $CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration.")
    }
    return configuration
}

private fun getDesugarLibDexFromTransform(configuration: Configuration, minSdkVersion: Int): FileCollection {
    return configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_DEX
            )
            it.attribute(ATTR_L8_MIN_SDK, minSdkVersion.toString())
        }
        config.componentFilter {
            it.displayName.contains(DESUGAR_LIB_NAME)
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
        configuration.componentFilter {
            it.displayName.contains(DESUGAR_LIB_CONFIG_NAME)
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
        config.componentFilter {
            it.displayName.contains(DESUGAR_LIB_NAME)
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
