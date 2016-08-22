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
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.InstantRunArtifact;
import com.google.common.collect.Iterables;

import org.gradle.api.JavaVersion;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests support for Dagger and Instant Run.
 */
@RunWith(FilterableParameterized.class)
public class DaggerTest {

    private static final ColdswapMode COLDSWAP_MODE = ColdswapMode.MULTIDEX;
    private static final String ORIGINAL_MESSAGE = "from module";
    private static final String APP_MODULE_DESC = "Lcom/android/tests/AppModule;";
    private static final String GET_MESSAGE = "getMessage";

    @Rule
    public Logcat logcat = Logcat.create();

    @Rule
    public final Adb adb = new Adb();

    @Parameterized.Parameters(name = "{0},useAndroidApt={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"daggerOne", true},
                {"daggerTwo", true},
                {"daggerTwo", false}
        });
    }

    @Rule
    public GradleTestProject project;

    private File mAppModule;

    private final String testProject;
    private final boolean useAndroidApt;

    public DaggerTest(String testProject, boolean useAndroidApt) {
        this.testProject = testProject;
        this.useAndroidApt = useAndroidApt;

        project = GradleTestProject.builder()
                .fromTestProject(testProject)
                .create();
    }

    @Before
    public void setUp() throws IOException {
        Assume.assumeFalse("Disabled until instant run supports Jack", GradleTestProject.USE_JACK);
        if (this.testProject.equals("daggerTwo")) {
            Assume.assumeTrue(
                    "Dagger 2 only works on java 7+",
                    JavaVersion.current().isJava7Compatible());
        }
        mAppModule = project.file("src/main/java/com/android/tests/AppModule.java");

        if (testProject.equals("daggerTwo") && !useAndroidApt) {
            project.setBuildFile("build.no-apt.gradle");
        }
    }

    @Test
    public void normalBuild() throws Exception {
        project.execute("assembleDebug");
    }

    @Test
    public void coldSwap() throws Exception {
        new ColdSwapTester(project).testMultiDex(new ColdSwapTester.Steps() {
            @Override
            public void checkApk(@NonNull File apk) throws Exception {
                assertThatApk(apk).hasClass(APP_MODULE_DESC, INSTANT_RUN);
            }

            @Override
            public void makeChange() throws Exception {
                TestFileUtils.addMethod(
                        mAppModule,
                        "public String getMessage() { return \"coldswap\"; }");
                TestFileUtils.searchAndReplace(mAppModule, "\"from module\"", "getMessage()");
            }

            @Override
            public void checkVerifierStatus(@NonNull InstantRunVerifierStatus status) throws Exception {
                assertThat(status).isEqualTo(InstantRunVerifierStatus.METHOD_ADDED);
            }

            @Override
            public void checkArtifacts(@NonNull List<InstantRunBuildContext.Artifact> artifacts) throws Exception {
                InstantRunBuildContext.Artifact artifact = Iterables.getOnlyElement(artifacts);
                assertThatDex(artifact.getLocation())
                        .containsClass(APP_MODULE_DESC)
                        .that().hasMethod(GET_MESSAGE);
            }
        });
    }

    @Test
    public void hotSwap() throws Exception {
        InstantRun instantRunModel =
                InstantRunTestUtils.doInitialBuild(project, Packaging.DEFAULT, 23, COLDSWAP_MODE);

        TestFileUtils.searchAndReplace(mAppModule, "from module", "CHANGE");

        project.executor().withInstantRun(23, COLDSWAP_MODE)
                .run("assembleDebug");

        InstantRunArtifact artifact =
                InstantRunTestUtils.getReloadDexArtifact(instantRunModel);

        assertThatDex(artifact.file).containsClass("Lcom/android/tests/AppModule$override;");
    }

    @Test
    @Category(DeviceTests.class)
    public void hotSwap_device_art() throws Exception {
        doTestHotSwap(adb.getDevice(AndroidVersionMatcher.thatUsesArt()));
    }

    @Test
    @Category(DeviceTests.class)
    public void hotSwap_device_dalvik() throws Exception {
        doTestHotSwap(adb.getDevice(AndroidVersionMatcher.thatUsesDalvik()));
    }

    private void doTestHotSwap(IDevice iDevice) throws Exception {
        HotSwapTester tester =
                new HotSwapTester(
                        project,
                        Packaging.DEFAULT,
                        "com.android.tests",
                        "MainActivity",
                        this.testProject,
                        iDevice,
                        logcat);

        tester.run(
                () -> assertThat(logcat).containsMessageWithText(ORIGINAL_MESSAGE),
                new HotSwapTester.LogcatChange(1, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        TestFileUtils.searchAndReplace(
                                mAppModule, "from module", CHANGE_PREFIX + 1);
                    }
                },
                new HotSwapTester.LogcatChange(2, ORIGINAL_MESSAGE) {
                    @Override
                    public void makeChange() throws Exception {
                        TestFileUtils.searchAndReplace(
                                mAppModule, CHANGE_PREFIX + 1, CHANGE_PREFIX + 2);
                    }
                }
        );
    }

}
