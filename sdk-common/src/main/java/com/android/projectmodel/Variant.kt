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
 * Variant of an [AndroidProject].
 *
 * New properties may be added in the future; clients are encouraged to use Kotlin named arguments
 * to stay source compatible.
 */
data class Variant(
        /**
         * Identifier of the [Variant]. Meant to be unique within a given [AndroidProject] and
         * stable across syncs. This will be used for cross-referencing the [Variant] from other
         * projects in [ProjectLibrary.variant].
         */
        val name: String,
        /**
         * User-readable name of the [Variant]. By default, this is the same as the [name].
         */
        val displayName: String = name,
        /**
         * Main artifact (for example, the application or library itself).
         */
        val mainArtifact: Artifact,
        /**
         * Android test cases or null if none.
         */
        val androidTestArtifact: Artifact? = null,
        /**
         * Plain java unit tests or null if none.
         */
        val unitTestArtifact: Artifact? = null,
        /**
         * Extra user-defined Android artifacts.
         */
        val extraArtifacts: List<Artifact> = emptyList(),
        /**
         * Extra user-defined java artifacts.
         */
        val extraJavaArtifacts: List<Artifact> = emptyList(),
        /**
         * Holds the path to the [Config] instances for this [Variant] within its [ConfigTable].
         */
        val configPath: ConfigPath
) {
    /**
     * Returns the [ConfigPath] for the main artifact in this [Variant].
     */
    val mainArtifactConfigPath: ConfigPath get() = ConfigPath(configPath.segments?.plus(mainArtifact.name))
}
