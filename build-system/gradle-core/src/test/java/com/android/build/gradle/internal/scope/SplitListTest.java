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

package com.android.build.gradle.internal.scope;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link SplitList} class.
 */
public class SplitListTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock ConfigurableFileCollection fileCollection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDensityPersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of("hdpi", "xxhdpi"),
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\""
                                + "DENSITY\",\"values\":[\"hdpi\",\"xxhdpi\"]},{\"splitType\":\""
                                + "LANGUAGE\",\"values\":[]},{\"splitType\":\""
                                + "ABI\",\"values\":[]},{\"splitType\":\"ResConfigs\",\"values\":[]}]");
    }

    @Test
    public void testLanguagePersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableSet.of("fr", "fr_BE"),
                ImmutableSet.of(),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\""
                                + "DENSITY\",\"values\":[]},{\"splitType\":\""
                                + "LANGUAGE\",\"values\":[\"fr\",\"fr_BE\"]},{\"splitType\":\""
                                + "ABI\",\"values\":[]},{\"splitType\":\"ResConfigs\",\"values\":[]}]");
    }

    @Test
    public void testAbiPersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of("arm", "x86"),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\""
                                + "DENSITY\",\"values\":[]},{\"splitType\":\""
                                + "LANGUAGE\",\"values\":[]},{\"splitType\":\""
                                + "ABI\",\"values\":[\"arm\",\"x86\"]},{\"splitType\":\"ResConfigs\",\"values\":[]}]");
    }

    @Test
    public void testAllPersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of("xhdpi", "xxhdpi"),
                ImmutableSet.of("de", "it"),
                ImmutableSet.of("arm", "x86"),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\""
                                + "DENSITY\",\"values\":[\"xhdpi\",\"xxhdpi\"]},{\"splitType\":\""
                                + "LANGUAGE\",\"values\":[\"de\",\"it\"]},{\"splitType\":\""
                                + "ABI\",\"values\":[\"arm\",\"x86\"]},{\"splitType\":\"ResConfigs\",\"values\":[]}]");
    }

    @Test
    public void testDensityLoading() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of("hdpi", "xxhdpi"),
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of());

        when(fileCollection.getSingleFile()).thenReturn(outputFile);

        SplitList newSplitList = SplitList.load(fileCollection);
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.DENSITY))
                .containsExactly("hdpi", "xxhdpi");
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.LANGUAGE)).isEmpty();
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.ABI)).isEmpty();
    }

    @Test
    public void testLanguageLoading() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableSet.of("fr", "fr_CA"),
                ImmutableSet.of(),
                ImmutableSet.of());

        when(fileCollection.getSingleFile()).thenReturn(outputFile);

        SplitList newSplitList = SplitList.load(fileCollection);
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.LANGUAGE))
                .containsExactly("fr", "fr_CA");
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.DENSITY)).isEmpty();
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.ABI)).isEmpty();

    }

    @Test
    public void testAbiLoading() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of("arm", "x86"),
                ImmutableSet.of());

        when(fileCollection.getSingleFile()).thenReturn(outputFile);

        SplitList newSplitList = SplitList.load(fileCollection);
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.ABI))
                .containsExactly("arm", "x86");
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.DENSITY)).isEmpty();
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.LANGUAGE)).isEmpty();
    }

    @Test
    public void testAllLoading() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of("xhdpi", "xxxhdpi"),
                ImmutableSet.of("es", "it"),
                ImmutableSet.of("arm", "x86"),
                ImmutableSet.of());

        when(fileCollection.getSingleFile()).thenReturn(outputFile);

        SplitList newSplitList = SplitList.load(fileCollection);
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.DENSITY))
                .containsExactly("xhdpi", "xxxhdpi");
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.LANGUAGE))
                .containsExactly("es", "it");
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.ABI))
                .containsExactly("arm", "x86");
        // check we only load the file once.
        Mockito.verify(fileCollection, times(1)).getSingleFile();
    }
}
