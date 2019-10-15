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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Sets;
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
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnOutputModel("clean", "assembleDebug");
        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugBuildOutput.getOutputs();
        assertNotNull(debugOutputs);

        // build a set of expectedDensities outputs
        Set<String> expectedDensities = Sets.newHashSetWithExpectedSize(5);
        expectedDensities.add("mdpi");
        expectedDensities.add("hdpi");
        expectedDensities.add("xhdpi");
        expectedDensities.add("xxhdpi");

        assertEquals(10, debugOutputs.size());
        for (OutputFile outputFile : debugOutputs) {
            String densityFilter = VariantOutputUtils.getFilter(outputFile, VariantOutput.DENSITY);
            if (densityFilter == null) {
                assertThat(VariantOutputUtils.getFilter(outputFile, VariantOutput.ABI)).isNotNull();
            }

            assertEquals(VariantOutput.FULL_SPLIT, outputFile.getOutputType());

            assertEquals(123, outputFile.getVersionCode());
            assertThatZip(outputFile.getOutputFile()).entries("/lib/.*").hasSize(1);
            if (densityFilter != null) {
                expectedDensities.remove(densityFilter);

                // ensure the .so file presence (and only one)
                assertThatZip(outputFile.getOutputFile())
                        .contains(
                                "lib/"
                                        + VariantOutputUtils.getFilter(
                                                outputFile, VariantOutput.ABI)
                                        + "/libhello-jni.so");
            }
        }

        // this checks we didn't miss any expectedDensities output.
        assertTrue(expectedDensities.isEmpty());
    }
}
