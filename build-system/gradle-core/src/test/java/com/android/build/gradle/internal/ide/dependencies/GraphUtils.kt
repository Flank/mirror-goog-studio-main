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

import com.android.build.gradle.internal.fixtures.FakeBuildIdentifier
import com.android.build.gradle.internal.fixtures.FakeModuleComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeProjectComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeResolvedComponentResult
import com.android.build.gradle.internal.fixtures.FakeResolvedDependencyResult
import com.android.build.gradle.internal.fixtures.FakeResolvedVariantResult
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import java.io.File
import java.util.Optional

internal fun buildGraph(
    action: DependencyBuilder.() -> Unit
): Pair<Set<DependencyResult>, Set<ResolvedArtifact>> {
    // create one root that holds the dependency
    val root = DependencyBuilderImpl()
    action(root)

    return root.buildGraph()
}

interface DependencyBuilder {
    fun project(
        path: String,
        buildName: String = "defaultBuildName",
        action: ProjectDependencyNodeBuilder.() -> Unit
    ): ProjectDependencyNodeBuilder

    fun module(
        group: String,
        module: String,
        version: String,
        action: ModuleDependencyNodeBuilder.() -> Unit
    ): ModuleDependencyNodeBuilder

    fun dependency(node: DependencyNodeBuilder?)
    fun dependencyConstraint(node: DependencyNodeBuilder)
}

interface DependencyNodeBuilder: DependencyBuilder {
    var dependencyType: DependencyType
    var file: File

    fun capability(group: String, name: String, version: String? = null)
    fun <T: Any> attribute(attribute: Attribute<T>, value: T)
}

interface ProjectDependencyNodeBuilder: DependencyNodeBuilder

interface ModuleDependencyNodeBuilder: DependencyNodeBuilder {
    var availableAt: ModuleDependencyNodeBuilder?
    fun setupDefaultCapability()
}

open class DependencyBuilderImpl: DependencyBuilder {
    protected val children = mutableListOf<DependencyNodeBuilderImpl>()
    protected val constraints = mutableListOf<DependencyNodeBuilderImpl>()

    override fun project(
        path: String,
        buildName: String,
        action: ProjectDependencyNodeBuilder.() -> Unit
    ): ProjectDependencyNodeBuilder {
        return ProjectDependencyBuilderImpl(path, buildName).also {
            action(it)
            children.add(it)
        }
    }

    override fun module(
        group: String,
        module: String,
        version: String,
        action: ModuleDependencyNodeBuilder.() -> Unit
    ): ModuleDependencyNodeBuilder {
        return ModuleDependencyBuilderImpl(group, module, version).also {
            action(it)
            children.add(it)
        }
    }

    override fun dependency(node: DependencyNodeBuilder?) {
        (node as? DependencyNodeBuilderImpl)?.let { children.add(it) }
    }

    override fun dependencyConstraint(node: DependencyNodeBuilder) {
        check(this is DependencyNodeBuilder) {
            "Constraints must be added to existing node in the dependency graph."
        }
        (node as DependencyNodeBuilderImpl).let { constraints.add(it) }
    }

    fun buildGraph(): Pair<Set<DependencyResult>, Set<ResolvedArtifact>> {
        // go through graph, convert to depresult and resolved-artifacts.
        // make sure to re-use node that are already converted.
        val nodeCache = mutableMapOf<DependencyNodeBuilderImpl, FakeResolvedDependencyResult>()
        val artifacts = mutableSetOf<ResolvedArtifact>()
        for (child in children) {
            processNode(child, artifacts, nodeCache)
        }
        for (child in children) {
            processConstrains(child, nodeCache)
        }
        return nodeCache.filter { it.key in children }.values.toSet() to artifacts.toSet()
    }

