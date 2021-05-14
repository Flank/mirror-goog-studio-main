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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeResolutionResult
import com.android.build.gradle.internal.fixtures.addDependencyEdge
import com.android.build.gradle.internal.fixtures.createModuleComponent
import com.android.build.gradle.internal.fixtures.createProjectComponent
import com.android.build.gradle.internal.tasks.bundle.appDependencies
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.common.truth.Truth.assertThat
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class PerModuleReportDependenciesTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    internal lateinit var project: Project
    lateinit var task: PerModuleReportDependenciesTask

    @Mock private lateinit var mockConfiguration: ConfigurationInternal
    @Mock private lateinit var mockResolvableDependencies: ResolvableDependencies
    @Mock private lateinit var mockArtifactView: ArtifactView

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        task = project.tasks.create("taskUnderTest", PerModuleReportDependenciesTask::class.java)
        task.runtimeClasspathName.set("dummyConfigurationName")
        task.moduleName.set("base")
        task.dependencyReport.set(project.file("dependencies.pb"))

        Mockito.`when`(mockConfiguration.incoming).thenReturn(mockResolvableDependencies)
        Mockito.`when`(mockConfiguration.name).thenReturn("dummyConfigurationName")
        project.configurations.add(mockConfiguration)
    }

    @Test
    fun testReportGeneration() {
        // Test-specific Task configuration -- stipulate that Repositories should not be included
        task.includeRepositoryInfo.set(false)

        // Test-specific Project configuration -- Build a fake dependency graph
        val rootComponent = createProjectComponent("root_module")
        val subProjectComponent = createProjectComponent("lib_module")
        val a = createModuleComponent("foo", "apple", "1.1")
        val b = createModuleComponent("foo", "banana", "1.2")
        val c = createModuleComponentInternal("foo", "cucumber", "1.1", "iffy")
        val d = createModuleComponentInternal("bar", "banana", "2.0", "maben")
        val e = createModuleComponent("baz", "durian", "bleh")
        val f = createModuleComponent("bar", "apple", "2.0")

        addDependencyEdge(rootComponent, subProjectComponent)
        addDependencyEdge(rootComponent, a)
        addDependencyEdge(rootComponent, d)
        addDependencyEdge(a, b)
        addDependencyEdge(b, c)
        addDependencyEdge(subProjectComponent, b)
        addDependencyEdge(subProjectComponent, e)
        addDependencyEdge(a, f)

        val stubbedGraph = FakeResolutionResult(rootComponent)
        val stubbedArtifactCollection = FakeArtifactCollection(mutableSetOf())

        Mockito.`when`(mockResolvableDependencies.artifactView(any())).thenReturn(mockArtifactView)
        Mockito.`when`(mockArtifactView.artifacts).thenReturn(stubbedArtifactCollection)
        Mockito.`when`(mockResolvableDependencies.resolutionResult).thenReturn(stubbedGraph)

        val expected = appDependencies {
            addLibrary("foo", "apple", "1.1")
            addLibrary("foo", "banana", "1.2")
            addLibrary("foo", "cucumber", "1.1")
            addLibrary("bar", "banana", "2.0")
            addLibrary("baz", "durian", "bleh")
            addLibrary("bar", "apple", "2.0")
            addLibraryDeps(0, 1, 5)
            addLibraryDeps(1, 2)
            addModuleDeps("base", 0, 1, 3, 4)
        }

        task.taskAction()

        val allDeps = AppDependencies.parseFrom(FileInputStream(task.dependencyReport.get().asFile))

        assertThat(allDeps.libraryList).containsExactlyElementsIn(expected.libraryList)
        //FIXME all assertions below are woefully inadequate
        assertThat(allDeps.libraryDependenciesCount).isEqualTo(expected.libraryDependenciesCount)
        assertThat(allDeps.moduleDependenciesCount).isEqualTo(expected.moduleDependenciesCount)
        assertThat(allDeps.repositoriesCount).isEqualTo(expected.repositoriesCount)
    }

    @Test
    fun testReportGenerationWithRepositoriesIncluded() {
        // Test-specific Task configuration -- stipulate that Repositories should be included
        task.includeRepositoryInfo.set(true)
        project.repositories.add(mavenRepoMock("maven1", "fakeUrl1"))
        project.repositories.add(ivyRepoMock("ivy1", "fakeUrl2"))
        project.repositories.add(mavenRepoMock("maven2", "file://fakeUrl3"))
        project.repositories.add(ivyRepoMock("ivy2", "fakeUrl4"))
        PerModuleReportDependenciesTask.CreationAction.run {
            task.projectRepositories.set(project.repositories.toInternalRepoMetadataList())
        }

        // Test-specific Project configuration -- Build a fake dependency graph
        val rootComponent = createProjectComponent("root_module")
        val subProjectComponent = createProjectComponent("lib_module")
        val a = createModuleComponent("foo", "apple", "1.1")
        val b = createModuleComponent("foo", "banana", "1.2")
        val c = createModuleComponentInternal("foo", "cucumber", "1.1", "ivy1")
        val d = createModuleComponentInternal("bar", "banana", "2.0", "maven1")
        val e = createModuleComponent("baz", "durian", "bleh")
        val f = createModuleComponent("bar", "apple", "2.0")

        addDependencyEdge(rootComponent, subProjectComponent)
        addDependencyEdge(rootComponent, a)
        addDependencyEdge(rootComponent, d)
        addDependencyEdge(a, b)
        addDependencyEdge(b, c)
        addDependencyEdge(subProjectComponent, b)
        addDependencyEdge(subProjectComponent, e)
        addDependencyEdge(a, f)

        val stubbedGraph = FakeResolutionResult(rootComponent)
        val stubbedArtifactCollection = FakeArtifactCollection(mutableSetOf())

        Mockito.`when`(mockResolvableDependencies.artifactView(any())).thenReturn(mockArtifactView)
        Mockito.`when`(mockArtifactView.artifacts).thenReturn(stubbedArtifactCollection)
        Mockito.`when`(mockResolvableDependencies.resolutionResult).thenReturn(stubbedGraph)

        val expected = appDependencies {
            addLibrary("foo", "apple", "1.1")
            addLibrary("foo", "banana", "1.2")
            addLibrary("foo", "cucumber", "1.1").setRepoIndex(1)
            addLibrary("bar", "banana", "2.0").setRepoIndex(0)
            addLibrary("baz", "durian", "bleh")
            addLibrary("bar", "apple", "2.0")
            addLibraryDeps(0, 1, 5)
            addLibraryDeps(1, 2)
            addModuleDeps("base", 0, 1, 3, 4)
            addMavenRepository("fakeUrl1")
            addIvyRepository("fakeUrl2")
            addIvyRepository("fakeUrl4")
        }

        task.taskAction()

        val allDeps = AppDependencies.parseFrom(FileInputStream(task.dependencyReport.get().asFile))

        assertThat(allDeps.libraryList).containsExactlyElementsIn(expected.libraryList)
        //FIXME all assertions below are woefully inadequate
        assertThat(allDeps.libraryDependenciesCount).isEqualTo(expected.libraryDependenciesCount)
        assertThat(allDeps.moduleDependenciesCount).isEqualTo(expected.moduleDependenciesCount)
        assertThat(allDeps.repositoriesCount).isEqualTo(expected.repositoriesCount)
    }
}

private fun mavenRepoMock(repoName: String, repoUrl: String): MavenArtifactRepository =
    mock<DefaultMavenArtifactRepository>().apply {
        Mockito.`when`(name).thenReturn(repoName)
        Mockito.`when`(url).thenReturn(URI.create(repoUrl))
    }

private fun ivyRepoMock(repoName: String, repoUrl: String): IvyArtifactRepository =
    mock<DefaultIvyArtifactRepository>().apply {
        Mockito.`when`(name).thenReturn(repoName)
        Mockito.`when`(url).thenReturn(URI.create(repoUrl))
    }

// TODO: simulate breaking change in internal API?
//    I.e. -- implement ResolvedComponentResultInternal, but throw error error in "getRepositoryName"?

private fun createModuleComponentInternal(group:String, name:String, version:String, repositoryName:String) =
    FakeResolvedComponentResultInternal(createModuleComponent(group,name,version), repositoryName)

private class FakeResolvedComponentResultInternal(thingy: ResolvedComponentResult, private val repositoryName:String ) :
    ResolvedComponentResult by thingy, ResolvedComponentResultInternal {
    override fun getRepositoryName(): String = repositoryName
}
