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
        val dimensions: List<ConfigDimension> = emptyList()
) {
    /**
     * Returns a [ConfigPath] that matches all [Config] instances that use the given
     * [dimensionValue].
     */
    fun pathFor(dimensionValue: String): ConfigPath {
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
