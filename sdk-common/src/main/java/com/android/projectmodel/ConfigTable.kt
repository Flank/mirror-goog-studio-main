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
 * A build matrix holds the set of [Config] instances for an [AndroidProject] and describes how they
 * are shared among build types, [Artifact] instances, and [Variant] instances.
 *
 * New properties may be added in the future; clients are encouraged to use Kotlin named arguments
 * to stay source compatible.
 */
data class ConfigTable(
        /**
         * Describes the dimensions of this matrix, the names each dimension, and the possible
         * values for each dimension. By convention the first dimensions correspond to flavors, if
         * any, the second-last dimension corresponds to build type, and the last dimensions
         * corresponds to an artifact name.
         */
        val schema: ConfigTableSchema = ConfigTableSchema(),
        /**
         * Holds the project's [Config] instances. Each [Config] has an associated [ConfigPath]
         * describing what artifacts and variants it applies to. The configs are ordered. [Config]
         * instances later in the list take precedence over earlier ones.
         */
        val associations: List<ConfigAssociation> = emptyList()
) {
    /**
     * Returns all [Config] instances in this table.
     */
    val configs: List<Config>
        get() = associations.map { it.config }

    /**
     * Returns the list of [Config] instances that completely contain the table region described
     * by the given path.
     *
     * For example, if [Variant.configPath] is passed to this method, it will return all [Config]
     * instances that are common to all [Artifact] instances within that [Variant]. It will
     * exclude [Config] instances that may only apply to specific [Artifact] instances and [Config]
     * instances that don't apply to the [Variant] at all.
     */
    fun configsContaining(searchCriteria: ConfigPath): List<Config> =
            filterContaining(searchCriteria).configs

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
     * Returns the list of [Config] instances that do not apply to all [Artifact] instances
     * within the table region described by the given [ConfigPath].
     *
     * For example, if given a [Variant.configPath], it will return all [Config] instances
     * that are not used by any [Artifact] with that [Variant] and those that only apply to
     * some [Artifact] instances within that [Variant].
     */
    fun configsNotContaining(searchCriteria: ConfigPath): List<Config> =
            filterNotContaining(searchCriteria).configs

    /**
     * Returns the [ConfigTable] containing the [Config] instances from this table that completely
     * contain the table region described by [searchCriteria].
     */
    fun filterContaining(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema,
                associations.filter { it.path.contains(searchCriteria) })
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
     * completely contain the table region described by [searchCriteria].
     */
    fun filterNotContaining(searchCriteria: ConfigPath): ConfigTable {
        return ConfigTable(schema,
                associations.filter { !it.path.contains(searchCriteria) })
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
}
