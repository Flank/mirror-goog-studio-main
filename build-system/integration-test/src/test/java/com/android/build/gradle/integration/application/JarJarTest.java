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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for the jarjar integration.
 */
@RunWith(FilterableParameterized.class)
public class JarJarTest {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<String> projects() {
        return ImmutableList.of("jarjarIntegration");
    }

    @Rule public GradleTestProject project;

    public JarJarTest(@NonNull String projectName) {
        project = GradleTestProject.builder().fromTestProject(projectName).create();
    }

    @Test
    public void checkRepackagedGsonLibraryFormonodex() throws Exception {
        project.executeAndReturnModel("clean", "assembleDebug");
        verifyApk();
    }

    @Test
    public void checkRepackagedForNativeMultidex() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "\n"
                        + "android.defaultConfig {\n"
                        + "    minSdkVersion 21\n"
                        + "    multiDexEnabled true\n"
                        + "}\n");

        project.executeAndReturnModel("clean", "assembleDebug");
        verifyApk();
    }

    @Test
    public void checkRepackagedForLegacyMultidex() throws Exception {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "\n"
                        + "android.defaultConfig {\n"
                        + "    minSdkVersion 19\n"
                        + "    multiDexEnabled true\n"
                        + "}\n");

        project.executeAndReturnModel("clean", "assembleDebug");
        verifyApk();
    }

    private void verifyApk() throws Exception {
        // make sure the Gson library has been renamed and the original one is not present.
        Apk outputFile = project.getApk("debug");
        TruthHelper.assertThatApk(outputFile)
                .containsClass("Lcom/google/repacked/gson/Gson;");
        TruthHelper.assertThatApk(outputFile)
                .doesNotContainClass("Lcom/google/gson/Gson;");
    }
}
