/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.android.build.api.artifact.BuildableArtifact
import com.android.builder.core.BuilderConstants
import com.android.ide.common.resources.AssetSet
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.function.Consumer
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.function.Supplier

class MergeSourceSetFoldersTest {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var task: MergeSourceSetFolders
    private lateinit var folderSets: MutableList<AssetSet>

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", MergeSourceSetFolders::class.java)

        folderSets = Lists.newArrayList()
        task.assetSetSupplier = Supplier { folderSets }
    }

    @Test
    @Throws(Exception::class)
    fun singleSetWithSingleFile() {
        val file = File("src/main")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file)

        assertThat(task.computeAssetSetList()).containsExactly(mainSet)
    }

    @Test
    @Throws(Exception::class)
    fun singleSetWithMultiFiles() {
        val file = File("src/main")
        val file2 = File("src/main2")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2)

        assertThat(task.computeAssetSetList()).containsExactly(mainSet)
    }

    @Test
    @Throws(Exception::class)
    fun twoSetsWithSingleFile() {
        val file = File("src/main")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file)

        val file2 = File("src/debug")
        val debugSet = createAssetSet(folderSets, "debug", file2)

        assertThat(task.computeAssetSetList()).containsExactly(mainSet, debugSet)
    }

    @Test
    @Throws(Exception::class)
    fun singleSetWithDependency() {
        val file = File("src/main")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file)

        val file2 = File("foo/bar/1.0")
        val librarySets = setupLibraryDependencies(file2, ":path")

        assertThat(task.getLibraries()!!.files).containsExactly(file2)
        assertThat(task.computeAssetSetList()).containsExactly(librarySets[0], mainSet).inOrder()
    }

    @Test
    @Throws(Exception::class)
    fun singleSetWithRenderscript() {
        val file = File("src/main")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file)

        val shaderFile = File("shader")
        setBuildableArtifact({ task.shadersOutputDir = it }, shaderFile)

        assertThat(task.computeAssetSetList()).containsExactly(mainSet)
        // shader file should have been added to the main resource sets.
        assertThat(mainSet.sourceFiles).containsExactly(file, shaderFile)
    }

    @Test
    @Throws(Exception::class)
    fun everything() {
        val file = File("src/main")
        val file2 = File("src/main2")
        val mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2)

        val debugFile = File("src/debug")
        val debugSet = createAssetSet(folderSets, "debug", debugFile)

        val libFile = File("foo/bar/1.0")
        val libFile2 = File("foo/bar/2.0")

        // the order returned by the dependency is meant to be in the wrong order (consumer first,
        // when we want dependent first for the merger), so the order in the asset set should be
        // the opposite order.
        val librarySets = setupLibraryDependencies(
            libFile, "foo:bar:1.0",
            libFile2, "foo:bar:2.0"
        )
        val librarySet = librarySets[0]
        val librarySet2 = librarySets[1]

        val shaderFile = File("shader")
        setBuildableArtifact({ task.shadersOutputDir = it }, shaderFile)

        assertThat(task.getLibraries()!!.files).containsExactly(libFile, libFile2)
        assertThat(task.computeAssetSetList())
            .containsExactly(librarySet2, librarySet, mainSet, debugSet)
            .inOrder()
    }

    private fun createAssetSet(
        folderSets: MutableList<AssetSet>?,
        name: String,
        vararg files: File
    ): AssetSet {
        val mainSet = AssetSet(name)
        mainSet.addSources(Arrays.asList(*files))
        folderSets?.add(mainSet)
        return mainSet
    }

    private fun setBuildableArtifact(setter: (BuildableArtifact) -> Unit, vararg files: File) {
        val fileCollection = mock(BuildableArtifact::class.java)
        val fileSet = ImmutableSet.copyOf(Arrays.asList(*files))
        `when`(fileCollection.files).thenReturn(fileSet)
        setter(fileCollection)
    }

    private fun setupLibraryDependencies(vararg objects: Any): List<AssetSet> {
        val libraries = mock(ArtifactCollection::class.java)

        val artifacts = LinkedHashSet<ResolvedArtifactResult>()
        val files = HashSet<File>()
        val assetSets = Lists.newArrayListWithCapacity<AssetSet>(objects.size / 2)

        var i = 0
        val count = objects.size
        while (i < count) {
            assertThat(objects[i]).isInstanceOf(File::class.java)
            assertThat(objects[i + 1]).isInstanceOf(String::class.java)

            val file = objects[i] as File
            val path = objects[i + 1] as String

            files.add(file)

            val artifact = mock(ResolvedArtifactResult::class.java)
            artifacts.add(artifact)

            val artifactId = mock(ComponentArtifactIdentifier::class.java)
            val id = mock(ProjectComponentIdentifier::class.java)

            `when`(id.projectPath).thenReturn(path)
            `when`<ComponentIdentifier>(artifactId.componentIdentifier).thenReturn(id)
            `when`(artifact.file).thenReturn(file)
            `when`(artifact.id).thenReturn(artifactId)

            // create a resource set that must match the one returned by the computation
            val set = AssetSet(path)
            set.addSource(file)
            assetSets.add(set)
            i += 2
        }

        val fileCollection = mock(FileCollection::class.java)
        `when`(fileCollection.files).thenReturn(files)

        `when`(libraries.artifacts).thenReturn(artifacts)
        `when`(libraries.artifactFiles).thenReturn(fileCollection)

        task.libraryCollection = libraries

        return assetSets
    }
}