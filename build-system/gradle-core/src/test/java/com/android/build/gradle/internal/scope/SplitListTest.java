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
import com.android.build.VariantOutput;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
                filterListFromStrings(ImmutableSet.of("hdpi", "xxhdpi")),
                ImmutableSet.of(),
                ImmutableSet.of(),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\"DENSITY\",\"filters\":"
                                + "[{\"value\":\"hdpi\"},{\"value\":\"xxhdpi\"}]},"
                                + "{\"splitType\":\"LANGUAGE\",\"filters\":[]},"
                                + "{\"splitType\":\"ABI\",\"filters\":[]},"
                                + "{\"splitType\":\"ResConfigs\",\"filters\":[]}]");
    }

    @Test
    public void testLanguagePersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableList.of(
                        new SplitList.Filter("fr,fr_BE", "fr"), new SplitList.Filter("it")),
                ImmutableSet.of(),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\"DENSITY\",\"filters\":[]},"
                                + "{\"splitType\":\"LANGUAGE\",\"filters\":"
                                + "[{\"value\":\"fr,fr_BE\",\"simplifiedName\":\"fr\"},{\"value\":\"it\"}]},"
                                + "{\"splitType\":\"ABI\",\"filters\":[]},"
                                + "{\"splitType\":\"ResConfigs\",\"filters\":[]}]");
    }

    @Test
    public void testAbiPersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                ImmutableSet.of(),
                ImmutableSet.of(),
                filterListFromStrings(ImmutableSet.of("arm", "x86")),
                ImmutableSet.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\"DENSITY\",\"filters\":[]},"
                                + "{\"splitType\":\"LANGUAGE\",\"filters\":[]},"
                                + "{\"splitType\":\"ABI\",\"filters\":"
                                + "[{\"value\":\"arm\"},{\"value\":\"x86\"}]},"
                                + "{\"splitType\":\"ResConfigs\",\"filters\":[]}]");
    }

    @Test
    public void testAllPersistence() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                filterListFromStrings(ImmutableSet.of("xhdpi", "xxhdpi")),
                filterListFromStrings(ImmutableSet.of("de", "it")),
                filterListFromStrings(ImmutableSet.of("arm", "x86")),
                ImmutableList.of());

        assertThat(outputFile)
                .contains(
                        "[{\"splitType\":\"DENSITY\",\"filters\":[{\"value\":\"xhdpi\"},{\"value\":\"xxhdpi\"}]},"
                                + "{\"splitType\":\"LANGUAGE\",\"filters\":[{\"value\":\"de\"},{\"value\":\"it\"}]},"
                                + "{\"splitType\":\"ABI\",\"filters\":[{\"value\":\"arm\"},{\"value\":\"x86\"}]},"
                                + "{\"splitType\":\"ResConfigs\",\"filters\":[]}]");
    }

    @Test
    public void testDensityLoading() throws IOException {
        File outputFile = temporaryFolder.newFile();
        SplitList.save(
                outputFile,
                filterListFromStrings(ImmutableSet.of("hdpi", "xxhdpi")),
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
                ImmutableList.of(
                        new SplitList.Filter("fr,fr_BE,fr_CA", "fr"),
                        new SplitList.Filter("de", "de")),
                ImmutableSet.of(),
                ImmutableSet.of());

        when(fileCollection.getSingleFile()).thenReturn(outputFile);

        SplitList newSplitList = SplitList.load(fileCollection);
        Truth.assertThat(newSplitList.getFilters(OutputFile.FilterType.LANGUAGE))
                .containsExactly("fr,fr_BE,fr_CA", "de");
        Map<String, String> expectedLanguageFilters =
                ImmutableMap.of("fr,fr_BE,fr_CA", "fr", "de", "de");
        Map<String, String> actualLanguageFilters = new HashMap<>();
        newSplitList.forEach(
                (filterType, filters) -> {
                    if (filterType == VariantOutput.FilterType.LANGUAGE) {
                        filters.forEach(
                                filter ->
                                        actualLanguageFilters.put(
                                                filter.getValue(), filter.getDisplayName()));
                    }
                });
        Truth.assertThat(actualLanguageFilters).isEqualTo(expectedLanguageFilters);
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
                filterListFromStrings(ImmutableSet.of("arm", "x86")),
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
                filterListFromStrings(ImmutableSet.of("xhdpi", "xxxhdpi")),
                filterListFromStrings(ImmutableSet.of("es", "it")),
                filterListFromStrings(ImmutableSet.of("arm", "x86")),
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

    private static List<SplitList.Filter> filterListFromStrings(Collection<String> values) {
        return values.stream().map(SplitList.Filter::new).collect(Collectors.toList());
    }
}
