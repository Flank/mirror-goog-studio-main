/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import groovy.transform.CompileStatic;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test for unresolved placeholders in libraries. */
@CompileStatic
public class PlaceholderInLibsTest {
    private static ModelContainer<AndroidProject> models;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("placeholderInLibsTest").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        models =
                project.executeAndReturnMultiModel(
                        "clean",
                        ":examplelibrary:generateDebugAndroidTestSources",
                        "app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void testLibraryPlaceholderSubstitutionInFinalApk() throws Exception {

        // Load the custom model for the project
        Collection<Variant> variants = models.getModelMap().get(":app").getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, "flavorDebug");
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        assertEquals(1, output.getOutputs().size());

        List<String> apkBadging =
                ApkHelper.getApkBadging(output.getOutputs().iterator().next().getOutputFile());

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'")) {
                return;
            }
        }
        Assert.fail("failed to find the permission with the right placeholder substitution.");
    }
}
