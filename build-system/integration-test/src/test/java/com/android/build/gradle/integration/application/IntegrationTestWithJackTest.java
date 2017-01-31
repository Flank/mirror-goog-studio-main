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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests the integration tests from androidTest are compiled successfully using Jack. */
public class IntegrationTestWithJackTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.defaultConfig.minSdkVersion = "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "android.defaultConfig.jackOptions.enabled = true");
    }

    @Test
    public void testAssembleAndroidTest() throws Exception {
        project.executor().run("clean", "assembleAndroidTest");

        Apk androidTestApk = project.getTestApk();
        assertThat(androidTestApk).containsClass("Lcom/example/helloworld/HelloWorldTest;");
    }

    @Test
    public void testDependencyOnTestedApp() throws Exception {
        TestFileUtils.addMethod(
                project.file("src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "\nprivate void referTestedApp() {\n"
                        + "    com.example.helloworld.HelloWorld.class.getName();\n"
                        + "}");

        project.executor().run("clean", "assembleAndroidTest");

        Apk androidTestApk = project.getTestApk();
        assertThat(androidTestApk).containsClass("Lcom/example/helloworld/HelloWorldTest;");
    }

    @Test
    public void testUsingTestedAppDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "\n"
                                + "dependencies {\n"
                                + "    compile 'com.android.support:support-v4:%s'\n"
                                + "}\n",
                        GradleTestProject.SUPPORT_LIB_VERSION));
        TestFileUtils.addMethod(
                project.file("src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "\nprivate void referTestedAppDependency() {\n"
                        + "    android.support.v4.app.Fragment.class.getName();\n"
                        + "}");

        project.executor().run("clean", "assembleAndroidTest");

        Apk androidTestApk = project.getTestApk();
        assertThat(androidTestApk).containsClass("Lcom/example/helloworld/HelloWorldTest;");
    }
}
