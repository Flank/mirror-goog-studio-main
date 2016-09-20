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

import static com.android.build.gradle.integration.common.truth.AbstractAndroidSubject.ClassFileScope.INSTANT_RUN;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatDex;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.Logcat;
import com.android.build.gradle.integration.common.fixture.Packaging;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunArtifact;
import com.google.common.collect.Iterables;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ButterKnifeTest {
    private static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;
    private static final String ORIGINAL_MESSAGE = "original";
    private static final String ACTIVITY_DESC = "Lcom/example/bk/Activ;";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("butterknife").create();

    private File mActiv;

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public final Adb adb = new Adb();

    @Before
    public void setUp() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        mActiv = project.file("src/main/java/com/example/bk/Activ.java");
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    public void coldSwapBuild() throws Exception {
        new ColdSwapTester(project).testMultiDex(new ColdSwapTester.Steps() {
            @Override
            public void checkApk(@NonNull File apk) throws Exception {
                assertThatApk(apk).hasClass(ACTIVITY_DESC, INSTANT_RUN);
            }

            @Override
            public void makeChange() throws Exception {
                TestFileUtils.searchAndReplace(mActiv.getAbsoluteFile(),
                        "text\\.getText\\(\\)\\.toString\\(\\)", "getMessage()");
                TestFileUtils.addMethod(
                        mActiv,
                        "public String getMessage() { return text.getText().toString(); }");
            }

            @Override
            public void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception {
                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
            }

            @Override
            public void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts) throws Exception {
                InstantRunBuildContext.Artifact artifact = Iterables.getOnlyElement(artifacts);
                assertThatDex(artifact.getLocation())
                        .containsClass(ACTIVITY_DESC)
                        .that().hasMethod("getMessage");
            }
        });
    }

    @Test
    public void hotSwap() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.doInitialBuild(project, Packaging.DEFAULT, 23, COLDSWAP_MODE);

        makeHotSwapChange("CHANGE");

        project.executor()
                .withInstantRun(23, COLDSWAP_MODE)
                .run("assembleDebug");

        InstantRunArtifact artifact =
                InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file).containsClass("Lcom/example/bk/Activ$override;");
    }

    private void makeHotSwapChange(String change) throws Exception {
        TestFileUtils.searchAndReplace(
                mActiv, "text\\.getText\\(\\)\\.toString\\(\\)", "\""+ change +"\"");
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
                        Packaging.DEFAULT,
                        "com.example.bk",
                        "Activ",
                        "butterknife",
                        device,
                        logcat);

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
