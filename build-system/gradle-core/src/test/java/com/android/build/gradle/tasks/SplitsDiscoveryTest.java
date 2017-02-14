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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;

import com.android.build.gradle.internal.scope.SplitList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link SplitsDiscovery} task.
 */
public class SplitsDiscoveryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;
    SplitsDiscovery task;

    @Mock
    FileCollection mergedFolders;

    @Mock
    SplitList splitList;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);
        File testDir = temporaryFolder.newFolder();
        File outputFile = temporaryFolder.newFile();
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", SplitsDiscovery.class);
        task.mergedResourcesFolders = mergedFolders;
        task.persistedList = outputFile;
        task.splitList = splitList;
        task.resourceConfigs = ImmutableSet.of();
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
        mergedFolders = null;
    }

    @Test
    public void testDensityFilters() throws IOException {
        task.densityAuto = false;
        task.densityFilters = ImmutableSet.of("hdpi", "xhdpi");

        task.taskAction();
        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of("hdpi", "xhdpi")),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testLanguageFilters() throws IOException {
        task.languageAuto = false;
        task.languageFilters = ImmutableSet.of("en", "fr", "de");

        task.taskAction();
        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of("en", "fr", "de")),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testAbiFilters() throws IOException {
        task.abiFilters = ImmutableSet.of("x86", "arm", "arm-v4");

        task.taskAction();
        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of("x86", "arm", "arm-v4")),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testAllFilters() throws IOException {
        task.densityAuto = false;
        task.densityFilters = ImmutableSet.of("hdpi", "xhdpi");
        task.languageAuto = false;
        task.languageFilters = ImmutableSet.of("en", "fr", "de");
        task.abiFilters = ImmutableSet.of("x86", "arm", "arm-v4");

        task.taskAction();
        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of("hdpi", "xhdpi")),
                        eq(ImmutableSet.of("en", "fr", "de")),
                        eq(ImmutableSet.of("x86", "arm", "arm-v4")),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testAutoDensityFilters() throws IOException {
        File mergedFolder = temporaryFolder.newFolder();
        assertThat(new File(mergedFolder, "drawable-xhdpi").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "drawable-hdpi").mkdirs()).isTrue();
        // wrong name, should not be picked up
        assertThat(new File(mergedFolder, "xxhdpi").mkdirs()).isTrue();

        task.mergedResourcesFolders = project.files(mergedFolder).builtBy(task);

        task.densityAuto = true;
        task.taskAction();

        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of("hdpi", "xhdpi")),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testAutoLanguageFilters() throws IOException {
        File mergedFolder = temporaryFolder.newFolder();
        assertThat(new File(mergedFolder, "values-fr").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "values-de").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "values-fr_be").mkdirs()).isTrue();
        // wrong name, should not be picked up
        assertThat(new File(mergedFolder, "en").mkdirs()).isTrue();

        task.mergedResourcesFolders = project.files(mergedFolder).builtBy(task);

        task.languageAuto = true;
        task.taskAction();

        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of("fr", "de", "fr_be")),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testAllAutoFilters() throws IOException {
        File mergedFolder = temporaryFolder.newFolder();
        assertThat(new File(mergedFolder, "drawable-xhdpi").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "drawable-hdpi").mkdirs()).isTrue();
        // wrong name, should not be picked up
        assertThat(new File(mergedFolder, "xxhdpi").mkdirs()).isTrue();

        assertThat(new File(mergedFolder, "values-fr").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "values-de").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "values-fr_be").mkdirs()).isTrue();
        // wrong name, should not be picked up
        assertThat(new File(mergedFolder, "en").mkdirs()).isTrue();
        task.mergedResourcesFolders = project.files(mergedFolder).builtBy(task);

        task.densityAuto = true;
        task.languageAuto = true;
        task.taskAction();

        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of("hdpi", "xhdpi")),
                        eq(ImmutableSet.of("fr", "de", "fr_be")),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }

    @Test
    public void testNoAutoForAbiFilters() throws IOException {
        File mergedFolder = temporaryFolder.newFolder();
        assertThat(new File(mergedFolder, "arm").mkdirs()).isTrue();
        assertThat(new File(mergedFolder, "x86").mkdirs()).isTrue();
        task.mergedResourcesFolders = project.files(mergedFolder).builtBy(task);

        task.taskAction();

        Mockito.verify(splitList)
                .save(
                        eq(task.persistedList),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()),
                        eq(ImmutableSet.of()));
    }
}
