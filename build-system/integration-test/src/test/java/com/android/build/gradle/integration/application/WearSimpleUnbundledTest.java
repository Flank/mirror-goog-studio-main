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

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.GradleExceptionsHelper.getFailureMessage;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Assemble tests for unbundled wear app with a single app.
 */
public class WearSimpleUnbundledTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("simpleMicroApp")
            .create();

    @Before
    public void setUp() throws IOException {
        File mainAppBuildGradle = project.file("main/build.gradle");

        TestFileUtils.appendToFile(mainAppBuildGradle,
                "android {\n"
                + "  defaultConfig {\n"
                + "    wearAppUnbundled true\n"
                + "  }\n"
                + "}\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkDefaultNonEmbedding() throws IOException {
        project.execute("clean", ":main:assemble");

        String embeddedApkPath = FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK +
                DOT_ANDROID_PACKAGE;

        List<String> apkNames = Lists.newArrayList("release-unsigned", "debug");

        for (String apkName : apkNames) {
            File fullApk = project.getSubproject("main").getApk(apkName);
            assertThatZip(fullApk).doesNotContain(embeddedApkPath);
        }
    }

    @Test
    public void checErrorOnUnbundledFlagPlusDependency() throws IOException {
        File mainAppBuildGradle = project.file("main/build.gradle");

        TestFileUtils.appendToFile(mainAppBuildGradle,
                "dependencies {\n"
                + "  wearApp project(':wear')\n"
                + "}\n");

        GradleBuildResult result = project.executor().expectFailure().run(
                "clean", ":main:assembleDebug");

        //noinspection ThrowableResultOfMethodCallIgnored
        assertThat(getFailureMessage(result.getException(), GradleException.class)).contains(
                "Wear app unbundling is turned on but a dependency on a wear App has been found for variant debug");
    }
}
