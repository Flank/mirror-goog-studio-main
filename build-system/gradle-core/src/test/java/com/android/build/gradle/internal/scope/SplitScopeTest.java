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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.ide.common.build.ApkData;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link SplitScope} class */
public class SplitScopeTest {

    @Mock private GradleVariantConfiguration variantConfiguration;
    @Mock private GlobalScope globalScope;
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPersistence() throws IOException {
        SplitScope splitScope = new SplitScope(MultiOutputPolicy.MULTI_APK);
        SplitFactory splitFactory = new SplitFactory("project", variantConfiguration, splitScope);

        splitFactory.addUniversalApk();
        ApkData densityApkData =
                splitFactory.addFullSplit(
                        ImmutableList.of(Pair.of(OutputFile.FilterType.DENSITY, "xxhdpi")));

        // simulate output
        File outputForSplit = temporaryFolder.newFile();
        splitScope.addOutputForSplit(
                VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST,
                densityApkData,
                outputForSplit);

        String persistedState =
                splitScope.persist(
                        ImmutableList.of(VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST));

        // load the persisted state.
        StringReader reader = new StringReader(persistedState);
        Collection<BuildOutput> buildOutputs =
                BuildOutputs.load(
                        ImmutableList.of(VariantScope.TaskOutputType.COMPATIBLE_SCREEN_MANIFEST),
                        reader);

        // check that persisted was loaded correctly.
        assertThat(buildOutputs).hasSize(1);
        BuildOutput buildOutput = Iterators.getOnlyElement(buildOutputs.iterator());
        assertThat(buildOutput.getOutputFile()).isEqualTo(outputForSplit);
        assertThat(buildOutput.getApkInfo().getFilters()).hasSize(1);
        FilterData filter =
                Iterators.getOnlyElement(buildOutput.getApkInfo().getFilters().iterator());
        assertThat(filter.getIdentifier()).isEqualTo("xxhdpi");
        assertThat(filter.getFilterType()).isEqualTo(OutputFile.FilterType.DENSITY.name());
    }
}
