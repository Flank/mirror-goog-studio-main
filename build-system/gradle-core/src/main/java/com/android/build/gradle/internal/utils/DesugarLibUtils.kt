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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ArtifactAttributes

private const val DESUGAR_LIB_CONFIG_NAME = "_internal_desugar_jdk_libs"
const val DESUGAR_LIB_DEX = "_internal-desugar-jdk-libs-dex"

/**
 * Returns a file collection which contains desugar_jdk_libs.jar
 */
fun getDesugarLibJarFromMaven(project: Project): FileCollection {
    val existingConfig = project.configurations.findByName(DESUGAR_LIB_CONFIG_NAME)
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
    val existingConfig = project.configurations.findByName(DESUGAR_LIB_CONFIG_NAME)
    if (existingConfig != null) {
        return getDesugarLibDexFromTransform(existingConfig)
    }
    return getDesugarLibDexFromTransform(createDesugarLibConfiguration(project))
}

/**
 * Returns a string which has the content of desugar lib configuration resolved from maven repo
 */
fun getDesugarLibConfig(project: Project): String {
    //TODO(b/140371855) switch to real lib configuration once available
    return "default"
}

private fun createDesugarLibConfiguration(project: Project): Configuration {
    val config = project.configurations.create(DESUGAR_LIB_CONFIG_NAME) {
        it.isVisible = false
        it.isTransitive = false
        it.isCanBeConsumed = false
        it.description = "The desugar_jdk_libs for desugaring Java Api."
    }

    project.dependencies.add(
        config.name,
        mapOf(
            "group" to "com.android.tools",
            "name" to "desugar_jdk_libs",
            "version" to "1.0.0"
        )
    )
    return config
}

private fun getDesugarLibDexFromTransform(config: Configuration): FileCollection {
    return config.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_DEX
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