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

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link FeatureSplitPackageIds} class. */
public class FeatureSplitPackageIdsTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
    }

    @Test(expected = FileNotFoundException.class)
    public void testMissingPesistenceFile() throws IOException {
        FeatureSplitPackageIds loaded = FeatureSplitPackageIds.load(ImmutableSet.of());
    }

    @Test
    public void testPersistence() throws IOException {
        FeatureSplitPackageIds featureSplitPackageIds = new FeatureSplitPackageIds();
        featureSplitPackageIds.addFeatureSplit("one");
        featureSplitPackageIds.addFeatureSplit("two");
        featureSplitPackageIds.addFeatureSplit("three");
        featureSplitPackageIds.save(temporaryFolder.getRoot());
        File[] files = temporaryFolder.getRoot().listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(1);
        File outputFile = files[0];
        assertThat(outputFile.exists()).isTrue();

        // now reload the file.
        FeatureSplitPackageIds loaded = FeatureSplitPackageIds.load(ImmutableSet.of(outputFile));

        assertThat(loaded.getIdFor("one")).isEqualTo(FeatureSplitPackageIds.BASE_ID);
        assertThat(loaded.getIdFor("two")).isEqualTo(FeatureSplitPackageIds.BASE_ID + 1);
        assertThat(loaded.getIdFor("three")).isEqualTo(FeatureSplitPackageIds.BASE_ID + 2);
    }
}
