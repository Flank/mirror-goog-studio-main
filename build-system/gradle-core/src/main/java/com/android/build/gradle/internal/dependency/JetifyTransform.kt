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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.Version
import com.android.tools.build.jetifier.core.config.ConfigParser
import com.android.tools.build.jetifier.processor.FileMapping
import com.android.tools.build.jetifier.processor.Processor
import com.google.common.base.Preconditions
import com.google.common.base.Verify
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.File
import javax.inject.Inject

/**
 * [ArtifactTransform] to convert a third-party library that uses old support libraries into an
 * equivalent library that uses new support libraries.
 */
class JetifyTransform @Inject constructor() : ArtifactTransform() {

    companion object {

        @JvmStatic
        val jetifyProcessor: Processor by lazy {
            Processor.createProcessor(ConfigParser.loadDefaultConfig()!!)
        }

        /**
         * Replaces old support libraries with new ones.
         */
        @JvmStatic
        fun replaceOldSupportLibraries(project: Project) {
            project.configurations.all { config ->
                // Only consider resolvable configurations
                if (config.isCanBeResolved) {
                    config.resolutionStrategy.dependencySubstitution.all { it ->
                        JetifyTransform.maybeSubstituteDependency(it, config)
                    }
                }
            }
        }

        /**
         * Replaces the given dependency with the new support library if the given dependency is an
         * old support library.
         */
        private fun maybeSubstituteDependency(
            dependencySubstitution: DependencySubstitution, configuration: Configuration
        ) {
            // Only consider Gradle module dependencies (in the form of group:module:version)
            if (dependencySubstitution.requested !is ModuleComponentSelector) {
                return
            }

            val requestedDependency = dependencySubstitution.requested as ModuleComponentSelector
            val newSupportLibrary = getNewSupportLibrary(requestedDependency)

            // If the returned value is not null, it means that the dependency is an old support
            // library and the new one is available in remote repositories. Let's replace it.
            val targetDependency = newSupportLibrary ?: requestedDependency.displayName

            val effectiveTargetDependency =
                correctVersionNumber(targetDependency, requestedDependency)
            if (effectiveTargetDependency != requestedDependency.displayName) {
                dependencySubstitution
                    .useTarget(
                        effectiveTargetDependency,
                        BooleanOption.ENABLE_JETIFIER.name + " is enabled"
                    )
            }

            handleSpecialCases(requestedDependency, dependencySubstitution, configuration)
        }

        /**
         * Returns the new support library that replaces the old one if the given dependency is an
         * old support library and the new one is available in remote repositories, otherwise
         * returns null.
         */
        private fun getNewSupportLibrary(dependency: ModuleComponentSelector): String? {
            if (isOldSupportLibrary(dependency) && newSupportLibraryAvailable(dependency)) {
                val newSupportLibraries = jetifyProcessor.mapDependency(dependency.displayName)
                if (newSupportLibraries == null || newSupportLibraries.isEmpty()) {
                    throw IllegalStateException(
                        "Can't find substitution for ${dependency.displayName}"
                    )
                }
                if (newSupportLibraries.size > 1) {
                    throw IllegalStateException(
                        "Multiple substitutions exist for ${dependency.displayName}.\n"
                                + "They are $newSupportLibraries."
                    )
                }
                return newSupportLibraries.single()
            } else {
                return null
            }
        }

        /**
         * Corrects the version number of the target dependency since the stable versions may not
         * yet be available in remote repositories. This is useful for testing while the feature is
         * still under development, eventually this method should be removed.
         */
        private fun correctVersionNumber(
            targetDependency: String, requestedDependency: ModuleComponentSelector
        ): String {
            val parts = targetDependency.split(':')
            val group = parts[0]
            val module = parts[1]
            val version = parts[2]

            // TODO (jetifier-core): Need to map android.arch.* to 2.0.0. Right now jetifier-core is
            // mapping it to 1.0.0, which is incorrect.
            if (requestedDependency.group.startsWith("android.arch")
                && group.startsWith("androidx")
                && version == "1.0.0"
            ) {
                return "$group:$module:2.0.0-alpha1" // alpha1 since stable version is not available
            }

            // TODO (jetifier-core): Need to map databinding to the Android Gradle plugin version.
            // Right now jetifier-core is mapping it to versions such as 1.0.0, which is incorrect.
            if (group == "androidx.databinding") {
                return "$group:$module:${Version.ANDROID_GRADLE_PLUGIN_VERSION}"
            }

            // TODO (AGP): The stable versions of AndroidX are not available yet, only preview
            // versions. Therefore, here we replace the dependencies with their preview versions.
            // Eventually, when the stable versions are all published, we should remove this method.
            if (group.startsWith("androidx")) {
                if (version == "1.0.0") {
                    return "$group:$module:1.0.0-alpha1"
                } else if (version == "2.0.0-SNAPSHOT") {
                    return "$group:$module:2.0.0-alpha1"
                }
            }
            return targetDependency
        }

        /**
         * Handles cases that are non-standard but required after the main dependency substitution
         * takes place, potentially overwriting/reverting part of it.
         */
        private fun handleSpecialCases(
            requestedDependency: ModuleComponentSelector,
            dependencySubstitution: DependencySubstitution,
            configuration: Configuration
        ) {
            // This is to address https://issuetracker.google.com/78202536:
            // androidx.databinding:databinding-compiler has a transitive dependency on
            // com.android.databinding:baseLibrary, which shouldn't be replaced with AndroidX.
            // Note that if com.android.databinding:baseLibrary doesn't come as a transitive
            // dependency of androidx.databinding:databinding-compiler (e.g., a configuration
            // explicitly depends on it), then we should still replace it.
            val configurationContainsDataBindingCompiler =
                configuration.allDependencies.any({ dependency ->
                    dependency.group == "androidx.databinding"
                            && dependency.name == "databinding-compiler"
                })
            if (configurationContainsDataBindingCompiler
                && requestedDependency.group == "com.android.databinding"
                && requestedDependency.module == "baseLibrary"
            ) {
                // Undo the replacement that was done earlier.
                dependencySubstitution.useTarget(requestedDependency)
            }
        }

        private fun isOldSupportLibrary(dependency: ModuleComponentSelector): Boolean {
            // TODO (jetifier-core): Need a method to tell whether the given dependency is an
            // old support library
            return dependency.group.startsWith("com.android.support")
                    || dependency.group.startsWith("android.arch")
                    || dependency.group == "com.android.databinding"
        }

        private fun isOldSupportLibrary(aarOrJarFile: File): Boolean {
            // TODO (jetifier-core): Similar to isOldSupportLibrary(ModuleComponentSelector)
            return aarOrJarFile.absolutePath.matches(Regex(".*com.android.support.*"))
                    || aarOrJarFile.absolutePath.matches(Regex(".*android.arch.*"))
                    || aarOrJarFile.absolutePath.matches(Regex(".*com.android.databinding.*"))
        }

        private fun newSupportLibraryAvailable(oldSupportLibDep: ModuleComponentSelector): Boolean {
            // TODO (AGP): AndroidX versions of Android testing support library are not yet
            // available. Remove this code when they are.
            return !oldSupportLibDep.group.startsWith("com.android.support.test")
        }

        private fun newSupportLibraryAvailable(aarOrJarFile: File): Boolean {
            // TODO (AGP): Similar to newSupportLibraryAvailable(ModuleComponentSelector)
            return !aarOrJarFile.absolutePath.matches(Regex(".*com.android.support.test.*"))
        }

        private fun isNewSupportLibrary(aarOrJarFile: File): Boolean {
            // TODO (jetifier-core): Need a method to tell whether the given aarOrJarFile is a
            // new support library
            return aarOrJarFile.absolutePath.contains("androidx")
                    || aarOrJarFile.absolutePath.matches(Regex(".*com.google.android.material.*"))
        }
    }

