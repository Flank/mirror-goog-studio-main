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

@file:JvmName("ConfigTableSchemaUtil")

package com.android.projectmodel

/**
 * Describes the schema for a [ConfigTable]. Specifically, it describes the set of dimensions
 * and the allowable values for [ConfigPath] instances along each dimension. For example, in
 * the case of Gradle projects the first dimensions correspond to flavors, if any, and the second-last
 * dimension corresponds to build type. For all build systems, the last dimension always corresponds
 * to an artifact name.
 */
data class ConfigTableSchema(
    /**
     * Dimensions for the table.
     */
    val dimensions: List<ConfigDimension> = listOf(defaultArtifactDimension)
) {
    init {
        if (dimensions.isEmpty() || !dimensions.last().values.contains(ARTIFACT_NAME_MAIN)) {
            throw IllegalArgumentException("The main artifact must be present in the config table")
        }
    }

    /**
     * Returns a [ConfigPath] that matches all [Config] instances that use the given
     * [dimensionValue]. If dimensionValue is null, the resulting path matches all
     * artifacts.
     */
    fun pathFor(dimensionValue: String?): ConfigPath {
        // TODO: Allow dimensionValue to be any ConfigPath simpleName. This would allow construction
        // of more elaborate multidimensional test cases using this utility method.
        dimensionValue ?: return matchAllArtifacts()
        val index = dimensions.indexOfFirst { it.values.contains(dimensionValue) }
        if (index == -1) {
            return matchNoArtifacts()
        }
        return matchDimension(index, dimensionValue)
    }

    /**
     * Returns a [ConfigPath] that matches an artifact name, which is always stored as the last
     * segment of the path.
     */
    fun matchArtifact(artifactName: String): ConfigPath {
        return matchDimension(dimensions.size - 1, artifactName)
    }

    /**
     * Returns a sequence containing every valid [ConfigPath] in this schema.
     */
    fun allPaths(): Sequence<ConfigPath> = allPathsOfLength(dimensions.size)

    /**
     * Returns a sequence containing every valid [ConfigPath] prefix in this schema with the given
     * length.
     */
    fun allPathsOfLength(desiredPathLength: Int): Sequence<ConfigPath> =
        if (desiredPathLength > dimensions.size)
            throw IllegalArgumentException("desiredPathLength ${desiredPathLength} must not be larger than the number of dimensions (${dimensions.size})")
        else allPathsOfLength(desiredPathLength, emptyList())

    private fun allPathsOfLength(
        desiredPathLength: Int,
        prefix: List<String>
    ): Sequence<ConfigPath> {
        return when {
            prefix.size == desiredPathLength - 1 -> dimensions[prefix.size].values.map {
                ConfigPath(
                    prefix + it
                )
            }.asSequence()
            prefix.size < desiredPathLength - 1 -> dimensions[prefix.size].values.asSequence().flatMap {
                allPathsOfLength(
                    desiredPathLength,
                    prefix + it
                )
            }
            else -> emptySequence()
        }
    }

    override fun toString(): String
        = "ConfigTableSchema(${dimensions.map {"${it.dimensionName}[${it.values.joinToString(",")}]"}.joinToString(",")})"


    class Builder {
        private val dimensions = ArrayList<ConfigDimension.Builder>()
        private val nameToDimension = HashMap<String, ConfigDimension.Builder>()

        fun getOrPutDimension(name: String): ConfigDimension.Builder {
            return nameToDimension.getOrPut(name, {
                val builder = ConfigDimension.Builder(name)
                dimensions.add(builder)
                builder
            })
        }

        fun build(): ConfigTableSchema = ConfigTableSchema(dimensions.map { it.build() })
    }
}

/**
 * Name of the dimension that identifies the artifact.
 */
val ARTIFACT_DIMENSION_NAME = "artifact"

/**
 * Default last dimension for a config table. It contains the default three artifacts for each
 * variant (a main artifact, a unit test artifact, and an android test artifact).
 */
val defaultArtifactDimension = ConfigDimension(
    ARTIFACT_DIMENSION_NAME, listOf(
        ARTIFACT_NAME_MAIN,
        ARTIFACT_NAME_UNIT_TEST,
        ARTIFACT_NAME_ANDROID_TEST
    )
)

/**
 * Construct a [ConfigTableSchema] from a vararg list of pairs. Intended primarily for providing
 * a concise syntax for hardcoded schema creation in unit tests. Schemas constructed this way always
 * use the default
 */
fun configTableSchemaWith(vararg dimensions: Pair<String, List<String>>): ConfigTableSchema {
    return ConfigTableSchema(dimensions.map {
        ConfigDimension(
            it.first,
            it.second
        )
    } + defaultArtifactDimension)
}
