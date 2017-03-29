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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test basic project with crashlytics. */
public class CrashlyticsTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "android \\{",
                "buildscript {\n"
                        + "    dependencies {\n"
                        + "        classpath 'io.fabric.tools:gradle:1.22.1'\n"
                        + "    }\n"
                        + "}\n"
                        + "\n"
                        + "apply plugin: 'io.fabric'\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile 'com.crashlytics.sdk.android:crashlytics:2.6.7@aar'\n"
                        + "}\n"
                        + ""
                        + "android {\n"
                        + "    defaultConfig.minSdkVersion 16");

        TestFileUtils.searchAndReplace(
                project.file("src/main/AndroidManifest.xml"),
                "</application>",
                "    <meta-data\n"
                        + "        android:name=\"io.fabric.ApiKey\"\n"
                        + "        android:value=\"testkey\"\n"
                        + "    />\n"
                        + "</application>");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleDebug() throws IOException, InterruptedException {
        // Crashlytics will create .crashlytics/ in user.home directory.  So set it to the project
        // directory so that it doesn't affect other tests.
        project.executor()
                .withArgument("-Duser.home=" + project.getTestDir().getAbsolutePath())
                .run("assembleDebug");
    }
}
