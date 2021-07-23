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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TaskStateList;
import com.android.build.gradle.integration.common.utils.AssumeBuildToolsUtil;
import com.android.build.gradle.integration.common.utils.TaskStateAssertionHelper;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to ensure that a build targeted to < 21 will still use native multidex when invoked by the
 * IDE with a build API >= 21.
 */
public class DeploymentApiOverrideTest {

    private static final int DEX_VERSION_FOR_MIN_SDK_14 = 35;
    private static final int DEX_VERSION_FOR_MIN_SDK_19 = 35;
    private static final int DEX_VERSION_FOR_MIN_SDK_21 = 35;
    private static final int DEX_VERSION_FOR_MIN_SDK_24 = 37;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiDex")
            .create();

    @BeforeClass
    public static void checkBuildTools() {
        AssumeBuildToolsUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testOriginalLegacyMultiDexOnDebugBuild() throws Exception {
        // ics flavor has minSdk = 14
        GradleBuildResult result =
                project.executor()
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleIcsDebug");

        assertThat(getMainDexListFile(project, "icsDebug").exists()).isTrue();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_14);
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", false));
    }

    @Test
    public void testOriginalNativeMultiDexOnDebugBuild() throws Exception {
        // lollipop flavor has minSdk = 21
        GradleBuildResult result =
                project.executor()
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleLollipopDebug");

        assertThat(getMainDexListFile(project, "lollipopDebug").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "lollipop"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_21);
        assertDexTask(result, genExpectedTaskStatesFor("LollipopDebug", true));
    }

    @Test
    public void testUpgradeToNativeMultiDexOnDebugBuild() throws Exception {
        // ics flavor has minSdk = 14 and we upgrade to 21 via IDE property
        GradleBuildResult result =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleIcsDebug");

        assertThat(getMainDexListFile(project, "icsDebug").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_21);
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", true));
    }

