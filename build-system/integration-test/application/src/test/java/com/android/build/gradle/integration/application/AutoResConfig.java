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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static org.junit.Assert.assertEquals;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.apk.Apk;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to ensure that "auto" resConfig setting only package application's languages.
 */
public class AutoResConfig {

    private ProjectBuildOutput model;

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
                        + "    compile 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");
        model = project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
    }

    @Test
    public void testAutoResConfigsOnlyPackageApplicationSpecificLanguage() throws Exception {
        VariantBuildOutput debugBuildOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(model);

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugBuildOutput.getOutputs();

        assertEquals(1, debugOutputs.size());
        OutputFile output = debugOutputs.iterator().next();

        System.out.println(output.getOutputFile().getAbsolutePath());
        Apk apk = new Apk(output.getOutputFile());

        assertThatApk(apk).locales().containsAllOf("en", "fr", "fr-CA", "fr-BE");
    }
}
