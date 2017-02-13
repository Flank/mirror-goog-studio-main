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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.apk.Apk;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to ensure that "auto" resConfig setting only package application's languages.
 */
public class AutoResConfig {

    private AndroidProject model;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedDensityAndLanguagePureSplits")
                    .create();

    @Before
    public void setup() throws Exception {
        AssumeUtil.assumeBuildToolsAtLeast(21);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 12\n"
                        + "        minSdkVersion 21\n"
                        + "        targetSdkVersion 21\n"
                        + "        resConfig \"auto\"\n"
                        + "    }\n"
                        + "    \n"
                        + "    splits {\n"
                        + "        density {\n"
                        + "            enable false\n"
                        + "        }\n"
                        + "        language {\n"
                        + "            enable false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:appcompat-v7:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:" + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
    }

    @Test
    public void testAutoResConfigsOnlyPackageApplicationSpecificLanguage() throws Exception {
        // Load the custom model for the project
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, DEBUG);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        assertEquals(1, output.getOutputs().size());

        System.out.println(output.getMainOutputFile().getOutputFile().getAbsolutePath());
        Apk apk = new Apk(output.getMainOutputFile().getOutputFile());

        assertThatApk(apk).locales().containsAllOf("en", "fr", "fr-CA", "fr-BE");
    }
}
