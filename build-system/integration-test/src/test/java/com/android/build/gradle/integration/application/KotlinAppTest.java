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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.builder.model.AndroidProject;
import com.android.testutils.apk.Apk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for kotlin. */
@Category(SmokeTests.class)
public class KotlinAppTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("kotlinApp").create();

    @Rule public Adb adb = new Adb();

    public static AndroidProject model;

    @BeforeClass
    public static void getModel() throws Exception {
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void projectModel() {
        assertFalse("Library Project", model.isLibrary());
        assertEquals("Project Type", AndroidProject.PROJECT_TYPE_APP, model.getProjectType());
        assertEquals(
                "Compile Target", GradleTestProject.getCompileSdkHash(), model.getCompileTarget());
    }

    @Test
    public void apkContents() throws Exception {
        Apk apk = project.getApk("debug");
        assertNotNull(apk);
        assertThat(apk).containsResource("layout/activity_layout.xml");
        assertThat(apk).containsMainClass("Lcom/example/android/kotlin/MainActivity;");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
