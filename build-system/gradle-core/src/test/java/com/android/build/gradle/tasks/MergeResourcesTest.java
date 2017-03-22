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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.builder.core.BuilderConstants;
import com.android.ide.common.res2.ResourceSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeResourcesTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public Project project;
    public MergeResources task;
    private List<ResourceSet> folderSets;

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", MergeResources.class);

        // set some default file collection on the required inputs
        FileCollection empty = mock(FileCollection.class);
        task.setRenderscriptResOutputDir(empty);
        task.setGeneratedResOutputDir(empty);

        folderSets = Lists.newArrayList();
        task.setSourceFolderInputs(InputSupplier.from(() -> folderSets));
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
        folderSets = null;
    }

    @Test
    public void singleSetWithSingleFile() throws Exception {
        List<ResourceSet> folderSets = Lists.newArrayList();
        task.setSourceFolderInputs(InputSupplier.from(() -> folderSets));

        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
    }

    @Test
    public void singleSetWithMultiFiles() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file, file2);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
    }

    @Test
    public void twoSetsWithSingleFile() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = new File("src/debug");
        ResourceSet debugSet = createResourceSet(folderSets, "debug", file2);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet, debugSet);
    }

    @Test
    public void singleSetWithDependency() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        List<ResourceSet> dependencySets = Lists.newArrayList();
        task.setDependencySetSupplier(() -> dependencySets);

        File file2 = new File("foo/bar/1.0");
        ResourceSet librarySet = createResourceSet(dependencySets, "foo:bar:1.0", file2);

        Set<File> dependencyFiles = Sets.newHashSet(file2);
        task.setDependencyFileSupplier(() -> dependencyFiles);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.getDependencyInputs()).containsExactly(file2);

        List<ResourceSet> computedSets = task.computeResourceSetList();
        assertThat(computedSets).containsExactly(librarySet, mainSet).inOrder();
    }

    @Test
    public void singleSetWithRenderscript() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File rsFile = new File("rs");
        setFileCollection(task::setRenderscriptResOutputDir, rsFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
        // rs file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, rsFile);
    }

    @Test
    public void singleSetWithGeneratedRes() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File genFile = new File("generated");
        setFileCollection(task::setGeneratedResOutputDir, genFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
        // generated file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, genFile);
    }

    @Test
    public void singleSetWithMicroApkRes() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File microFile = new File("micro");
        setFileCollection(task::setMicroApkResDirectory, microFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
        // micro file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, microFile);
    }

    @Test
    public void singleSetWithExtraRes() throws Exception {
        File file = new File("src/main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File extraFile = new File("extra");
        setFileCollectionSupplier(task::setExtraGeneratedResFolders, extraFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeResourceSetList()).containsExactly(mainSet);
        // rs file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, extraFile);
    }

    @Test
    public void everything() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file, file2);

        File debugFile = new File("src/debug");
        ResourceSet debugSet = createResourceSet(folderSets, "debug", debugFile);

        List<ResourceSet> dependencySets = Lists.newArrayList();
        task.setDependencySetSupplier(() -> dependencySets);

        File libFile = new File("foo/bar/1.0");
        ResourceSet librarySet = createResourceSet(dependencySets, "foo:bar:1.0", libFile);

        File libFile2 = new File("foo/bar/2.0");
        ResourceSet librarySet2 = createResourceSet(dependencySets, "foo:bar:2.0", libFile2);

        Set<File> dependencyFiles = Sets.newHashSet(libFile, libFile2);
        task.setDependencyFileSupplier(() -> dependencyFiles);

        File rsFile = new File("rs");
        setFileCollection(task::setRenderscriptResOutputDir, rsFile);

        File genFile = new File("generated");
        setFileCollection(task::setGeneratedResOutputDir, genFile);

        File microFile = new File("micro");
        setFileCollection(task::setMicroApkResDirectory, microFile);

        File extraFile = new File("extra");
        setFileCollectionSupplier(task::setExtraGeneratedResFolders, extraFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2, debugFile);
        assertThat(task.getDependencyInputs()).containsExactly(libFile, libFile2);
        assertThat(task.computeResourceSetList())
                .containsExactly(librarySet, librarySet2, mainSet, debugSet)
                .inOrder();
        // generated files should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles())
                .containsExactly(file, file2, rsFile, genFile, microFile, extraFile);
    }

    @NonNull
    private static ResourceSet createResourceSet(
            List<ResourceSet> folderSets, String name, File... files) {
        ResourceSet mainSet = new ResourceSet(name, null, null, false);
        mainSet.addSources(Arrays.asList(files));
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
            Consumer<Supplier<FileCollection>> setter,
            File... files) {
        FileCollection fileCollection = mock(FileCollection.class);
        Set<File> fileSet = ImmutableSet.copyOf(Arrays.asList(files));
        when(fileCollection.getFiles()).thenReturn(fileSet);
        setter.accept(() -> fileCollection);
    }

}
