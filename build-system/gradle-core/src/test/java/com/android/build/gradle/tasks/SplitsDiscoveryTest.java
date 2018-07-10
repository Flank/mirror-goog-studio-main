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
import static org.mockito.Mockito.when;

import com.android.build.VariantOutput;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.SplitList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link SplitsDiscovery} task.
 */
public class SplitsDiscoveryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    Project project;
    SplitsDiscovery task;
    File outputFile;

    @Mock BuildableArtifact outputs;

    @Before
    public void setUp() throws IOException {

        MockitoAnnotations.initMocks(this);
        File testDir = temporaryFolder.newFolder();
        outputFile = temporaryFolder.newFile();
        when(outputs.iterator()).thenReturn(ImmutableList.of(outputFile).iterator());
        project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", SplitsDiscovery.class);
        task.persistedList = outputFile;
        task.resourceConfigs = ImmutableSet.of();
    }

    @After
    public void tearDown() {
        project = null;
        task = null;
    }

    @Test
    public void testDensityFilters() throws IOException {
        task.densityFilters = ImmutableSet.of("hdpi", "xhdpi");

        task.taskAction();

        SplitList splitList = SplitList.load(outputs);
        assertThat(splitList.getFilters(VariantOutput.FilterType.DENSITY))
                .containsExactly("hdpi", "xhdpi");
        assertThat(splitList.getFilters(VariantOutput.FilterType.LANGUAGE)).isEmpty();
        assertThat(splitList.getFilters(VariantOutput.FilterType.ABI)).isEmpty();
    }

    @Test
    public void testLanguageFilters() throws IOException {
        task.languageFilters = ImmutableSet.of("en", "fr", "de");

        task.taskAction();
        SplitList splitList = SplitList.load(outputs);
        assertThat(splitList.getFilters(VariantOutput.FilterType.LANGUAGE))
                .containsExactly("en", "fr", "de");
        assertThat(splitList.getFilters(VariantOutput.FilterType.DENSITY)).isEmpty();
        assertThat(splitList.getFilters(VariantOutput.FilterType.ABI)).isEmpty();
    }

    @Test
    public void testAbiFilters() throws IOException {
        task.abiFilters = ImmutableSet.of("x86", "arm", "arm-v4");

        task.taskAction();
        SplitList splitList = SplitList.load(outputs);
        assertThat(splitList.getFilters(VariantOutput.FilterType.ABI))
                .containsExactly("x86", "arm", "arm-v4");
        assertThat(splitList.getFilters(VariantOutput.FilterType.LANGUAGE)).isEmpty();
        assertThat(splitList.getFilters(VariantOutput.FilterType.DENSITY)).isEmpty();
    }

    @Test
    public void testAllFilters() throws IOException {
        task.densityFilters = ImmutableSet.of("hdpi", "xhdpi");
        task.languageFilters = ImmutableSet.of("en", "fr", "de");
        task.abiFilters = ImmutableSet.of("x86", "arm", "arm-v4");

        task.taskAction();
        SplitList splitList = SplitList.load(outputs);
        assertThat(splitList.getFilters(VariantOutput.FilterType.DENSITY))
                .containsExactly("hdpi", "xhdpi");
        assertThat(splitList.getFilters(VariantOutput.FilterType.LANGUAGE))
                .containsExactly("en", "fr", "de");
        assertThat(splitList.getFilters(VariantOutput.FilterType.ABI))
                .containsExactly("x86", "arm", "arm-v4");
    }
}
