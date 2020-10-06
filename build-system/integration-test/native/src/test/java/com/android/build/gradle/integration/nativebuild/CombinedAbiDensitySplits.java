/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.testutils.truth.ZipFileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VariantBuildInformation;
import com.android.testutils.apk.Zip;
import com.android.testutils.truth.ZipFileSubject;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test drive for the CombinedAbiDensityPureSplits samples test. */
public class CombinedAbiDensitySplits {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedAbiDensitySplits")
                    .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                    .create();

    @Before
    public void setup() {
    }

    @Test
    public void testCombinedDensityAndAbiPureSplits() throws Exception {
        project.executor().run("clean", "assembleDebug");
        AndroidProject projectBuildOutput =
                project.model().fetchAndroidProjectsAllowSyncIssues().getOnlyModel();
        VariantBuildInformation debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);

        // get the outputs.
        Collection<String> debugOutputs = ProjectBuildOutputUtils.getOutputFiles(debugBuildOutput);
        assertNotNull(debugOutputs);

        // build a set of expectedDensities outputs
        Set<String> expectedDensities = Sets.newHashSetWithExpectedSize(5);
        expectedDensities.add("mdpi");
        expectedDensities.add("hdpi");
        expectedDensities.add("xhdpi");
        expectedDensities.add("xxhdpi");

        BuiltArtifactsImpl builtArtifacts =
                ProjectBuildOutputUtils.getBuiltArtifacts(debugBuildOutput);
        assertEquals(10, builtArtifacts.getElements().size());
        for (BuiltArtifact builtArtifact : builtArtifacts.getElements()) {
            String densityFilter =
                    VariantOutputUtils.getFilter(
                            builtArtifact, FilterConfiguration.FilterType.DENSITY);
            if (densityFilter == null) {
                assertThat(
                                VariantOutputUtils.getFilter(
                                        builtArtifact, FilterConfiguration.FilterType.ABI))
                        .isNotNull();
            }

            assertEquals(
                    VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                    builtArtifact.getOutputType());

            assertThat(builtArtifact.getVersionCode()).isEqualTo(123);
            try (Zip it = new Zip(new File(builtArtifact.getOutputFile()))) {
                ZipFileSubject.assertThat(it).entries("/lib/.*").hasSize(1);
            }

            if (densityFilter != null) {
                expectedDensities.remove(densityFilter);

                // ensure the .so file presence (and only one)
                try (Zip it = new Zip(new File(builtArtifact.getOutputFile()))) {
                    assertThat(it)
                            .contains(
                                    "lib/"
                                            + VariantOutputUtils.getFilter(
                                                    builtArtifact,
                                                    FilterConfiguration.FilterType.ABI)
                                            + "/libhello-jni.so");
                }
            }
        }

        // this checks we didn't miss any expectedDensities output.
        assertTrue(expectedDensities.isEmpty());
    }
}
