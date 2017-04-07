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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for composite build. */
public class CompositeBuildTest {

    @Rule
    public GradleTestProject app =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .withName("app")
                    .withDependencyChecker(false)
                    .create();

    @Rule
    public GradleTestProject lib =
            GradleTestProject.builder()
                    .fromTestApp(new EmptyAndroidTestApp())
                    .withName("lib")
                    .create();

    @Rule
    public GradleTestProject androidLib =
            GradleTestProject.builder()
                    .fromTestApp(new EmptyAndroidTestApp())
                    .withName("androidLib")
                    .withDependencyChecker(false)
                    .create();

    @Before
    public void setUp() throws IOException {
        Files.write(
                "includeBuild('../lib') {\n"
                        + "    dependencySubstitution {\n"
                        + "        substitute module('com.example:lib') with project(':')\n"
                        + "    }\n"
                        + "}\n"
                        + "includeBuild('../androidLib') {\n"
                        + "    dependencySubstitution {\n"
                        + "        substitute module('com.example:androidLib') with project(':')\n"
                        + "    }\n"
                        + "}\n",
                app.file("settings.gradle"),
                Charsets.UTF_8);

        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "android {\n"
                        + "    buildTypes.debug.testCoverageEnabled true\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    compile 'com.example:lib'\n"
                        + "    compile 'com.example:androidLib'\n"
                        + "}\n");

        // lib is just an empty project.
        lib.file("settings.gradle").createNewFile();
        TestFileUtils.appendToFile(lib.getBuildFile(), "apply plugin: 'java'\n");

        // lib is just an empty project.
        androidLib.file("settings.gradle").createNewFile();
        TestFileUtils.appendToFile(
                androidLib.getBuildFile(),
                "apply plugin: 'com.android.library'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "}\n");
        Files.createParentDirs(androidLib.file("src/main/AndroidManifest.xml"));
        Files.write(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"com.example.androidLib\"\n"
                        + "        android:versionCode=\"1\"\n"
                        + "        android:versionName=\"1.0\">\n"
                        + "    <application/>\n"
                        + "</manifest>\n",
                androidLib.file("src/main/AndroidManifest.xml"),
                Charsets.UTF_8);
    }

    @Test
    public void assembleDebug() throws Exception {
        app.execute(":assembleDebug");
        assertThat(app.getApk("debug")).exists();
    }
}
