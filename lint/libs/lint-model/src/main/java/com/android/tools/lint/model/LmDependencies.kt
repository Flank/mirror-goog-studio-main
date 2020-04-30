/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

/**
 * The dependencies for an [LmArtifact]
 */
interface LmDependencies {
    /**
     * The compile dependency graph
     */
    val compileDependencies: LmDependencyGraph

    /**
     * The package dependency graph. This only includes the libraries which are
     * packaged; e.g. it will not include provided (compileOnly) libraries.
     */
    val packageDependencies: LmDependencyGraph

    /**
     * All the transitive compile dependencies. This is just a convenience method for calling
     * [compileDependencies] and from there [LmDependencyGraph.getAllLibraries]
     */
    fun getAll(): List<LmLibrary> = compileDependencies.getAllLibraries()

    /** Looks up the library provider to use to resolve artifact addresses in [LmDependency]
     * nodes into full [LmLibrary] */
    fun getLibraryResolver(): LmLibraryResolver
}

/**
 * A dependency graph.
 *
 * Each graph is fairly lightweight, with each artifact node being mostly an address, children,
 * and modifiers that are specific to this particular usage of the artifact rather than
 * artifact properties.
 */
interface LmDependencyGraph {
    val roots: List<LmDependency>

    /**
     * Find the library with the given [mavenName] (group id and artifact id). If [direct] is false,
     * it will search transitively; otherwise it will only look at direct dependencies.
     *
     * This lookup method is intended to be fast, so implementations should add caching
     * and ensure that shared transitive dependencies are only searched once (e.g. if
     * this module depends on libraries A and B, and both A and B in turn depend on library
     * C, we will only look at A, B and C once, and in particular we won't search C both
     * from A and from B.
     */
    fun findLibrary(mavenName: String, direct: Boolean = true): LmLibrary?

    /** Returns all the (transitively) included graph items */
    fun getAllGraphItems(): List<LmDependency>

    /** Returns all the (transitively) included libraries */
    fun getAllLibraries(): List<LmLibrary>
}

/**
 * A node in a dependency graph, representing a direct or transitive dependency.
 *
 * This does not directly contain artifact information, instead it focuses on the graph
 * information (transitive dependencies) as well as the usage of this particular dependency
 * in this node of the graph (ie what are its modifiers: what version was originally requested.)
 *
 * To get the full [LmLibrary] definition on item in the dependency graph, call
 * [LmLibraryResolver.getLibrary] with the [artifactAddress] from this [LmDependency],
 * which you can do with the convenience method [findLibrary].
 */
interface LmDependency {
    /**
     * The simple name of a library: this is like the [artifactAddress] but does not
     * include version information or classifiers; for a Maven library it is the
     * groupId and artifactId separated by a colon, such as "androidx.annotation:annotation",
     * and for a local dependency it is the same as the artifact address.
     */
    val artifactName: String

    /**
     * The unique address of an artifact.
     *
     * This is either a module path for sub-modules, or a full maven coordinate for external
     * dependencies.
     *
     * The maven coordinates are in the format: groupId:artifactId:version[:classifier][@extension].
     */
    val artifactAddress: String

    /**
     * The Maven coordinates, as requested in the build file or POM file, if known.
     */
    val requestedCoordinates: String?

    /**
     * The direct dependencies of this dependency graph node.
     */
    val dependencies: List<LmDependency>

    /** Find the library corresponding to this item */
    fun findLibrary(): LmLibrary?

    /** Returns the artifact id portion of the [artifactName], *if* it is a maven library. */
    fun getArtifactId(): String {
        return artifactName.substring(artifactName.indexOf(':') + 1)
    }
}

/**
 * A lookup mechanism from artifact address to library. This is a global registry
 * of libraries, shared between projects and variants.
 */
interface LmLibraryResolver {
    /**
     * Returns all libraries known to the resolver.
     *
     * **NOTE**: Lint checks should **not** iterate through this list to see whether
     * we depend on a certain library; this list contains knowledge about
     * libraries from separate variants and modules. The correct way to see
     * if a variant or module depends on a library is via
     * [LmDependencyGraph] and the lookup methods there.
     *
     * This method is primarily here to support model persistence operations.
     */
    fun getAllLibraries(): Collection<LmLibrary>

    /** Get the library corresponding to the given artifact address, if any */
    fun getLibrary(artifactAddress: String): LmLibrary?
}

// Default implementations

class DefaultLmDependencyGraph(
    override val roots: List<LmDependency>,
    private val libraryResolver: LmLibraryResolver
) : LmDependencyGraph {
    /** All libraries that we depend on, keyed by maven name (groupId:artifactId) */
    private val transitiveDependencies = mutableMapOf<String, LmDependency>()

    init {
        for (item in roots) {
            register(item)
        }
    }

    private fun register(item: LmDependency) {
        if (transitiveDependencies.containsKey(item.artifactName)) {
            return
        }

        transitiveDependencies[item.artifactName] = item

        for (dependsOn in item.dependencies) {
            register(dependsOn)
        }
    }

    override fun findLibrary(mavenName: String, direct: Boolean): LmLibrary? {
        val artifactAddress = if (direct) {
            roots.firstOrNull { it.artifactName == mavenName }?.artifactAddress
        } else {
            transitiveDependencies[mavenName]?.artifactAddress
        }

        // Not found?
        artifactAddress ?: return null

        return libraryResolver.getLibrary(artifactAddress)
    }

    // Cache for [getAllLibraries]
    private var allLibraries: List<LmLibrary>? = null
    private var allItems: List<LmDependency>? = null

    override fun getAllGraphItems(): List<LmDependency> {
        return allItems
            ?: transitiveDependencies.values.toList()
                .also { allItems = it }
    }

    override fun getAllLibraries(): List<LmLibrary> {
        return allLibraries
            ?: getAllGraphItems().mapNotNull { libraryResolver.getLibrary(it.artifactAddress) }
                .toList()
                .also { allLibraries = it }
    }

    // For debugging purposes only; should not be used for other purposes such as persistence
    override fun toString(): String {
        return roots.toString()
    }
}

open class DefaultLmDependency(
    override val artifactName: String,
    override val artifactAddress: String,
    override val requestedCoordinates: String?,
    override val dependencies: List<LmDependency>,
    private val libraryResolver: LmLibraryResolver
) : LmDependency {
    override fun findLibrary(): LmLibrary? = libraryResolver.getLibrary(artifactAddress)

    // For debugging purposes only; should not be used for other purposes such as persistence
    override fun toString(): String =
        "$artifactAddress${if (dependencies.isNotEmpty()) dependencies.toString() else ""}"
}

class DefaultLmDependencies(
    override val compileDependencies: LmDependencyGraph,
    override val packageDependencies: LmDependencyGraph,
    private val libraryResolver: LmLibraryResolver
) : LmDependencies {
    override fun getLibraryResolver(): LmLibraryResolver = libraryResolver

    // For debugging purposes only; should not be used for other purposes such as persistence
    override fun toString(): String =
        "compile=$compileDependencies, package=$packageDependencies"
}

class DefaultLmLibraryResolver(
    val libraryMap: Map<String, LmLibrary>
) : LmLibraryResolver {

    override fun getAllLibraries(): Collection<LmLibrary> {
        return libraryMap.values.toList()
    }

    override fun getLibrary(artifactAddress: String): LmLibrary? = libraryMap[artifactAddress]
}
