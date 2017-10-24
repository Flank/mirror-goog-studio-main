/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.projectmodel

/**
 * Holds information about what is involved in producing a single Android artifact (a single output of the project).
 */
data class Artifact(
        /**
         * Name of the artifact, unique within a given [AndroidProject]. Should remain stable across syncs.
         */
        val name: String,
        /**
         * Contains the merged project [Config] for this [Artifact]. Note that this contains the resolved information
         * used by the build system to produce the artifact. This will be similar to what would be produced by merging
         * the [configs] list, but it is not guaranteed to be exactly the same.
         *
         * For example, the build system is likely to apply additional smarts when resolving dependencies (in order
         * to resolve conflicts between [Config] instances or remove redundant dependencies). It also may apply additional
         * manifest substitutions at resolve-time that didn't come directly from one of the constituent [Config] instances.
         *
         * For this reason, API consumers that need to know what went into the artifact should generally use this
         * [Config] rather than trying to compute it themselves from the [configs] list.
         *
         * For well-formed projects, this [Config] is expected to override all possible metadata and to
         * supply a valid dependency list.
         */
        val resolved: Config,
        /**
         * All [Config] instances that contributed to this artifact, in priority order. [Config] instances later in the list
         * override configs earlier in the list.
         */
        val configs: List<Config> = listOf(resolved),
        /**
         * List of class folders and .jar files containing the output of java compilation for this artifact.
         */
        val classFolders: List<PathString>,
        /**
         * The package name of the R file. Application projects may also use this as the default
         * value for the application ID. It is defined here:
         * https://developer.android.com/studio/build/application-id.html. Null if undefined.
         * This will be undefined if it is unknown to the build system at sync time or if this is
         * not an Android artifact.
         */
        val packageName: String? = null
) {
    /**
     * The compile-time dependencies for this artifact. This is a shorthand for accessing the compile time dependencies from [resolved].
     */
    val compileDeps: List<ArtifactDependency>
        get() {
            // Note that the resolved dependencies will always supply a list of compile-time dependencies for well-formed projects,
            // so there is no need for the return value to be nullable.
            return resolved.compileDeps ?: emptyList()
        }
}
