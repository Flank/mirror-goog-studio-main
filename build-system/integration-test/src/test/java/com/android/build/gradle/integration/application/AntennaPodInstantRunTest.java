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

import static com.android.testutils.truth.MoreTruth.assertThatDex;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.instant.InstantRunTestUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AntennaPodInstantRunTest {

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Rule
    public GradleTestProject mainProject =
            GradleTestProject.builder().fromExternalProject("AntennaPod").create();

    private GradleTestProject project;

    @Before
    public void updateToLatestVersionsAndSetOptions() throws IOException {
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
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion = \".*\"",
                "buildToolsVersion = \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\" // Updated by test");

        List<String> subprojects =
                ImmutableList.of("AudioPlayer/library", "afollestad/commons", "afollestad/core");

        for (String subproject: subprojects) {
            TestFileUtils.searchAndReplace(
                    mainProject.getSubproject(subproject).getBuildFile(),
                    "buildToolsVersion \".*\"",
                    "buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                            + "\" // Updated by test");
        }

        // Update the support lib and fix resulting issue:
        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.getBuildFile(),
                        mainProject.file("afollestad/core/build.gradle"),
                        mainProject.file("afollestad/commons/build.gradle"));

        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchAndReplace(
                    buildFile,
                    "23.1.1",
                    GradleTestProject.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchAndReplace(
                mainProject.file("afollestad/core/src/main/res/values-v11/styles.xml"),
                "abc_ic_ab_back_mtrl_am_alpha",
                "abc_ic_ab_back_material");
    }

    @Test
    public void buildAntennaPod() throws Exception {
        getExecutor().run("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().withoutOfflineFlag().getMulti().getModelMap().get(":app"));

        getExecutor()
                .withInstantRun(23, ColdswapMode.MULTIDEX, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        // Test the incremental build
        makeHotSwapChange(1);
        getExecutor()
                .withInstantRun(23, ColdswapMode.MULTIDEX, OptionalCompilationStep.RESTART_ONLY)
                .run(":app:assembleDebug");

        makeHotSwapChange(100);

        getExecutor().withInstantRun(23, ColdswapMode.MULTIDEX).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file)
                .containsClass("Lde/danoeh/antennapod/activity/MainActivity$override;")
                .that()
                .hasMethod("onStart");

        // Test cold swap
        makeColdSwapChange(100);

        getExecutor().withInstantRun(23, ColdswapMode.MULTIDEX).run(":app:assembleDebug");

        InstantRunTestUtils.getCompiledColdSwapChange(instantRunModel, ColdswapMode.MULTIDEX);
    }

    @NonNull
    private RunGradleTasks getExecutor() {
        return project.executor().withoutOfflineFlag();
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
