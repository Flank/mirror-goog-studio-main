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

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.gradle.internal.dependency.ResolutionResultProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType.ANDROID
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.junit.Test
import org.mockito.Mockito
import java.io.File

internal class FullDependencyGraphBuilderTest {

    val objectFactory = FakeObjectFactory.factory

    @Test
    fun `basic dependency`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0||foo:bar:1.0")
        val item = graphs.compileDependencies.first()
        Truth.assertThat(item.dependencies).isEmpty()
    }

    @Test
    fun `basic attributes`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
                attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    objectFactory.named(BuildTypeAttr::class.java, "debug")
                )
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0|debug||foo:bar:1.0")
        val item = graphs.compileDependencies.first()
        Truth.assertThat(item.dependencies).isEmpty()
    }

    @Test
    fun `graph with java module test fixtures`() {
        val (graphs, libraries) = buildModelGraph {
            val mainLib = module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
            }
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-test-fixtures-1.0.jar")
                capability("foo", "bar-test-fixtures", "1.0")

                dependency(mainLib)
            }
        }

        val compileDependencies = graphs.compileDependencies
        Truth
            .assertThat(compileDependencies.map { it.key })
            .containsExactly(
                "foo|bar|1.0||foo:bar:1.0",
                "foo|bar|1.0||foo:bar-test-fixtures:1.0"
            )

        // check that the dependency instance of the fixture is the same instance as the main
        val fixture =
            compileDependencies.single { it.key == "foo|bar|1.0||foo:bar-test-fixtures:1.0" }
        val main = compileDependencies.single { it.key == "foo|bar|1.0||foo:bar:1.0" }

        Truth.assertThat(fixture.dependencies.single()).isSameInstanceAs(main)
    }

    @Test
    fun `graph with android project test fixtures`() {
        val (graphs, libraries) = buildModelGraph {
            val mainLib = project(":foo") {
                dependencyType = ANDROID
                file = File("path/to/mergedManifest/debug/AndroidManifest.xml")
                capability("foo", "bar", "1.0")
            }
            project(":foo") {
                dependencyType = ANDROID
                file = File("path/to/mergedManifest/debugTestFixtures/AndroidManifest.xml")
                capability("foo", "bar-test-fixtures", "1.0")

                dependency(mainLib)
            }
        }

        val compileDependencies = graphs.compileDependencies
        Truth
            .assertThat(compileDependencies.map { it.key })
            .containsExactly(
                "defaultBuildName|:foo||foo:bar:1.0",
                "defaultBuildName|:foo||foo:bar-test-fixtures:1.0"
            )

        // check that the dependency instance of the fixture is the same instance as the main
        val fixture =
            compileDependencies.single { it.key == "defaultBuildName|:foo||foo:bar-test-fixtures:1.0" }
        val main = compileDependencies.single { it.key == "defaultBuildName|:foo||foo:bar:1.0" }

        Truth.assertThat(fixture.dependencies.single()).isSameInstanceAs(main)
    }

    @Test
    fun `relocated artifact`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                setupDefaultCapability()

                availableAt = module("relocated-foo", "bar", "1.0") {
                    file = File("path/to/bar-1.0.jar")
                    setupDefaultCapability()
                }
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0||foo:bar:1.0")

        val rootLibrary =
            libraries["foo|bar|1.0||foo:bar:1.0"]
                ?: throw RuntimeException("Failed to find root library")
        Truth.assertWithMessage("root library").that(rootLibrary.artifact).isNull()
        Truth.assertWithMessage("root library").that(rootLibrary.type).isEqualTo(LibraryType.RELOCATED)

        val children = graphs.compileDependencies.single().dependencies
        Truth
            .assertThat(children.map { it.key })
            .containsExactly("relocated-foo|bar|1.0||relocated-foo:bar:1.0")
    }

    @Test
    fun testDependencyConstraint() {
        val (graphs, libraries) = buildModelGraph {
            val barParentLib = module("foo", "bar-parent", "1.0") {
                file = File("path/to/bar-parent-1.0.jar")
            }

            val barLib = module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                dependency(barParentLib)
            }
            barParentLib.dependencyConstraint(barLib)
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar-parent|1.0||", "foo|bar|1.0||")
    }
}

// -------------

private fun buildModelGraph(action: DependencyBuilder.() -> Unit): Pair<ArtifactDependencies, Map<String, Library>> {
    val stringCache = StringCacheImpl()
    val localJarCache = LocalJarCacheImpl()
    val libraryService = LibraryServiceImpl(stringCache, localJarCache)

    val (dependencyResults, resolvedArtifacts) = buildGraph(action)

    val builder = FullDependencyGraphBuilder(
        getInputs(resolvedArtifacts),
        getResolutionResultProvider(dependencyResults),
        libraryService
    )

    return builder.build() to libraryService.getAllLibraries().associateBy { it.key }
}

private fun getInputs(artifacts: Set<ResolvedArtifact>): ArtifactCollectionsInputs =
    FakeArtifactCollectionsInputs(artifacts)

private fun getResolutionResultProvider(
    compileResultsResults: Set<DependencyResult>
): ResolutionResultProvider {
    val result = Mockito.mock(ResolutionResult::class.java)
    val root = Mockito.mock(ResolvedComponentResult::class.java)
    Mockito.`when`(result.root).thenReturn(root)
    Mockito.`when`(root.dependencies).thenReturn(compileResultsResults)

    return ResolutionResultProviderImpl(result, result)
}

private class ResolutionResultProviderImpl(
    private val compileResult: ResolutionResult,
    private val runtimeResult: ResolutionResult
): ResolutionResultProvider {

    override fun getResolutionResult(
        configType: AndroidArtifacts.ConsumedConfigType
    ): ResolutionResult =
            when (configType) {
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH -> compileResult
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeResult
                else -> throw RuntimeException("Unsupported ConsumedConfigType value: $configType")
            }
}

private class FakeArtifactCollectionsInputs(
    private val artifacts: Set<ResolvedArtifact>,
    override val projectPath: String = ":path:to:project",
    override val variantName: String = "variant-name"
): ArtifactCollectionsInputs {

    override fun getAllArtifacts(
        consumedConfigType: AndroidArtifacts.ConsumedConfigType,
        dependencyFailureHandler: DependencyFailureHandler?
    ): Set<ResolvedArtifact> {
        return artifacts
    }

    override val buildMapping: ImmutableMap<String, String>
        get() = ImmutableMap.of()

    override val compileClasspath: ArtifactCollections
        get() = TODO("Not needed by code under test")

    override val runtimeClasspath: ArtifactCollections
        get() = TODO("Not needed by code under test")

    override val runtimeLintJars: ArtifactCollection
        get() = TODO("Not needed by code under test")

    override val compileLintJars: ArtifactCollection
        get() = TODO("Not needed by code under test")

    override val level1RuntimeArtifactCollections: Level1RuntimeArtifactCollections
        get() = TODO("Not needed by code under test")
}
