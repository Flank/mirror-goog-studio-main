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
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import java.io.File

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
        action: DependencyNodeBuilder.() -> Unit
    ): DependencyNodeBuilder

    fun module(
        group: String,
        module: String,
        version: String,
        action: DependencyNodeBuilder.() -> Unit
    ): DependencyNodeBuilder
    fun dependency(node: DependencyNodeBuilder?)
}

interface DependencyNodeBuilder: DependencyBuilder {
    var dependencyType: DependencyType
    var file: File

    fun capability(group: String, name: String, version: String? = null)
    fun <T: Any> attribute(attribute: Attribute<T>, value: T)
}

open class DependencyBuilderImpl: DependencyBuilder {
    protected val children = mutableListOf<DependencyNodeBuilderImpl>()

    override fun project(
        path: String,
        buildName: String,
        action: DependencyNodeBuilder.() -> Unit
    ): DependencyNodeBuilder {
        return ProjectDependencyBuilderImpl(path, buildName).also {
            action(it)
            children.add(it)
        }
    }

    override fun module(
        group: String,
        module: String,
        version: String,
        action: DependencyNodeBuilder.() -> Unit
    ): DependencyNodeBuilder {
        return ModuleDependencyBuilderImpl(group, module, version).also {
            action(it)
            children.add(it)
        }
    }

    override fun dependency(node: DependencyNodeBuilder?) {
        (node as? DependencyNodeBuilderImpl)?.let { children.add(it) }
    }

    fun buildGraph(): Pair<Set<DependencyResult>, Set<ResolvedArtifact>> {
        // go through graph, convert to depresult and resolved-artifacts.
        // make sure to re-use node that are already converted.

        val results = LinkedHashSet<DependencyResult>()
        val artifacts = mutableSetOf<ResolvedArtifact>()
        for (child in children) {
            results.add(processNode(child, artifacts))
        }

        return results to artifacts.toSet()
    }

    private fun processNode(
        node: DependencyNodeBuilderImpl,
        artifacts: MutableSet<ResolvedArtifact>
    ): DependencyResult {
        val newChildren = LinkedHashSet<DependencyResult>()
        for (child in node.children) {
            newChildren.add(processNode(child, artifacts))
        }

        val componentIdentifier = node.getComponentIdentifier()

        val resolvedVariantResult = FakeResolvedVariantResult(
            owner = componentIdentifier,
            attributes = FakeAttributeContainer(node.attributes),
            capabilities = node.capabilities,
        )

        artifacts.add(
            ResolvedArtifact(
                componentIdentifier = componentIdentifier,
                variant = resolvedVariantResult,
                variantName = null, //FIXME
                artifactFile = node.file,
                extractedFolder = null,
                publishedLintJar = null,
                dependencyType = node.dependencyType,
                isWrappedModule = false, // does not really matter
                buildMapping = ImmutableMap.of() // does not really matter
            )
        )

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
        )
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
): DependencyNodeBuilderImpl() {

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
): DependencyNodeBuilderImpl() {

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
