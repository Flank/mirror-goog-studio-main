/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.ndk;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VariantBuildInformation;
import com.google.common.collect.Maps;
import com.google.common.truth.Truth;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for ndkSanAngeles.
 */
public class NdkSanAngelesTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                    .fromTestProject("ndkSanAngeles")
                    .create();

    public static AndroidProject model;

    @BeforeClass
    public static void setUp() throws Exception {
        project.executor().run("clean", "assembleDebug");
        model = project.model().fetchAndroidProjectsAllowSyncIssues().getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void lint() throws Exception {
        project.execute("lint");
    }

    @Test
    public void checkVersionCodeInModel() {
        VariantBuildInformation debugOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(model);

        // get the outputs.
        BuiltArtifactsImpl builtArtifacts = ProjectBuildOutputUtils.getBuiltArtifacts(debugOutput);
        assertEquals(3, builtArtifacts.getElements().size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(2);
        expected.put("armeabi-v7a", 1000123);
        expected.put("x86", 2000123);
        expected.put(null, 3000123);

        for (BuiltArtifact builtArtifact : builtArtifacts.getElements()) {
            if (builtArtifact.getFilters().isEmpty()) {
                // universal apk
                Integer value = expected.get(null);
                // this checks we're not getting an unexpected builtArtifact.
                assertNotNull("Check Valid builtArtifact: " + null, value);

                Truth.assertThat(builtArtifact.getVersionCode()).isEqualTo(value);
                expected.remove(null);
            }
            for (FilterConfiguration filter : builtArtifact.getFilters()) {
                if (filter.getFilterType().equals(FilterConfiguration.FilterType.ABI)) {
                    String abiFilter = filter.getIdentifier();
                    Integer value = expected.get(abiFilter);
                    // this checks we're not getting an unexpected builtArtifact.
                    assertNotNull("Check Valid builtArtifact: " + abiFilter, value);

                    Truth.assertThat(builtArtifact.getVersionCode()).isEqualTo(value);
                    expected.remove(abiFilter);
                }
            }
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
