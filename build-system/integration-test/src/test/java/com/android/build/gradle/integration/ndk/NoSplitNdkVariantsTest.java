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

import static com.android.build.gradle.integration.common.utils.AndroidVersionMatcher.anyAndroidVersion;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ddmlib.IDevice;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.util.Collection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Integration test of the native plugin with multiple variants without using splits.
 */
public class NoSplitNdkVariantsTest {

    @Rule
    public Adb adb = new Adb();

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
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
                        + "                abiFilters 'armeabi-v7a', 'armeabi'\n"
                        + "            }\n"
                        + "        }\n"
                        + "        mips {\n"
                        + "            ndk {\n"
                        + "                abiFilter 'mips'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
        TestFileUtils.appendToFile(
                new File(project.getBuildFile().getParentFile(), "Android.mk"),
                "LOCAL_PATH := $(call my-dir)\n"
                        + "include $(CLEAR_VARS)\n"
                        + "\n"
                        + "LOCAL_MODULE := hello-jni\n"
                        + "LOCAL_SRC_FILES := src/main/jni/hello-jni.c\n"
                        + "\n"
                        + "include $(BUILD_SHARED_LIBRARY)");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleX86Release() throws Exception {
        project.execute("assembleX86Release");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.DefaultApkType.RELEASE, "x86");
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleArmRelease() throws Exception {
        project.execute("assembleArmRelease");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.DefaultApkType.RELEASE, "arm");
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));

    }

    @Test
    public void assembleMipsRelease() throws Exception {
        project.execute("assembleMipsRelease");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(null, GradleTestProject.DefaultApkType.RELEASE, "mips");
        assertNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws Exception {
        project.executor().run(
                "assembleX86Debug", "assembleX86DebugAndroidTest",
                "assembleArmDebug", "assembleArmDebugAndroidTest");
        IDevice testDevice = adb.getDevice(anyAndroidVersion());
        Collection<String> abis = testDevice.getAbis();
        String taskName = abis.contains("x86") ?
                "devicePoolX86DebugAndroidTest" : "devicePoolArmDebugAndroidTest";
        project.executor()
                .withArgument(Adb.getInjectToDeviceProviderProperty(testDevice))
                .run(taskName);
    }
}
