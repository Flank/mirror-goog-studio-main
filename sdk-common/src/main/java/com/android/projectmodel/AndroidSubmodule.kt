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
@file:JvmName("AndroidSubmoduleUtil")

package com.android.projectmodel

import com.android.ide.common.util.PathString

/**
 * Represents a single Android submodule. This maps quite closely to what Gradle refers to as an Android Project. This is the collection of
 * sources and metadata needed to construct a set of related android artifacts that share the same set of variants. An android submodule
 * contains one or more [Variant]s, which are alternative ways of constructing the artifacts in the submodule.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class AndroidSubmodule(
    /**
     * Unique identifier of the submodule, provided by the build system. This is used for cross-referencing the submodule when it appears
     * as a dependency for other submodules. Should remain invariant across syncs, but does not need to remain invariant across
     * machines. This will be displayed to the user as the submodule's identifier, so it should be something the user would be familiar
     * with (such as the submodule's build target or root folder). For example, in Gradle this will be the submodule path, e.g.
     * :util:widgets.
     */
    val name: String,
    /**
     * Indicates the type of submodule.
     */
    val type: ProjectType,
    /**
     * List of variants for the submodule.
     */
    val variants: List<Variant> = emptyList(),
    /**
     * Config table for this submodule.
     */
    val configTable: ConfigTable = ConfigTable(),
    /**
     * List of locations where this submodule will write generated files and folders. The list
     * may contains a mixture of files and folders. Any file located at or below these paths should
     * be considered derived.
     */
    val generatedPaths: List<PathString> = emptyList(),
    /**
     * Namespacing strategy for this submodule
     */
    val namespacing: NamespacingType = NamespacingType.DISABLED
) {
    constructor(name: String, type: ProjectType) : this(
        name = name,
        type = type,
        variants = emptyList()
    )

    override fun toString(): String =
        printProperties(this, AndroidSubmodule(name = "", type = ProjectType.APP))

    /**
     * Returns a copy of the receiver with the given submodule type. Intended to simplify construction
     * from Java.
     */
    fun withType(type: ProjectType) = copy(type = type)

    /**
     * Returns a copy of the receiver with the given config table, containing variants generated
     * from the given config table to contain the given artifacts.
     */
    fun withVariantsGeneratedBy(configTable: ConfigTable) =
        withVariantsGeneratedBy(configTable, configTable.generateArtifacts())

    /**
     * Returns a copy of the receiver with the given config table, containing variants generated
     * from the given config table to contain the given artifacts.
     */
    fun withVariantsGeneratedBy(
        configTable: ConfigTable,
        artifacts: Map<ConfigPath, Artifact>
    ) = copy(
        configTable = configTable,
        variants = configTable.generateVariantsFor(artifacts)
    )

    /**
     * Returns a copy of the receiver with the given list of generated paths. Intended to simplify
     * construction from Java.
     */
    fun withGeneratedPaths(paths: List<PathString>) = copy(generatedPaths = paths)

    /**
     * Returns a copy of the receiver with the given namespacing strategy. Intended to simplify
     * construction from Java.
     */
    fun withNamespacing(namespacing: NamespacingType) = copy(namespacing = namespacing)

}
