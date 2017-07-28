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

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.instant.InstantRunTestUtils.PORTS;
import static com.android.testutils.truth.MoreTruth.assertThatDex;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunBuildMode;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.SplitApks;
import com.android.tools.ir.client.InstantRunArtifact;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ButterKnifeTest {
    private static final String ORIGINAL_MESSAGE = "original";
    private static final String ACTIVITY_DESC = "Lcom/example/bk/Activ;";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("butterknife").create();

    private File mActiv;

    @Rule public Logcat logcat = Logcat.create();

    @Rule public final Adb adb = new Adb();

    @Before
    public void setUp() throws Exception {
        mActiv = project.file("src/main/java/com/example/bk/Activ.java");
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    public void coldSwapBuild() throws Exception {
        new ColdSwapTester(project)
                .testMultiApk(
                        new ColdSwapTester.Steps() {
                            @Override
                            public void checkApks(@NonNull SplitApks apks) throws Exception {
                                assertThat(apks).hasClass(ACTIVITY_DESC);
                            }

                            @Override
                            public void makeChange() throws Exception {
                                TestFileUtils.searchAndReplace(
                                        mActiv.getAbsoluteFile(),
                                        "text\\.getText\\(\\)\\.toString\\(\\)",
                                        "getMessage()");
                                TestFileUtils.addMethod(
                                        mActiv,
                                        "public String getMessage() { return text.getText().toString(); }");
                            }

                            @Override
                            public void checkVerifierStatus(
                                    @NonNull InstantRunVerifierStatus status) throws Exception {
                                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
                            }

                            @Override
                            public void checkBuildMode(@NonNull InstantRunBuildMode buildMode)
                                    throws Exception {
                                // for multi dex cold build mode is triggered
                                assertEquals(InstantRunBuildMode.COLD, buildMode);
                            }

                            @Override
                            public void checkArtifacts(
                                    @NonNull List<InstantRunBuildContext.Artifact> artifacts)
                                    throws Exception {
                                InstantRunBuildContext.Artifact artifact =
                                        Iterables.getOnlyElement(artifacts);
                                assertThatApk(new Apk(artifact.getLocation()))
                                        .hasClass(ACTIVITY_DESC)
                                        .that()
                                        .hasMethod("getMessage");
                            }
                        });
    }

    @Test
    public void hotSwap() throws Exception {
        AndroidVersion androidVersion = new AndroidVersion(23, null);

        InstantRun instantRunModel = InstantRunTestUtils.doInitialBuild(project, androidVersion);

        makeHotSwapChange("CHANGE");

        project.executor().withInstantRun(androidVersion).run("assembleDebug");

        InstantRunArtifact artifact = InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file).containsClass("Lcom/example/bk/Activ$override;");
    }

    private void makeHotSwapChange(String change) throws Exception {
        TestFileUtils.searchAndReplace(
                mActiv, "text\\.getText\\(\\)\\.toString\\(\\)", "\"" + change + "\"");
    }

    @Test
    @Category(DeviceTests.class)
    public void hotSwap_dalvik() throws Exception {
        doTestHotSwap(adb.getDevice(AndroidVersionMatcher.thatUsesDalvik()));
    }

    @Test
    @Category(DeviceTests.class)
    public void hotSwap_art() throws Exception {
        doTestHotSwap(adb.getDevice(AndroidVersionMatcher.thatUsesArt()));
    }

    private void doTestHotSwap(IDevice device) throws Exception {
        HotSwapTester tester =
                new HotSwapTester(
                        project,
                        "com.example.bk",
                        "Activ",
                        "butterknife",
                        device,
                        logcat,
                        PORTS.get(ButterKnifeTest.class.getSimpleName()));

        tester.run(
                () -> assertThat(logcat).containsMessageWithText(ORIGINAL_MESSAGE),
                new HotSwapTester.LogcatChange(1, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        makeHotSwapChange(CHANGE_PREFIX + 1);
                    }
                },
                new HotSwapTester.LogcatChange(2, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        TestFileUtils.searchAndReplace(
                                mActiv, CHANGE_PREFIX + 1, CHANGE_PREFIX + 2);
                    }
                });
    }
}
