/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.ndk;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
/**
 * Assemble tests for ndkJniLib.
 */
public class NdkJniLibTest {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniLib")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.execute("clean", "assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void lint() throws Exception {
        project.execute("lint");
    }

    @Test
    public void checkVersionCode() throws Exception {
        GradleTestProject app = project.getSubproject("app");

        TruthHelper.assertThat(
                        app.getApk(
                                "universal", GradleTestProject.DefaultApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1000123);
        TruthHelper.assertThat(
                        app.getApk(
                                "armeabi-v7a",
                                GradleTestProject.DefaultApkType.DEBUG,
                                "gingerbread"))
                .hasVersionCode(1100123);
        TruthHelper.assertThat(
                        app.getApk("mips", GradleTestProject.DefaultApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1200123);
        TruthHelper.assertThat(
                        app.getApk("x86", GradleTestProject.DefaultApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1300123);
        TruthHelper.assertThat(
                        app.getApk(
                                "universal",
                                GradleTestProject.DefaultApkType.DEBUG,
                                "icecreamSandwich"))
                .hasVersionCode(2000123);
        TruthHelper.assertThat(
                        app.getApk(
                                "armeabi-v7a",
                                GradleTestProject.DefaultApkType.DEBUG,
                                "icecreamSandwich"))
                .hasVersionCode(2100123);
        TruthHelper.assertThat(
                        app.getApk(
                                "mips", GradleTestProject.DefaultApkType.DEBUG, "icecreamSandwich"))
                .hasVersionCode(2200123);
        TruthHelper.assertThat(
                        app.getApk(
                                "x86", GradleTestProject.DefaultApkType.DEBUG, "icecreamSandwich"))
                .hasVersionCode(2300123);
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
