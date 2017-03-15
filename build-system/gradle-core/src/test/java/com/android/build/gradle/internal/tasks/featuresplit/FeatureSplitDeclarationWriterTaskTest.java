/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/** Tests for the {@link FeatureSplitDeclarationWriterTask} */
public class FeatureSplitDeclarationWriterTaskTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;
    FeatureSplitDeclarationWriterTask task;
    File outputDirectory;

    @Before
    public void setUp() throws IOException {

        File testDir = temporaryFolder.newFolder();
        outputDirectory = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", FeatureSplitDeclarationWriterTask.class);
        task.outputDirectory = outputDirectory;
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void testDensityFilters() throws IOException {
        task.uniqueIdentifier = "unique_split";
        task.fullTaskAction();
        File[] files = outputDirectory.listFiles();
        assertThat(files).hasLength(1);

        FileCollection fileCollection = Mockito.mock(FileCollection.class);
        FileTree fileTree = Mockito.mock(FileTree.class);
        when(fileCollection.getAsFileTree()).thenReturn(fileTree);
        when(fileTree.getFiles()).thenReturn(ImmutableSet.of(files[0]));

        FeatureSplitDeclaration loadedDecl = FeatureSplitDeclaration.load(fileCollection);
        assertThat("unique_split").isEqualTo(loadedDecl.getUniqueIdentifier());
    }
}
