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

package com.android.tools.lint.model

import com.android.ide.common.repository.GradleVersion
import java.io.File

/**
 * Metadata about the project from the build system perspective.
 * This is a generalization of the older builder-model library which
 * communicated Gradle project information to lint.
 *
 * Not all build systems have the same capabilities. The LintModel skews
 * a bit towards Gradle and some concepts provided by the Android
 * Gradle plugin, such as "variants", "product flavors" and so on.
 */
interface LmModule {
    /** The root location of this module */
    val dir: File

    /** Name of the module */
    val moduleName: String

    /** Type of the model */
    val type: LmModuleType

    /** The Maven coordinate of this project, if known */
    val mavenName: LmMavenName?

    /** If the build model is Gradle, the Gradle version */
    val gradleVersion: GradleVersion?

    /** Returns the build folder of this project */
    val buildFolder: File

    /** Lint customization options */
    val lintOptions: LmLintOptions

    /** Build features in effect */
    val buildFeatures: LmBuildFeatures

    /**
     * The resource prefix to use, if any. This is an optional prefix which can be set and
     * which is used by the defaults to automatically choose new resources with a certain prefix,
     * warn if resources are not using the given prefix, etc. This helps work with resources in the
     * app namespace where there could otherwise be unintentional duplicated resource names between
     * unrelated libraries.
     */
    val resourcePrefix: String?
    val dynamicFeatures: Collection<String>

    /**
     * Returns the boot classpath matching the compile target. This is typically android.jar plus
     * other optional libraries.
     */
    val bootClassPath: List<File>
    val javaSourceLevel: String
    val compileTarget: String

    val variants: List<LmVariant>

    // For temporary backwards compatibility
    val oldProject: com.android.ide.common.gradle.model.IdeAndroidProject?

    fun defaultVariant(): LmVariant? = variants.firstOrNull()

    /**
     * Returns true if none of the build types used by this module have enabled shrinking,
     * or false if at least one variant's build type is known to use shrinking.
     */
    fun neverShrinking(): Boolean

    fun findVariant(name: String): LmVariant? { // TODO: Lazily compute in delegate
        for (variant in variants) {
            if (name == variant.name) {
                return variant
            }
        }

        return null
    }

    /** Given an active variant, return all the *other* (inactive) source providers */
    fun getInactiveSourceProviders(active: LmVariant): List<LmSourceProvider> {
        val seen = HashSet<File>()
        for (provider in active.sourceProviders) {
            seen.add(provider.manifestFile)
        }

        val providers: MutableList<LmSourceProvider> = mutableListOf()
        for (variant in variants) {
            if (variant != active) {
                for (provider in active.sourceProviders) {
                    if (seen.add(provider.manifestFile)) {
                        providers.add(provider)
                    }
                }
            }
        }

        return providers
    }
}

class DefaultLmModule(
    override val dir: File,
    override val moduleName: String,
    override val type: LmModuleType,
    override val mavenName: LmMavenName?,
    override val gradleVersion: GradleVersion?,
    override val buildFolder: File,
    override val lintOptions: LmLintOptions,
    override val buildFeatures: LmBuildFeatures,
    override val resourcePrefix: String?,
    override val dynamicFeatures: Collection<String>,
    override val bootClassPath: List<File>,
    override val javaSourceLevel: String,
    override val compileTarget: String,
    override val variants: List<LmVariant>,
    private val neverShrinking: Boolean,
    override val oldProject: com.android.ide.common.gradle.model.IdeAndroidProject?
) : LmModule {
    override fun neverShrinking(): Boolean {
        return neverShrinking
    }
}

/**
 * Writes this module model to the given file
 */
fun LmModule.write(xmlFile: File, createdBy: String? = null) {
    LmSerialization.write(this, destination = xmlFile, createdBy = createdBy)
}
