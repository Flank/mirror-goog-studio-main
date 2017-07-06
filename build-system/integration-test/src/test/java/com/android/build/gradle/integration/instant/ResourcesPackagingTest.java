/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test the various packaging scheme for the resources. In 26 and above, the resources are packaged
 * in their own split APK.
 */
@RunWith(Parameterized.class)
public class ResourcesPackagingTest {

    @Rule
    public GradleTestProject mProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Rule public Logcat logcat = Logcat.create();

    @Rule public final Adb adb = new Adb();

    private final boolean separateResourcesApk;

    @Parameterized.Parameters(name = "separateResourcesApk={0}")
    public static Boolean[] getParameters() {
        return new Boolean[] {Boolean.TRUE, Boolean.FALSE};
    }

    public ResourcesPackagingTest(boolean separateResourcesApk) {
        this.separateResourcesApk = separateResourcesApk;
    }

    @Test
    public void testFullAndIncrementalBuilds() throws Exception {

        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(mProject.model().getSingle().getOnlyModel());

        AndroidVersion androidVersion = new AndroidVersion(separateResourcesApk ? 26 : 21, null);
        mProject.executor()
                .withInstantRun(androidVersion, OptionalCompilationStep.FULL_APK)
                .with(BooleanOption.ENABLE_SEPARATE_APK_RESOURCES, separateResourcesApk)
                .run("assembleDebug");

        InstantRunBuildInfo buildInfo = InstantRunTestUtils.loadContext(instantRunModel);

        // 2 extra apks when using separate apk for resources, one is the main apk,
        // the other the resources.apk. Otherwise, just one for the main apk
        int extraApks = separateResourcesApk ? 2 : 1;
        assertThat(buildInfo.getArtifacts())
                .hasSize(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES + extraApks);
        assertThat(
                        InstantRunTestUtils.getArtifactsOfType(
                                        buildInfo, InstantRunArtifactType.SPLIT)
                                .size())
                .isEqualTo(InstantRunSlicer.NUMBER_OF_SLICES_FOR_PROJECT_CLASSES);

        InstantRunArtifact splitMain =
                InstantRunTestUtils.getOnlyArtifactOfType(
                        buildInfo, InstantRunArtifactType.SPLIT_MAIN);
        assertThat(splitMain.file).exists();

        Apk containsResources;
        if (separateResourcesApk) {
            containsResources =
                    new Apk(
                            InstantRunTestUtils.getOnlyArtifactOfType(
                                            buildInfo, InstantRunArtifactType.RESOURCES)
                                    .file);
            assertThat(new Apk(splitMain.file)).doesNotContainResource("layout/main.xml");
        } else {
            containsResources = new Apk(splitMain.file);
        }

        assertThat(containsResources).exists();
        assertThat(containsResources).containsResource("layout/main.xml");

        // now modify a resource.
        TemporaryProjectModification.doTest(
                mProject,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "src/main/res/values/strings.xml", "HelloWorld", "HelloNewWorld");

                    GradleBuildResult run =
                            mProject.executor()
                                    .withInstantRun(androidVersion)
                                    .with(
                                            BooleanOption.ENABLE_SEPARATE_APK_RESOURCES,
                                            separateResourcesApk)
                                    .run("assembleDebug");
                    assertThat(run.getFailureMessage()).isNull();

                    InstantRunBuildInfo modifiedBuildInfo =
                            InstantRunTestUtils.loadContext(instantRunModel);

                    assertThat(modifiedBuildInfo.getArtifacts()).hasSize(1);
                    InstantRunArtifact resourcesApk = modifiedBuildInfo.getArtifacts().get(0);
                    assertThat(resourcesApk.type).isEqualTo(InstantRunArtifactType.RESOURCES);
                    assertThat(resourcesApk.file.getName())
                            .endsWith(
                                    separateResourcesApk
                                            ? SdkConstants.DOT_ANDROID_PACKAGE
                                            : SdkConstants.DOT_RES);
                });
    }
}
