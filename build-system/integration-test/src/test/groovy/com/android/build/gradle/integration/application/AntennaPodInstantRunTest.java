/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatDex;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.utils.FileUtils;
import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class AntennaPodInstantRunTest {

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();

    private GradleTestProject project;

    @Before
    public void updateToLatestGradleAndSetOptions() throws IOException {
        project = mainProject.getSubproject("AntennaPod");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath \"com.android.tools.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                "maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                        + "'} \n"
                        + "        jcenter()");
    }

    @Test
    public void buildAntennaPod() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(project.model().getMulti().get(":app"));

        project.executor()
                .withInstantRun(23, ColdswapMode.MULTIDEX, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        // Test the incremental build
        makeHotSwapChange(1);
        project.executor()
                .withInstantRun(23, ColdswapMode.MULTIDEX, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        makeHotSwapChange(100);

        project.executor().withInstantRun(23, ColdswapMode.MULTIDEX).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file)
                .containsClass("Lde/danoeh/antennapod/activity/MainActivity$override;")
                .that()
                .hasMethod("onStart");

        // Test cold swap
        makeColdSwapChange(100);

        project.executor().withInstantRun(23, ColdswapMode.MULTIDEX).run(":app:assembleDebug");

        InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel, ColdswapMode.MULTIDEX);
    }

    private void makeHotSwapChange(int i) throws IOException {
        TestFileUtils.searchAndReplace(
                project.file("app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"),
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n" + "        Log.d(TAG, \"onStart called " + i + "\");");
    }

    private void makeColdSwapChange(int i) throws IOException {
        String newMethodName = "newMethod" + i;
        File mainActivity =
                project.file("app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java");
        TestFileUtils.searchAndReplace(
                mainActivity,
                "public void onStart\\(\\) \\{",
                "public void onStart() {\n"
                        + "        " + newMethodName + "();");
        TestFileUtils.addMethod(
                mainActivity,
                "private void " + newMethodName + "() {\n"
                        + "        Log.d(TAG, \"" + newMethodName + " called\");\n"
                        + "    }\n");
    }
}
