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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link FeatureSplitPackageIdsWriterTask} class */
public class FeatureSplitPackageIdsWriterTaskTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;
    FeatureSplitPackageIdsWriterTask task;
    File outputDirectory;

    @Mock FileCollection fileCollection;
    @Mock FileTree fileTree;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);
        File testDir = temporaryFolder.newFolder();
        outputDirectory = temporaryFolder.newFolder();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", FeatureSplitPackageIdsWriterTask.class);
        task.outputDirectory = outputDirectory;
        task.input = fileCollection;

        when(fileCollection.getAsFileTree()).thenReturn(fileTree);
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void testTask() throws IOException {
        ImmutableSet.Builder<File> inputDirs = ImmutableSet.builder();
        for (int i = 0; i < 5; i++) {
            inputDirs.add(generateInputDir("id_" + i));
        }
        when(fileTree.getFiles()).thenReturn(inputDirs.build());

        task.fullTaskAction();
        File[] files = outputDirectory.listFiles();
        assertThat(files).isNotNull();
        assertThat(files).hasLength(1);
        assertThat(files[0].exists()).isTrue();

        FeatureSplitPackageIds loaded = FeatureSplitPackageIds.load(files[0]);
        for (int i = 0; i < 5; i++) {
            assertThat(loaded.getIdFor("id_" + i)).isEqualTo(FeatureSplitPackageIds.BASE_ID + i);
        }
    }

    private File generateInputDir(String id) throws IOException {
        File inputDir = temporaryFolder.newFolder();
        FeatureSplitDeclaration featureSplitDeclaration = new FeatureSplitDeclaration(id);
        featureSplitDeclaration.save(inputDir);
        return FeatureSplitDeclaration.getOutputFile(inputDir);
    }
}
