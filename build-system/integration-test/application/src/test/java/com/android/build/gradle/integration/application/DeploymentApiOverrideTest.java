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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeBuildToolsUtil;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
        project.executor().run("clean", "assembleIcsDebug");
        File mainDexList =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST.INSTANCE,
                                project.getBuildDir()),
                        "icsDebug/mainDexList.txt");
        assertThat(mainDexList.exists()).isTrue();
    }

    @Test
    public void testMultiDexOnPost21Build() throws Exception {
        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                .run("clean", "assembleIcsDebug");
        File mainDexList =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST.INSTANCE,
                                project.getBuildDir()),
                        "icsDebug/mainDexList.txt");
        assertThat(mainDexList.exists()).isFalse();
    }

    @Test
    public void testMultiDexOnReleaseBuild() throws Exception {
        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                // http://b/162074215
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .run("clean", "assembleIcsRelease");
        File mainDexList =
                new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST.INSTANCE,
                                project.getBuildDir()),
                        "icsRelease/mainDexList.txt");
        assertThat(mainDexList.exists()).isTrue();
    }

    /** Regression test for https://issuetracker.google.com/72085541. */
    @Test
    public void testSwitchingDevices() throws Exception {
        project.executor().with(IntegerOption.IDE_TARGET_DEVICE_API, 19).run("assembleIcsDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "ics");
        List<String> userClasses = new ArrayList<>();
        for (Dex dex : apk.getAllDexes()) {
            ImmutableSet<String> classNames = dex.getClasses().keySet();
            for (String className : classNames) {
                if (className.startsWith("Lcom/android/tests/basic")) {
                    userClasses.add(className);
                }
            }
        }

        project.executor().with(IntegerOption.IDE_TARGET_DEVICE_API, 27).run("assembleIcsDebug");

        // make sure all user classes are still there
        apk = project.getApk(GradleTestProject.ApkType.DEBUG, "ics");
        for (String userClass : userClasses) {
            assertThat(apk).containsClass(userClass);
        }
    }

    @Test
    public void testDexingUsesDeviceApi() throws Exception {
        project.executor().run("assembleIcsDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics")).hasDexVersion(35);

        project.executor().with(IntegerOption.IDE_TARGET_DEVICE_API, 24).run("assembleIcsDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics")).hasDexVersion(37);
    }
}
