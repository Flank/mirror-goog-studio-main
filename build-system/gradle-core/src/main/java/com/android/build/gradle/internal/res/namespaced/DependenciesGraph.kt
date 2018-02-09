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

package com.android.build.gradle.internal.res.namespaced

import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

typealias ArtifactFiles = Map<ArtifactType, ImmutableMap<String, File>>

/**
 * Represents a graph of dependencies, with custom Nodes capable of fetching artifact files.
 * For examples of usages look at the DependenciesGraphTest.
 */
class DependenciesGraph(val rootNodes: ImmutableSet<Node>, val allNodes: ImmutableSet<Node>) {

    companion object {
        /**
         * Creates an instance of the DependenciesGraph based on the given ResolvableDependencies
         * object.
         * For examples of usages look at the DependenciesGraphTest.
         *
         * @param dependencies resolvable dependencies (can be local or remote)
         * @param artifacts an optional map that stores a mapping of component ID to artifact file
         *          for an ArtifactType
         */
        fun create(
            dependencies: ResolvableDependencies,
            artifacts: ArtifactFiles = ImmutableMap.of()
        ): DependenciesGraph {
            return create(
                    dependencies.resolutionResult.root.dependencies,
                    HashMap(),
                    artifacts
            )
        }

        @VisibleForTesting
        fun create(
            roots: Iterable<DependencyResult>,
            foundNodes: HashMap<ComponentIdentifier, Node>,
            artifacts: ArtifactFiles
        ): DependenciesGraph {
            val rootNodes = mutableSetOf<Node>()
            // We can have multiple roots. Collect nodes starting from each of them.
            for (dependency in roots) {
                dependency as ResolvedDependencyResult
                val node = collect(dependency, foundNodes, artifacts)
                foundNodes.put(dependency.selected.id, node)
                rootNodes.add(node)
            }
            return DependenciesGraph(
                    ImmutableSet.copyOf(rootNodes),
                    ImmutableSet.copyOf(foundNodes.values)
            )
        }

        private fun collect(
            dependencyResult: DependencyResult,
            foundNodes: HashMap<ComponentIdentifier, Node>,
            artifacts: ArtifactFiles
        ): Node {
            dependencyResult as ResolvedDependencyResult
            // Visit all children of the node and collect them. If a child has already been visited,
            // it will be stored in the 'foundNodes' map.
            val dependencies = ImmutableSet.copyOf(
                    dependencyResult.selected.dependencies.mapNotNull { dependency ->
                        dependency as ResolvedDependencyResult
                        foundNodes.computeIfAbsent(
                                dependency.selected.id,
                                { collect(dependency, foundNodes, artifacts) }
                        )
                    }
            )
            return Node(dependencyResult.selected.id, dependencies, artifacts)
        }
    }

    /**
     * Class for storing information about a dependency node.
     */
    class Node(
        val id: ComponentIdentifier,
        val dependencies: ImmutableSet<Node>,
        artifactFiles: ArtifactFiles
    ) {
        val artifacts: ImmutableMap<ArtifactType, File>
        private val transitiveArtifactCache: HashMap<ArtifactType, ImmutableList<File>> = HashMap()

        init {
            val builder = ImmutableMap.builder<ArtifactType, File>()
            for (type in artifactFiles.keys) {
                val file = artifactFiles[type]!![id.displayName] ?:
                        error("Missing artifact for ID ${id.displayName} and type ${type.type}.")
                builder.put(type, file)
            }
            artifacts = builder.build()
        }

        fun getFile(type: ArtifactType): File {
            return artifacts[type] ?:
                    error("Node with ID ${id.displayName} does not contain an artifact of type" +
                            " ${type.type}")
        }

        fun getTransitiveFiles(type: ArtifactType): ImmutableList<File> {
            if (transitiveArtifactCache.containsKey(type)) {
                return transitiveArtifactCache[type]!!
            }
            val builder = ArrayList<File>()
            // Add ourselves first.
            builder.add(getFile(type))
            // We have only the immediate children, go through them in alphabetical order to make it
            // more deterministic.
            val children = dependencies.asList()
            children.sortedBy { it.id.displayName }
            for (child in children) {
                for (childFile in child.getTransitiveFiles(type)) {
                    if (!builder.contains(childFile)) {
                        builder.add(childFile)
                    }
                }
            }
            transitiveArtifactCache.put(type, ImmutableList.copyOf(builder))
            return transitiveArtifactCache[type]!!
        }
    }
}
