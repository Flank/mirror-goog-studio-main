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
import com.android.ide.common.res2.AssetSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeSourceSetFoldersTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    public Project project;
    public MergeSourceSetFolders task;
    private List<AssetSet> folderSets;

    @Before
    public void setUp() throws IOException {
        File testDir = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", MergeSourceSetFolders.class);

        folderSets = Lists.newArrayList();
        task.setAssetSetSupplier(InputSupplier.from(() -> folderSets));
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void singleSetWithSingleFile() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
    }

    @Test
    public void singleSetWithMultiFiles() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2);
        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
    }

    @Test
    public void twoSetsWithSingleFile() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = new File("src/debug");
        AssetSet debugSet = createAssetSet(folderSets, "debug", file2);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2);
        assertThat(task.computeAssetSetList()).containsExactly(mainSet, debugSet);
    }

    @Test
    public void singleSetWithDependency() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        List<AssetSet> dependencySets = Lists.newArrayList();
        task.setDependencySetSupplier(InputSupplier.from(() -> dependencySets));

        File file2 = new File("foo/bar/1.0");
        AssetSet librarySet = createAssetSet(dependencySets, "foo:bar:1.0", file2);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.getDependencyInputs()).containsExactly(file2);
        assertThat(task.computeAssetSetList()).containsExactly(librarySet, mainSet).inOrder();
    }

    @Test
    public void singleSetWithRenderscript() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File shaderFile = new File("shader");
        setFileCollection(task::setShadersOutputDir, shaderFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
        // shader file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, shaderFile);
    }

    @Test
    public void singleSetWithGeneratedRes() throws Exception {
        File file = new File("src/main");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file);

        File copyApkFile = new File("copyApk");
        setFileCollection(task::setCopyApk, copyApkFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file);
        assertThat(task.computeAssetSetList()).containsExactly(mainSet);
        // copyApk file should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles()).containsExactly(file, copyApkFile);
    }

    @Test
    public void everything() throws Exception {
        File file = new File("src/main");
        File file2 = new File("src/main2");
        AssetSet mainSet = createAssetSet(folderSets, BuilderConstants.MAIN, file, file2);

        File debugFile = new File("src/debug");
        AssetSet debugSet = createAssetSet(folderSets, "debug", debugFile);

        List<AssetSet> dependencySets = Lists.newArrayList();
        task.setDependencySetSupplier(InputSupplier.from(() -> dependencySets));

        File libFile = new File("foo/bar/1.0");
        AssetSet librarySet = createAssetSet(dependencySets, "foo:bar:1.0", libFile);

        File libFile2 = new File("foo/bar/2.0");
        AssetSet librarySet2 = createAssetSet(dependencySets, "foo:bar:2.0", libFile2);

        File shaderFile = new File("shader");
        setFileCollection(task::setShadersOutputDir, shaderFile);

        File copyApkFile = new File("copyApk");
        setFileCollection(task::setCopyApk, copyApkFile);

        assertThat(task.getSourceFolderInputs()).containsExactly(file, file2, debugFile);
        assertThat(task.getDependencyInputs()).containsExactly(libFile, libFile2);
        assertThat(task.computeAssetSetList())
                .containsExactly(librarySet, librarySet2, mainSet, debugSet)
                .inOrder();
        // generated files should have been added to the main resource sets.
        assertThat(mainSet.getSourceFiles())
                .containsExactly(file, file2, shaderFile, copyApkFile);
    }

    @NonNull
    private static AssetSet createAssetSet(
            List<AssetSet> folderSets, String name, File... files) {
        AssetSet mainSet = new AssetSet(name);
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
}