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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.ddmlib.IDevice
import com.android.testutils.apk.Apk
import com.google.common.collect.ImmutableList
import com.google.common.collect.Range
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.testutils.truth.MoreTruth.assertThat

/**
 * Integration test of the native plugin with multiple variants.
 */
@CompileStatic
class NdkComponentSplitTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .useExperimentalGradleVersion(true)
            .create()

    @Rule
    public Adb adb = new Adb();

    @BeforeClass
    public static void setUp() {
        AssumeUtil.assumeBuildToolsAtLeast(21)
        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        generatePureSplits true

        defaultConfig {
            minSdkVersion {
                 apiLevel 21
            }
        }

        splits.with {
            abi {
                enable true
                reset()
                include "x86", "armeabi-v7a", "mips"
            }
        }
        ndk {
            moduleName "hello-jni"
        }
    }
}
"""
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    public void assembleDebug() {
        // Ensure compileDebugSource creates the shared object.
        project.execute("compileDebugSources");
        assertThat(project.file("build/intermediates/binaries/debug/lib/x86/libhello-jni.so"))
                .exists();

        project.execute("assembleDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk("debug")
        assertThat(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThat(apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThat(apk).doesNotContain("lib/x86/libhello-jni.so")

        Apk armApk = project.getApk("debug_armeabi-v7a")
        assertThat(armApk).contains("lib/armeabi-v7a/libhello-jni.so")
        assertThat(armApk).doesNotContain("lib/mips/libhello-jni.so")
        assertThat(armApk).doesNotContain("lib/x86/libhello-jni.so")

        Apk mipsApk = project.getApk("debug_mips")
        assertThat(mipsApk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThat(mipsApk).contains("lib/mips/libhello-jni.so")
        assertThat(mipsApk).doesNotContain("lib/x86/libhello-jni.so")

        Apk x86Apk = project.getApk("debug_x86")
        assertThat(x86Apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so")
        assertThat(x86Apk).doesNotContain("lib/mips/libhello-jni.so")
        assertThat(x86Apk).contains("lib/x86/libhello-jni.so")
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
        IDevice device = adb.getDevice(AndroidVersionMatcher.forRange(Range.singleton(21)));
        project.executeConnectedCheck(
                ImmutableList.of(Adb.getInjectToDeviceProviderProperty(device)));
    }
}
