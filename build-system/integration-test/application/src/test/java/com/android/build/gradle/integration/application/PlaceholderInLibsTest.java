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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.VariantBuildInformation;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test for unresolved placeholders in libraries. */
public class PlaceholderInLibsTest {
    private static Map<String, AndroidProject> models;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("placeholderInLibsTest").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        project.execute(
                "clean", ":examplelibrary:generateDebugAndroidTestSources", "app:assembleDebug");
        models = project.model().fetchAndroidProjects().getOnlyModelMap();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        models = null;
    }

    @Test
    public void testLibraryPlaceholderSubstitutionInFinalApk() throws Exception {

        // Load the custom model for the project
        AndroidProject projectModel = models.get(":app");
        Collection<VariantBuildInformation> variantBuildOutputs =
                projectModel.getVariantsBuildInformation();
        assertThat(variantBuildOutputs).named("Variant Count").hasSize(2);

        // get the main artifact of the debug artifact
        VariantBuildInformation debugOutput =
                ProjectBuildOutputUtils.getVariantBuildInformation(projectModel, "flavorDebug");

        // get the outputs.
        Collection<String> debugOutputs = ProjectBuildOutputUtils.getOutputFiles(debugOutput);
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        String output = debugOutputs.iterator().next();

        List<String> apkBadging = ApkSubject.getBadging(new File(output));

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'")) {
                return;
            }
        }
        Assert.fail("failed to find the permission with the right placeholder substitution.");
    }
}
