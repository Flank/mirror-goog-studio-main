/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl;
import com.android.build.gradle.internal.fixtures.FakeFileCollection;
import com.android.builder.core.BuilderConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DependencyResourcesComputerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private DependencyResourcesComputer computer;
    private List<ResourceSet> folderSets;
    private Map<String, BuildableArtifact> artifactMap;

    @Before
    public void setUp() throws IOException {
        computer = new DependencyResourcesComputer();

        // set some default file collection on the required inputs
        FileCollection empty = mock(FileCollection.class);
        computer.setRenderscriptResOutputDir(empty);
        computer.setGeneratedResOutputDir(empty);

        folderSets = Lists.newArrayList();
        artifactMap = new LinkedHashMap<>();
        computer.setResources(artifactMap);
    }

    @After
    public void tearDown() {
        computer = null;
        folderSets = null;
    }

    @Test
    public void singleSetWithSingleFile() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        assertThat(computer.compute()).containsExactly(mainSet);
    }

    @Test
    public void singleSetWithMultiFiles() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        File file2 = temporaryFolder.newFolder("src", "main2");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file, file2);

        assertThat(computer.compute()).containsExactly(mainSet);
    }

    @Test
    public void twoSetsWithSingleFile() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File file2 = temporaryFolder.newFolder("src", "debug");
        ResourceSet debugSet = createResourceSet(folderSets, artifactMap, "debug", file2);

        assertThat(computer.compute()).containsExactly(mainSet, debugSet);
    }

    @Test
    public void singleSetWithDependency() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File file2 = temporaryFolder.newFolder("foo", "bar", "1.0");
        List<ResourceSet> librarySets = setupLibraryDependencies(file2, ":path");

        assertThat(computer.getLocalLibraries().getArtifactFiles()).containsExactly(file2);

        List<ResourceSet> computedSets = computer.compute();
        assertThat(computedSets).containsExactly(librarySets.get(0), mainSet).inOrder();
    }

    @Test
    public void singleSetWithRenderscript() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File rsFile = temporaryFolder.newFolder("rs");
        setFileCollection(computer::setRenderscriptResOutputDir, rsFile);
        mainSet.addSource(rsFile);

        assertThat(computer.compute()).containsExactly(mainSet);
        // rs file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, rsFile);
    }

    @Test
    public void singleSetWithGeneratedRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File genFile = temporaryFolder.newFolder("generated");
        setFileCollection(computer::setGeneratedResOutputDir, genFile);
        mainSet.addSource(genFile);

        assertThat(computer.compute()).containsExactly(mainSet);
        // generated file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, genFile);
    }

    @Test
    public void singleSetWithMicroApkRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File microFile = temporaryFolder.newFolder("micro");
        setFileCollection(computer::setMicroApkResDirectory, microFile);
        mainSet.addSource(microFile);

        assertThat(computer.compute()).containsExactly(mainSet);
        // micro file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, microFile);
    }

    @Test
    public void singleSetWithExtraRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file);

        File extraFile = temporaryFolder.newFolder("extra");
        setFileCollectionSupplier(computer::setExtraGeneratedResFolders, extraFile);
        mainSet.addSource(extraFile);

        assertThat(computer.compute()).containsExactly(mainSet);
        // rs file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, extraFile);
    }

    @Test
    public void everything() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        File file2 = temporaryFolder.newFolder("src", "main2");
        ResourceSet mainSet =
                createResourceSet(folderSets, artifactMap, BuilderConstants.MAIN, file, file2);

        File debugFile = temporaryFolder.newFolder("src", "debug");
        ResourceSet debugSet = createResourceSet(folderSets, artifactMap, "debug", debugFile);

        File libFile = temporaryFolder.newFolder("foo", "bar", "1.0");
        File libFile2 = temporaryFolder.newFolder("foo", "bar", "2.0");

        // the order returned by the dependency is meant to be in the wrong order (consumer first,
        // when we want dependent first for the merger), so the order in the res set should be
        // the opposite order.
        List<ResourceSet> librarySets = setupLibraryDependencies(
                libFile, ":path1",
                libFile2, ":path2");
        ResourceSet librarySet = librarySets.get(0);
        ResourceSet librarySet2 = librarySets.get(1);

        // Note: the order of files are added to mainSet matters.
        File rsFile = temporaryFolder.newFolder("rs");
        setFileCollection(computer::setRenderscriptResOutputDir, rsFile);
        mainSet.addSource(rsFile);

        File genFile = temporaryFolder.newFolder("generated");
        setFileCollection(computer::setGeneratedResOutputDir, genFile);
        mainSet.addSource(genFile);

        File extraFile = temporaryFolder.newFolder("extra");
        setFileCollectionSupplier(computer::setExtraGeneratedResFolders, extraFile);
        mainSet.addSource(extraFile);

        File microFile = temporaryFolder.newFolder("micro");
        setFileCollection(computer::setMicroApkResDirectory, microFile);
        mainSet.addSource(microFile);

        assertThat(computer.getLocalLibraries().getArtifactFiles())
                .containsExactly(libFile, libFile2);
        assertThat(computer.compute())
                .containsExactly(librarySet2, librarySet, mainSet, debugSet)
                .inOrder();
        // generated files should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles())
                .containsExactly(file, file2, rsFile, genFile, extraFile, microFile);
        assertThat(computer.getLocalLibraries().getArtifactFiles())
                .containsExactly(libFile, libFile2);
    }

    @NonNull
    private ResourceSet createResourceSet(
            List<ResourceSet> folderSets,
            Map<String, BuildableArtifact> artifactMap,
            String name,
            File... files) {
        ResourceSet mainSet = new ResourceSet(name, ResourceNamespace.RES_AUTO, null, false);
        BuildableArtifact artifact =
                new BuildableArtifactImpl(new FakeFileCollection(Arrays.asList(files)));
        artifactMap.put(name, artifact);
        mainSet.addSources(artifact.getFiles());
        folderSets.add(mainSet);
        return mainSet;
    }

    private static void setFileCollection(Consumer<FileCollection> setter, File... files) {
        FileCollection fileCollection = mock(FileCollection.class);
        Set<File> fileSet = ImmutableSet.copyOf(Arrays.asList(files));
        when(fileCollection.getFiles()).thenReturn(fileSet);
        setter.accept(fileCollection);
    }

    private static void setFileCollectionSupplier(
            Consumer<FileCollection> setter,
            File... files) {
        FileCollection fileCollection = mock(FileCollection.class);
        Set<File> fileSet = ImmutableSet.copyOf(Arrays.asList(files));
        when(fileCollection.getFiles()).thenReturn(fileSet);
        setter.accept(fileCollection);
    }

    @NonNull
    private List<ResourceSet> setupLibraryDependencies(Object... objects) {
        ArtifactCollection libraries = mock(ArtifactCollection.class);

        Set<ResolvedArtifactResult> artifacts = new LinkedHashSet<>();
        Set<File> files = new HashSet<>();
        List<ResourceSet> resourceSets = Lists.newArrayListWithCapacity(objects.length/2);

        for (int i = 0, count = objects.length; i < count ; i+=2) {
            assertThat(objects[i]).isInstanceOf(File.class);
            assertThat(objects[i+1]).isInstanceOf(String.class);

            File file = (File) objects[i];
            String path = (String) objects[i+1];

            files.add(file);

            ResolvedArtifactResult artifact = mock(ResolvedArtifactResult.class);
            artifacts.add(artifact);

            ComponentArtifactIdentifier artifactId = mock(ComponentArtifactIdentifier.class);
            ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);

            when(id.getProjectPath()).thenReturn(path);
            when(artifactId.getComponentIdentifier()).thenReturn(id);
            when(artifact.getFile()).thenReturn(file);
            when(artifact.getId()).thenReturn(artifactId);

            // create a resource set that must match the one returned by the computation
            ResourceSet set = new ResourceSet(path, ResourceNamespace.RES_AUTO, null, false);
            set.addSource(file);
            set.setFromDependency(true);
            resourceSets.add(set);
        }

        when(libraries.getArtifacts()).thenReturn(artifacts);
        when(libraries.getArtifactFiles()).thenReturn(new FakeFileCollection(files));

        computer.setLocalLibraries(libraries);

        return resourceSets;
    }
}
