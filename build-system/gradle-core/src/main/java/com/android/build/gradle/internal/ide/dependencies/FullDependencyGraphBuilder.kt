/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.gradle.internal.attributes.VariantAttr
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.ide.v2.ArtifactDependenciesImpl
import com.android.build.gradle.internal.ide.v2.GlobalLibraryBuildService
import com.android.build.gradle.internal.ide.v2.GraphItemImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.errors.IssueReporter
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.GraphItem
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import java.io.File

class FullDependencyGraphBuilder(
    private val inputs: ArtifactCollectionsInputs,
    private val variantDependencies: VariantDependencies,
    private val globalLibraryBuildService: GlobalLibraryBuildService
) {

    private val unresolvedDependencies = mutableListOf<UnresolvedDependencyResult>()

    fun build(
        issueReporter: IssueReporter
    ): ArtifactDependencies {

        return ArtifactDependenciesImpl(
            buildGraph(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH),
            buildGraph(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH)
        )
    }

    private fun buildGraph(
        configType: AndroidArtifacts.ConsumedConfigType,

        ): List<GraphItem> {
        // query for the actual graph, and get the first level children.
        val roots: Set<DependencyResult> = variantDependencies.getResolutionResult(configType).root.dependencies

        val dependencyFailureHandler = DependencyFailureHandler()

        // get the artifact first. This is a flat list of items that have been computed
        // to contain information about the actual artifacts (whether they are sub-projects
        // or external dependencies, whether they are java or android, whether they are
        // wrapper local jar/aar, etc...)
        val artifacts = getAllArtifacts(inputs, configType, dependencyFailureHandler)

        val artifactMap = artifacts.associateBy { it.variant }

        // Keep a list of the visited nodes so that we don't revisit them in different branches.
        // This is a map so that we can easy get the matching GraphItem for it,
        val visited = mutableMapOf<ResolvedVariantResult, GraphItem>()

        val items = mutableListOf<GraphItem>()
        // at the top level, there can be a duplicate of all the dependencies if the graph is
        // setup via constraints, which is the case for our compile classpath always as the
        // constraints come from the runtime classpath
        for (dependency in roots.filter { !it.isConstraint }) {
            handleDependency(dependency, visited, artifactMap)?.let {
                items.add(it)
            }
        }

        // handle local Jars. They are not visited via the roots but they are part
        // of the artifacts list.
        val unvisitedArtifacts = artifacts.filter { it.componentIdentifier is OpaqueComponentArtifactIdentifier }

        for (artifact in unvisitedArtifacts) {
            val library = globalLibraryBuildService.getLibrary(artifact)
            items.add(GraphItemImpl(library.key, null, listOf()))
        }

        return items.toList()
    }

    private fun handleDependency(
        dependency: DependencyResult,
        visited: MutableMap<ResolvedVariantResult, GraphItem>,
        artifactMap: Map<ResolvedVariantResult, ResolvedArtifact>
    ): GraphItem? {
        when (dependency) {
            is ResolvedDependencyResult -> {
                val variant = dependency.resolvedVariant

                // check if we already visited this.
                val graphItem = visited[variant]
                if (graphItem != null) {
                    return graphItem
                }

                val artifact = artifactMap[variant]

                val library = if (artifact == null) {
                    // this can happen when resolving a test graph, as one of the roots will be
                    // the same module and this is not included in the other artifact-based API.
                    val owner = variant.owner
                    if (owner is ProjectComponentIdentifier &&
                        inputs.projectPath == owner.projectPath) {

                        // create on the fly a ResolvedArtifact around this project
                        // and get the matching library item
                        globalLibraryBuildService.getLibrary(
                            ResolvedArtifact(
                                variant.owner,
                                variant,
                                variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.toString()
                                        ?: "unknown",
                                File("wont/matter"),
                                null,
                                ResolvedArtifact.DependencyType.ANDROID,
                                false,
                                inputs.buildMapping,
                                inputs.mavenCoordinatesCache.get()
                            )
                        )
                    } else {
                        null
                    }
                } else {
                    // get the matching library item
                    globalLibraryBuildService.getLibrary(artifact)
                }

                if (library != null) {
                    // get the graph item for the children
                    val children =
                            dependency.selected.getDependenciesForVariant(variant).mapNotNull {
                                handleDependency(
                                    it, visited, artifactMap
                                )
                            }

                    // from there create a GraphItem if it does not exist yet.
                    return GraphItemImpl(
                        library.key,
                        null,
                        children
                    )
                }
            }
            is UnresolvedDependencyResult -> {
                unresolvedDependencies.add(dependency)
            }
        }

        return null
    }

    private fun VariantDependencies.getResolutionResult(
        type: AndroidArtifacts.ConsumedConfigType
    ): ResolutionResult = when (type) {
        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH -> compileClasspath.incoming.resolutionResult
        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeClasspath.incoming.resolutionResult
        else -> throw RuntimeException("Unsupported ConsumedConfigType value: $type")
    }
}
