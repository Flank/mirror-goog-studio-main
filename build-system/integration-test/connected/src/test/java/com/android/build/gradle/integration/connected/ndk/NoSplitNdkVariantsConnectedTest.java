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

package com.android.build.gradle.integration.connected.ndk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class NoSplitNdkVariantsConnectedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    defaultConfig {\n"
                        + "      minSdkVersion rootProject.supportLibMinSdk\n"
                        + "      testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path 'Android.mk'\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        release\n"
                        + "        debug {\n"
                        + "            jniDebuggable true\n"
                        + "        }\n"
                        + "    }\n"
                        + "    flavorDimensions 'abi'\n"
                        + "    productFlavors {\n"
                        + "        x86 {\n"
                        + "            ndk {\n"
                        + "                abiFilter 'x86'\n"
                        + "            }\n"
                        + "        }\n"
                        + "        arm {\n"
                        + "            ndk {\n"
                        + "                abiFilters 'armeabi-v7a'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "  androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n"
                        + "\n");
        TestFileUtils.appendToFile(
                new File(project.getBuildFile().getParentFile(), "Android.mk"),
                "LOCAL_PATH := $(call my-dir)\n"
                        + "include $(CLEAR_VARS)\n"
                        + "\n"
                        + "LOCAL_MODULE := hello-jni\n"
                        + "LOCAL_SRC_FILES := src/main/jni/hello-jni.c\n"
                        + "\n"
                        + "include $(BUILD_SHARED_LIBRARY)");
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    @Ignore("b/175333004 ADB is not supported")
    public void connectedAndroidTest() throws Exception {
        project.executor()
                .run(
                        "assembleX86Debug", "assembleX86DebugAndroidTest",
                        "assembleArmDebug", "assembleArmDebugAndroidTest");
        Collection<String> abis = ImmutableList.of("x86");
        String taskName =
                abis.contains("x86")
                        ? "devicePoolX86DebugAndroidTest"
                        : "devicePoolArmDebugAndroidTest";
        project.executor()
                .run(taskName);
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedX86DebugAndroidTest");
    }
}
