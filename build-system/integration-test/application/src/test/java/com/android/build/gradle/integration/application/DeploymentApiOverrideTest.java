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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TaskStateList;
import com.android.build.gradle.integration.common.utils.AssumeBuildToolsUtil;
import com.android.build.gradle.options.IntegerOption;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kotlin.Unit;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to ensure that a build targeted to < 21 will still use native multidex when invoked
 * by the IDE with a build API > 21.
 */
public class DeploymentApiOverrideTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiDex")
            .create();

    @BeforeClass
    public static void checkBuildTools() {
        AssumeBuildToolsUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testMultiDexOnPre21Build() throws Exception {
        GradleBuildResult lastBuild = project.executor().run("clean", "assembleIcsDebug");
        TaskStateList.TaskInfo multidexTask =
                Objects.requireNonNull(lastBuild.findTask(":multiDexListIcsDebug"));
        assertThat(multidexTask).didWork();
    }

    @Test
    public void testMultiDexOnPost21Build() throws Exception {
        GradleBuildResult lastBuild =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        .run("clean", "assembleIcsDebug");
        TaskStateList.TaskInfo multidexTask = lastBuild.findTask(":multiDexListIcsDebug");
        assertThat(multidexTask).isNull();
    }

    @Test
    public void testMultiDexOnReleaseBuild() throws Exception {
        GradleBuildResult lastBuild =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        .run("clean", "assembleIcsRelease");
        TaskStateList.TaskInfo multidexTask =
                Objects.requireNonNull(lastBuild.findTask(":multiDexListIcsRelease"));
        assertThat(multidexTask).didWork();
    }

    /** Regression test for https://issuetracker.google.com/72085541. */
    @Test
    public void testSwitchingDevices() throws Exception {
        List<String> userClasses = new ArrayList<>();

        Map<String, BuiltArtifacts> outputModels =
                executeWithDeviceApiVersionAndReturnOutputModels(project, 19, "assembleIcsDebug");
        try (Apk apk = getApkforVariant(outputModels, "icsDebug")) {
            assertThat(apk).exists();
            for (Dex dex : apk.getAllDexes()) {
                ImmutableSet<String> classNames = dex.getClasses().keySet();
                for (String className : classNames) {
                    if (className.startsWith("Lcom/android/tests/basic")) {
                        userClasses.add(className);
                    }
                }
            }
        }

        outputModels =
                executeWithDeviceApiVersionAndReturnOutputModels(project, 27, "assembleIcsDebug");
        try (Apk apk = getApkforVariant(outputModels, "icsDebug")) {
            assertThat(apk).exists();
            // make sure all user classes are still there
            for (String userClass : userClasses) {
                assertThat(apk).containsClass(userClass);
            }
        }
    }

    @Test
    public void testDexingUsesDeviceApi() throws Exception {
        Map<String, BuiltArtifacts> outputModels =
                project.executeAndReturnOutputModels("assembleIcsDebug");
        assertThat(getApkforVariant(outputModels, "icsDebug")).hasDexVersion(35);

        outputModels =
                executeWithDeviceApiVersionAndReturnOutputModels(project, 24, "assembleIcsDebug");
        assertThat(getApkforVariant(outputModels, "icsDebug")).hasDexVersion(37);
    }

    private static Map<String, BuiltArtifacts> executeWithDeviceApiVersionAndReturnOutputModels(
            GradleTestProject project, int deviceApiVersion, String... tasks) {
        return project.executeAndReturnOutputModels(
                (BaseGradleExecutor<?> bge) -> {
                    bge.with(IntegerOption.IDE_TARGET_DEVICE_API, deviceApiVersion);
                    return Unit.INSTANCE;
                },
                tasks);
    }

    private static Apk getApkforVariant(
            Map<String, BuiltArtifacts> outputModels, String variantName) throws IOException {
        String apkFileName =
                Iterables.getOnlyElement(outputModels.get(variantName).getElements())
                        .getOutputFile();
        return new Apk(new File(apkFileName));
    }
}
