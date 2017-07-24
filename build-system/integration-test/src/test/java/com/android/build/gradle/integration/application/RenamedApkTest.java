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

package com.android.build.gradle.integration.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import groovy.transform.CompileStatic;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for renamedApk. */
@CompileStatic
public class RenamedApkTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("renamedApk").create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkModelReflectsRenamedApk() throws Exception {
        File projectDir = project.getTestDir();

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        File buildDir = new File(projectDir, "build/outputs/apk/debug");

        for (Variant variant : variants) {
            if (!variant.getName().equals("debug")) {
                continue;
            }
            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);

            AndroidArtifactOutput output = mainInfo.getOutputs().iterator().next();

            assertEquals(
                    "Output file for " + variant.getName(),
                    new File(buildDir, variant.getName() + ".apk"),
                    output.getMainOutputFile().getOutputFile());
        }
    }

    @Test
    public void checkRenamedApk() {
        File debugApk = project.file("build/outputs/apk/debug/debug.apk");
        assertTrue("Check output file: " + debugApk, debugApk.isFile());
    }
}
