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
@file:JvmName("VariantUtil")
package com.android.projectmodel

/**
 * Variant of an [AndroidProject].
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
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
         * Main artifact (for example, the application or library itself). This is the artifact
         * named [ARTIFACT_NAME_MAIN].
         */
        val mainArtifact: Artifact,
        /**
         * Android test cases or null if none. This is the artifact named
         * [ARTIFACT_NAME_ANDROID_TEST].
         */
        val androidTestArtifact: Artifact? = null,
        /**
         * Plain java unit tests or null if none. This is the artifact named
         * [ARTIFACT_NAME_UNIT_TEST].
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
        val configPath: ConfigPath = matchAllArtifacts()
) {
    /**
     * Returns the [ConfigPath] for the main artifact in this [Variant].
     */
    val mainArtifactConfigPath: ConfigPath get() = ConfigPath(configPath.segments?.plus(mainArtifact.name))

    /**
     * Returns the [Artifact] in this [Variant] with the given name or null if none.
     */
    fun artifactNamed(name: String): Artifact? {
        return when (name) {
            ARTIFACT_NAME_MAIN -> mainArtifact
            ARTIFACT_NAME_UNIT_TEST -> unitTestArtifact
            ARTIFACT_NAME_ANDROID_TEST -> androidTestArtifact
            else -> extraArtifacts.find { it.name == name }
                    ?: extraJavaArtifacts.find { it.name == name }
        }
    }

    /**
     * Returns all [Artifact] instances that are part of this [Variant].
     */
    val artifacts: List<Artifact> = listOfNotNull(mainArtifact, androidTestArtifact, unitTestArtifact) + extraArtifacts + extraJavaArtifacts
}

/**
 * Name reserved the main artifact in a [Variant].
 */
const val ARTIFACT_NAME_MAIN = "_main_"

/**
 * Name reserved the android test artifact in a [Variant].
 */
const val ARTIFACT_NAME_ANDROID_TEST = "_android_test_"

/**
 * Name reserved the unit test artifact in a [Variant].
 */
const val ARTIFACT_NAME_UNIT_TEST = "_unit_test_"