    private fun processNode(
        node: DependencyNodeBuilderImpl,
        artifacts: MutableSet<ResolvedArtifact>,
        nodeCache: MutableMap<DependencyNodeBuilderImpl, FakeResolvedDependencyResult>
    ): DependencyResult {
        nodeCache[node]?.let { return it }

        val availableAt = if (node is ModuleDependencyNodeBuilder) node.availableAt else null
        var externalVariant: ResolvedVariantResult? = null

        val newChildren = LinkedHashSet<DependencyResult>()
        for (child in node.children) {
            val newChild = processNode(child, artifacts, nodeCache)
            if (child == availableAt) {
                externalVariant = (newChild as? ResolvedDependencyResult)?.resolvedVariant
            }
            newChildren.add(newChild)
        }

        val componentIdentifier = node.getComponentIdentifier()

        val resolvedVariantResult = FakeResolvedVariantResult(
            owner = componentIdentifier,
            attributes = FakeAttributeContainer(node.attributes),
            capabilities = node.capabilities,
            externalVariant = Optional.ofNullable(externalVariant)
        )

        // if there's an external variant due to relocation then that particular module
        // will not have a matching artifact.
        if (externalVariant == null) {
            artifacts.add(
                ResolvedArtifact(
                    componentIdentifier = componentIdentifier,
                    variant = resolvedVariantResult,
                    variantName = null, //FIXME
                    isTestFixturesArtifact = false,
                    artifactFile = node.file,
                    extractedFolder = null,
                    publishedLintJar = null,
                    dependencyType = node.dependencyType,
                    isWrappedModule = false, // does not really matter
                    buildMapping = ImmutableMap.of() // does not really matter
                )
            )
        }

        // the proper ResolvedVariantResult that contains the capabilities

        val resolvedComponentResult = FakeResolvedComponentResult(
            id = componentIdentifier,
            dependencies = newChildren,
            variant = resolvedVariantResult
        )

        return FakeResolvedDependencyResult(
            selected = resolvedComponentResult,
            resolvedVariant = resolvedVariantResult,
            constraint = false
        ).also {
            nodeCache[node] = it
        }
    }

    private fun processConstrains(
        node: DependencyNodeBuilderImpl,
        nodeCache: MutableMap<DependencyNodeBuilderImpl, FakeResolvedDependencyResult>
    ) {
        val graphNode = nodeCache.getValue(node)
        for (constraint in node.constraints) {
            val constraintNode = nodeCache.getValue(constraint)
            graphNode.addConstraint(constraintNode.copy(constraint = true))
        }
        for (child in node.children) {
            processConstrains(child, nodeCache)
        }
    }
}

abstract class DependencyNodeBuilderImpl: DependencyBuilderImpl(), DependencyNodeBuilder {
    override var dependencyType: DependencyType = DependencyType.JAVA

    val capabilities = mutableListOf<Capability>()
    val attributes = mutableMapOf<Attribute<*>, Any>()

    abstract fun getComponentIdentifier(): ComponentIdentifier

    override var file: File = File("")
    override fun capability(group: String, name: String, version: String?) {
        capabilities.add(FakeCapability(group, name, version))
    }

    override fun <T: Any> attribute(attribute: Attribute<T>, value: T) {
        attributes[attribute] = value
    }
}

private class ProjectDependencyBuilderImpl(
    val projectPath: String,
    val buildName: String
): DependencyNodeBuilderImpl(), ProjectDependencyNodeBuilder {

    override fun getComponentIdentifier(): ComponentIdentifier {
        return FakeProjectComponentIdentifier(
            projectPath = projectPath,
            buildIdentifier = FakeBuildIdentifier(buildName)
        )
    }
}

private class ModuleDependencyBuilderImpl(
    val group: String,
    val module: String,
    val version: String
): DependencyNodeBuilderImpl(), ModuleDependencyNodeBuilder {
    override var availableAt: ModuleDependencyNodeBuilder? = null

    override fun setupDefaultCapability() {
        capability(group, module, version)
    }

    override fun getComponentIdentifier(): ComponentIdentifier {
        return FakeModuleComponentIdentifier(group, module, version)
    }
}

private class FakeCapability(
    private val _group: String,
    private val _name: String,
    private val _version: String?
): Capability {
    override fun getGroup(): String = _group
    override fun getName(): String = _name
    override fun getVersion(): String? = _version
}

@Suppress("UNCHECKED_CAST")
private data class FakeAttributeContainer(
    private val map: Map<Attribute<*>, Any>
) : AttributeContainer {


    override fun getAttributes(): AttributeContainer = this // ?
    override fun keySet(): MutableSet<Attribute<*>> = map.keys.toMutableSet()

    override fun <T : Any> attribute(key: Attribute<T>, value: T): AttributeContainer {
        error("do not call this")
    }

    override fun <T : Any?> getAttribute(key: Attribute<T>): T? {
        return map[key] as? T?
    }

    override fun isEmpty(): Boolean = map.isEmpty()
    override fun contains(key: Attribute<*>): Boolean = map.containsKey(key)
}
