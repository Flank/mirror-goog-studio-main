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
import org.gradle.api.artifacts.DirectDependencyMetadata
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
            Processor.createProcessor(
                ConfigParser.loadDefaultConfig()!!,
                dataBindingVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION
            )
        }

        /**
         * Mappings from old dependencies to AndroidX dependencies.
         *
         * Each entry maps "old-group:old-module" (without version) to
         * "new-group:new-module:new-version" (with version).
         */
        @JvmField
        val androidXMappings: Map<String, String> =
            jetifyProcessor.getDependenciesMap(filterOutBaseLibrary = false)

        /**
         * Replaces old support libraries with new ones.
         */
        @JvmStatic
        fun replaceOldSupportLibraries(project: Project) {
            // TODO (AGP): This is a quick fix to work around Gradle bug with dependency
            // substitution (https://github.com/gradle/gradle/issues/5174). Once Gradle has fixed
            // this issue, this should be removed.
            project.dependencies.components.all { component ->
                component.allVariants { variant ->
                    variant.withDependencies { metadata ->
                        val oldDeps = mutableSetOf<DirectDependencyMetadata>()
                        val newDeps = mutableListOf<String>()
                        metadata.forEach { it ->
                            val newDep = if (bypassDependencySubstitution(it)) {
                                null
                            } else {
                                androidXMappings["${it.group}:${it.name}"]
                            }
                            if (newDep != null) {
                                oldDeps.add(it)
                                newDeps.add(newDep)
                            }
                        }
                        // Using metadata.removeAll(oldDeps) doesn't work for some reason, we need
                        // to use this for loop.
                        for (oldDep in oldDeps.map { it -> "${it.group}:${it.name}" }) {
                            metadata.removeIf { it -> "${it.group}:${it.name}" == oldDep }
                        }
                        for (newDep in newDeps) {
                            metadata.add(newDep)
                        }
                    }
                }
            }

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
            if (bypassDependencySubstitution(requestedDependency, configuration)) {
                return
            }

            androidXMappings[requestedDependency.group + ":" + requestedDependency.module]?.let {
                dependencySubstitution.useTarget(
                    it,
                    BooleanOption.ENABLE_JETIFIER.name + " is enabled"
                )
            }
        }

        /**
         * Returns `true` if the requested dependency should not be substituted.
         */
        private fun bypassDependencySubstitution(
            requestedDependency: DirectDependencyMetadata
        ): Boolean {
            // See bypassDependencySubstitution(ModuleComponentSelector, Configuration).
            // This condition is more relaxed (catches more cases) than the condition in the
            // other method, since the configuration information is not available when this
            // method is called. That is acceptable as dependency substitution happens at two
            // phases (due to a Gradle bug as mentioned in the replaceOldSupportLibraries()
            // method): the first one can be relaxed but the second one should be strict.
            return requestedDependency.group == "com.android.databinding"
                    && requestedDependency.name == "baseLibrary"
        }

        /**
         * Returns `true` if the requested dependency should not be substituted.
         */
        private fun bypassDependencySubstitution(
            requestedDependency: ModuleComponentSelector,
            configuration: Configuration
        ): Boolean {
            // androidx.databinding:databinding-compiler has a transitive dependency on
            // com.android.databinding:baseLibrary, which shouldn't be replaced with AndroidX.
            // Note that if com.android.databinding:baseLibrary doesn't come as a transitive
            // dependency of androidx.databinding:databinding-compiler (e.g., a configuration
            // explicitly depends on it), then we should still replace it. See
            // https://issuetracker.google.com/78202536.
            return requestedDependency.group == "com.android.databinding"
                    && requestedDependency.module == "baseLibrary"
                    && configuration.allDependencies.any { dependency ->
                dependency.group == "androidx.databinding"
                        && dependency.name == "databinding-compiler"
            }
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
        // Case 1: If this is a new support library, no need to jetify it
        if (jetifyProcessor.isNewDependencyFile(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // Case 2: If this is an old support library, it means that it was not replaced during
        // dependency substitution earlier, either because it does not yet have an AndroidX version,
        // or because its AndroidX version is not yet available on remote repositories. Again, no
        // need to jetify it.
        if (jetifyProcessor.isOldDependencyFile(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // Case 3: For the remaining, let's jetify them.
        val outputFile = File(outputDirectory, "jetified-" + aarOrJarFile.name)
        val maybeTransformedFile = try {
            jetifyProcessor.transform(
                setOf(FileMapping(aarOrJarFile, outputFile)), false
            )
                .single()
        } catch (exception: Exception) {
            throw RuntimeException(
                "Failed to transform '$aarOrJarFile' using Jetifier."
                        + " Reason: ${exception.message}. (Run with --stacktrace for more details.)"
                        + " To disable Jetifier,"
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

