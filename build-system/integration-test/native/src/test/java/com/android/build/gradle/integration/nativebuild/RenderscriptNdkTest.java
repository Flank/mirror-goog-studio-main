/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for renderscript with NDK mode enabled. */
public class RenderscriptNdkTest {
    private static final String thirtyTwoBitAbi = "x86";
    private static final String sixtyFourBitAbi = "x86_64";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("renderscriptNdk")
                    .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                    .create();

    private void checkPackagedFiles(boolean checkDotSo, boolean is32Bit, boolean is64Bit)
            throws IOException, InterruptedException {

        project.execute("clean", "assembleDebug");

        if (checkDotSo) {
            if (is32Bit) {
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .contains("lib/" + thirtyTwoBitAbi + "/librs.addint.so");
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .contains("lib/" + thirtyTwoBitAbi + "/libaddint.so");
            } else {
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .doesNotContain("lib/" + thirtyTwoBitAbi + "/librs.addint.so");
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .doesNotContain("lib/" + thirtyTwoBitAbi + "/libaddint.so");
            }

            if (is64Bit) {
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .contains("lib/" + sixtyFourBitAbi + "/librs.addint.so");
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .contains("lib/" + sixtyFourBitAbi + "/libaddint.so");
            } else {
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .doesNotContain("lib/" + sixtyFourBitAbi + "/librs.addint.so");
                assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                        .doesNotContain("lib/" + sixtyFourBitAbi + "/libaddint.so");
            }
        }

        Path rawDir = project.getBuildDir().toPath().resolve("generated/res/rs/debug/raw");
        Path bc32 = rawDir.resolve("bc32");
        Path bc64 = rawDir.resolve("bc64");

        if (is32Bit) {
            assertThat(bc32.resolve("addint.bc")).exists();
        } else {
            assertThat(bc32).doesNotExist();
        }

        if (is64Bit) {
            assertThat(bc64.resolve("addint.bc")).exists();
        } else {
            assertThat(bc64).doesNotExist();
        }
    }

    @Test
    public void checkSeparateAbis() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            abiFilters \""
                        + thirtyTwoBitAbi
                        + "\", \""
                        + sixtyFourBitAbi
                        + "\"\n"
                        + "        }\n"
                        + "    }"
                        + "}");

        checkPackagedFiles(true, true, true);
    }

    @Test
    public void check32BitAbi() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            abiFilters \""
                        + thirtyTwoBitAbi
                        + "\"\n"
                        + "        }\n"
                        + "    }"
                        + "}");

        checkPackagedFiles(true, true, false);
    }

    @Test
    public void check64BitAbi() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            abiFilters \""
                        + sixtyFourBitAbi
                        + "\"\n"
                        + "        }\n"
                        + "    }"
                        + "}");

        checkPackagedFiles(true, false, true);
    }

    @Test
    public void checkEmptyAbiFilter() throws IOException, InterruptedException {
        checkPackagedFiles(true, true, true);
    }

    @Test
    public void checkOldVersionApi() throws IOException, InterruptedException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "renderscriptTargetApi 28", "renderscriptTargetApi 20");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "minSdkVersion 21", "minSdkVersion 20");

        GradleBuildResult result = project.executor().expectFailure().run("clean", "assembleDebug");
        assertNotNull(result.getException());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Api version 20 does not support 64 bit ndk compilation");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "        ndk {\n"
                        + "            abiFilters \""
                        + thirtyTwoBitAbi
                        + "\"\n"
                        + "        }\n"
                        + "    }"
                        + "}");

        checkPackagedFiles(false, false, false);

        Path raw = project.getBuildDir().toPath().resolve("generated/res/rs/debug/raw");
        assertThat(raw.resolve("addint.bc")).exists();
    }
}
