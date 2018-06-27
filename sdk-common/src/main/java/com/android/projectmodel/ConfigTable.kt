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
 * A config table holds the set of [Config] instances for an [AndroidProject] and describes how they
 * are shared among build types, [Artifact] instances, and [Variant] instances.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class ConfigTable(
        /**
         * Describes the dimensions of this matrix, the names each dimension, and the possible
         * values for each dimension.
         */
        val schema: ConfigTableSchema = ConfigTableSchema(),
        /**
         * Holds the association of the project's [Config] instances with the [ConfigPath] they apply to.
         * The list is ordered. [Config] instances later in the list take precedence over earlier ones.
         */
        val associations: List<ConfigAssociation> = emptyList()
) {
    /**
     * Returns all [Config] instances in this table.
     */
    val configs: List<Config>
        get() = associations.map { it.config }

    /**
     * Returns the list of [Config] instances that have any intersection with the table region
     * described by the given path.
     *
     * For example, if [Variant.configPath] is passed to this method, it will return all [Config]
     * instances that have any possibility of being used by an [Artifact] in that [Variant]. It
     * will include [Config] instances that only apply to specific [Artifact] instances, but will
     * exclude [Config] instances that don't apply to the [Variant] at all.
     */
    fun configsIntersecting(searchCriteria: ConfigPath): List<Config> =
            filterIntersecting(searchCriteria).configs

    /**
     * Returns the list of [Config] instances that do not apply to any [Artifact] within the table
     * region described by the given [ConfigPath].
     *
     * For example, if given a [Variant.configPath], it will return all [Config] instances
     * that are not used by any [Artifact] with that [Variant].
     */
    fun configsNotIntersecting(searchCriteria: ConfigPath): List<Config> =
            filterNotIntersecting(searchCriteria).configs

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that pass the given
     * filter.
     */
    inline fun filter(func: (ConfigAssociation)-> Boolean): ConfigTable {
        return ConfigTable(schema, associations.filter(func))
    }

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that intersect
     * the table region described by [searchCriteria].
     */
    fun filterIntersecting(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema, associations.filter {
            it.path.intersects(searchCriteria)
        })
    }

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that do not
     * intersect the table region described by [searchCriteria].
     */
    fun filterNotIntersecting(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema, associations.filter {
            !it.path.intersects(searchCriteria)
        })
    }

    /**
     * Generates all possible [Variant] instances for this [ConfigTable] by merging every
     * combination of schema dimensions.
     */
    fun generateVariants(): List<Variant> {
        val result = ArrayList<Variant>()
        generateVariants(result, emptyList())
        return result
    }

    private fun generateVariants(result: MutableList<Variant>, prefix: List<String>) {
        if (prefix.size < schema.dimensions.size - 1) {
            val dimension = schema.dimensions[prefix.size]
            for (dimensionValue in dimension.values) {
                generateVariants(result, prefix + dimensionValue)
            }
        }

        if (prefix.size == schema.dimensions.size - 1) {
            val artifacts = schema.dimensions[prefix.size].values.map {
                val configPath = matchArtifactsWith(prefix + it)
                Artifact(
                    name = it,
                    resolved = configsIntersecting(configPath).merged()
                )
            }

            val variantPath = ConfigPath(prefix)

            val mainArtifact = artifacts.find { it.name == ARTIFACT_NAME_MAIN }
            if (mainArtifact == null) {
                throw IllegalStateException("No main artifact found")
            }

            result.add(Variant(
                name = variantPath.simpleName,
                configPath = variantPath,
                mainArtifact = mainArtifact,
                androidTestArtifact = artifacts.find { it.name == ARTIFACT_NAME_ANDROID_TEST },
                unitTestArtifact = artifacts.find { it.name == ARTIFACT_NAME_UNIT_TEST },
                extraArtifacts = artifacts.filter { !defaultArtifactDimension.values.contains(it.name) }
            ))
        }
    }
}

/**
 * Constructs a [ConfigTable] from the given [ConfigTableSchema]. This is intended primarily
 * as a convenient way to construct hardcoded [ConfigTable] instances.
 *
 * @param schema the schema to use for the table
 * @param associations the entries to include in the table, where the keys map onto entries
 * in the schema.
 */
fun configTableWith(schema: ConfigTableSchema, associations: Map<String?, Config>) = ConfigTable(
    schema, associations.entries.map { ConfigAssociation(schema.pathFor(it.key), it.value) })
