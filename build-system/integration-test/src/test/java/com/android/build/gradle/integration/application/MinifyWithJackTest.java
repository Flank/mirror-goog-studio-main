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

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for minify with jack enabled
 */
public class MinifyWithJackTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minify")
            .withJack(true)
            .create();

    @Before
    public void setUp() throws Exception {
        project
                .executor()
                .run("clean", "assembleMinified", "assembleDebugAndroidTest");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() {
        project.executeConnectedCheck();
    }

    @Test
    public void checkApkIsMinified() throws Exception {
        ApkSubject apkSubject = assertThat(project.getApk("minified"));

        apkSubject.doesNotContainClass("Lcom/android/tests/basic/UnusedTestClass;");
        apkSubject.containsClass("Lcom/android/tests/basic/a;");

        apkSubject
                .hasMainClass("Lcom/android/tests/basic/Main;")
                .that()
                // Make sure default ProGuard rules were applied.
                .hasMethod("handleOnClick");
    }

    @Test
    public void checkTestApkNotMinified() throws Exception {
        ApkSubject apkSubject = assertThat(project.getTestApk("debug"));

        apkSubject.containsClass("Lcom/android/tests/basic/MainTest;");
        apkSubject.containsClass("Lcom/android/tests/basic/UnusedTestClass;");
        apkSubject.containsClass("Lcom/android/tests/basic/UsedTestClass;");
        apkSubject.containsClass("Lcom/android/tests/basic/test/BuildConfig;");
    }
}