    override fun transform(aarOrJarFile: File): List<File> {
        Preconditions.checkArgument(
            aarOrJarFile.name.toLowerCase().endsWith(".aar")
                    || aarOrJarFile.name.toLowerCase().endsWith(".jar")
        )

        /*
         * The aars or jars can be categorized into 3 types:
         *  - New support libraries
         *  - Old support libraries
         *  - Others
         * In the following, we handle these cases accordingly.
         */
        // Case 1: If this is a new support library, no need to transform it
        if (isNewSupportLibrary(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // TODO (AGP): This additional case is only temporary. It will be removed in later versions.
        // See newSupportLibraryAvailable() method.
        if (isOldSupportLibrary(aarOrJarFile) && !newSupportLibraryAvailable(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // Case 2: If this is an old support library, there was probably some bug because it should
        // have been replaced with a new support library earlier via dependency substitution.
        if (isOldSupportLibrary(aarOrJarFile)) {
            throw IllegalStateException(
                "Dependency was not replaced with AndroidX: ${aarOrJarFile.absolutePath}"
            )
        }

        // Case 3: For the remaining, let's jetify them.
        val outputFile = File(outputDirectory, "jetified-" + aarOrJarFile.name)
        val maybeTransformedFile: File
        try {
            maybeTransformedFile = jetifyProcessor.transform(
                setOf(FileMapping(aarOrJarFile, outputFile)), false
            )
                .single()
        } catch (exception: Exception) {
            throw RuntimeException(
                "Failed to transform '$aarOrJarFile' using Jetifier. To disable Jetifier,"
                        + " set ${BooleanOption.ENABLE_JETIFIER.propertyName}=false in your"
                        + " gradle.properties file.",
                exception
            )
        }

        // If the aar/jar was transformed, the returned file would be the output file. Otherwise, it
        // would be the original file.
        Preconditions.checkState(
            maybeTransformedFile == aarOrJarFile || maybeTransformedFile == outputFile
        )

        // If the file wasn't transformed, returning the original file here also tells Gradle that
        // the file wasn't transformed. In either case (whether the file was transformed or not), we
        // can just return to Gradle the file that was returned from Jetifier.
        Verify.verify(maybeTransformedFile.exists(), "$outputFile does not exist")
        return listOf(maybeTransformedFile)
    }
}