    @Test
    public void testOriginalLegacyMultiDexOnReleaseBuild() throws Exception {
        // ics flavor has minSdk = 14
        GradleBuildResult result =
                project.executor()
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleIcsRelease");

        assertThat(getMainDexListFile(project, "icsRelease").exists()).isTrue();
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_14);
        assertDexTask(result, genExpectedTaskStatesFor("IcsRelease", false));
    }

    @Test
    public void testOriginalNativeMultiDexOnReleaseBuild() throws Exception {
        // lollipop flavor has minSdk = 21
        GradleBuildResult result =
                project.executor()
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleLollipopRelease");

        assertThat(getMainDexListFile(project, "lollipopRelease").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "lollipop"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_21);
        assertDexTask(result, genExpectedTaskStatesFor("LollipopRelease", true));
    }

    @Test
    public void testUpgradeToNativeMultiDexOnReleaseBuild() throws Exception {
        // ics flavor has minSdk = 14 and we upgrade to 21 via IDE property
        GradleBuildResult result =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleIcsRelease");

        assertThat(getMainDexListFile(project, "icsRelease").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_21);
        assertDexTask(result, genExpectedTaskStatesFor("IcsRelease", true));
    }

    /** Regression test for https://issuetracker.google.com/72085541. */
    @Test
    public void testSwitchingDevices() throws Exception {
        GradleBuildResult result =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 19)
                        .run("clean", "assembleIcsDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG, "ics");
        assertThat(getMainDexListFile(project, "icsDebug").exists()).isTrue();
        assertThat(apk).hasDexVersion(DEX_VERSION_FOR_MIN_SDK_14);
        // because minSdkVersion = 14 and targetApi = 19
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", false));
        List<String> userClasses = new ArrayList<>();
        for (Dex dex : apk.getAllDexes()) {
            ImmutableSet<String> classNames = dex.getClasses().keySet();
            for (String className : classNames) {
                if (className.startsWith("Lcom/android/tests/basic")) {
                    userClasses.add(className);
                }
            }
        }

        result =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                        .run("assembleIcsDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG, "ics");
        // We skip the getMainDexListFile() assertion here, because since we didn't cleaned the
        // project before running with IDE_TARGET_DEVICE_API >= 21, the output is still there
        // even if we are executing in Native Multidex now.
        assertThat(apk).hasDexVersion(DEX_VERSION_FOR_MIN_SDK_24);
        // because IDE_TARGET_DEVICE_API
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", true));
        // make sure all user classes are still there
        for (String userClass : userClasses) {
            assertThat(apk).containsClass(userClass);
        }
    }

    /** Regression test for https://issuetracker.google.com/157701376. */
    @Test
    public void testSwitchingDevicesBothLightDesugar() throws Exception {
        // We first target 24, which is the first version that supports desugaring
        // with less things added to the classpath.
        GradleBuildResult result24 =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("clean", "assembleIcsRelease");

        assertThat(getMainDexListFile(project, "icsRelease").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_24);
        assertDexTask(result24, genExpectedTaskStatesFor("IcsRelease", true));

        // Now we re-run only changing the target to 25 and we should see dex tasks being up-to-date
        GradleBuildResult result25 =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 25)
                        // http://b/162074215
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("assembleIcsRelease");

        assertThat(getMainDexListFile(project, "icsRelease").exists()).isFalse();
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_24);
        assertDexTask(result25, genExpectedTaskStatesFor("IcsRelease", true, false));
    }

    @Test
    public void testDexingUsesDeviceApi() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "assembleIcsDebug");
        assertThat(getMainDexListFile(project, "icsDebug").exists()).isTrue();
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_14);
        // because minSdkVersion = 14
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", false));

        result =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                        .run("assembleIcsDebug");
        // We skip the getMainDexListFile() assertion here, because since we didn't cleaned the
        // project before running with IDE_TARGET_DEVICE_API >= 21, the output is still there
        // even if we are executing in Native Multidex now.
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "ics"))
                .hasDexVersion(DEX_VERSION_FOR_MIN_SDK_24);
        // because IDE_TARGET_DEVICE_API
        assertDexTask(result, genExpectedTaskStatesFor("IcsDebug", true));
    }

    /**
     * Return the mainDexList.txt from the intermediate outputs of the project. It's a way to check
     * if we ran under Legacy Multidex.
     */
    private static File getMainDexListFile(GradleTestProject project, String target) {
        String mainDexPath = target + "/mainDexList.txt";
        return new File(
                ArtifactTypeUtil.getOutputDir(
                        InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST.INSTANCE,
                        project.getBuildDir()),
                mainDexPath);
    }

    private static void assertDexTask(
            GradleBuildResult result, Map<String, TaskStateList.ExecutionState> expectedTasks) {
        TaskStateAssertionHelper helper = new TaskStateAssertionHelper(result.getTaskStates());
        helper.assertTaskStates(expectedTasks, false);
    }

    private static Map<String, TaskStateList.ExecutionState> genExpectedTaskStatesFor(
            String target, boolean nativeMultidex) {
        return genExpectedTaskStatesFor(target, nativeMultidex, true);
    }

    private static Map<String, TaskStateList.ExecutionState> genExpectedTaskStatesFor(
            String target, boolean nativeMultidex, boolean didWork) {
        TaskStateList.ExecutionState expectedState =
                didWork
                        ? TaskStateList.ExecutionState.DID_WORK
                        : TaskStateList.ExecutionState.UP_TO_DATE;
        if (nativeMultidex) {
            if (target.endsWith("Release")) {
                return ImmutableMap.of(
                        ":dexBuilder" + target, expectedState,
                        ":mergeDex" + target, expectedState,
                        ":mergeExtDex" + target, expectedState);
            } else {
                return ImmutableMap.of(
                        ":dexBuilder" + target, expectedState,
                        ":mergeLibDex" + target, expectedState,
                        ":mergeExtDex" + target, expectedState);
            }
        } else {
            return ImmutableMap.of(
                    ":dexBuilder" + target, expectedState,
                    ":mergeDex" + target, expectedState);
        }
    }
}
