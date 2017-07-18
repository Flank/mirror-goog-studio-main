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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.SigningHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.StringOption;
import com.android.ddmlib.IDevice;
import com.android.testutils.apk.Apk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Integration test of the native plugin with multiple variants. */
public class NdkComponentSplitTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Rule public Adb adb = new Adb();

    @BeforeClass
    public static void setUp() throws Exception {
        AssumeUtil.assumeBuildToolsAtLeast(21);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "        generatePureSplits true\n"
                        + "\n"
                        + "        defaultConfig {\n"
                        + "            minSdkVersion {\n"
                        + "                 apiLevel 21\n"
                        + "            }\n"
                        + "        }\n"
                        + "\n"
                        + "        splits.with {\n"
                        + "            abi {\n"
                        + "                enable true\n"
                        + "                reset()\n"
                        + "                include \"x86\", \"armeabi-v7a\", \"mips\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void assembleDebug() throws Exception {
        // Ensure compileDebugSource creates the shared object.
        project.execute("compileDebugSources");
        assertThat(project.file("build/intermediates/binaries/debug/lib/x86/libhello-jni.so"))
                .exists();

        project.execute("assembleDebug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk(ApkType.DEBUG);
        assertThat(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/mips/libhello-jni.so");
        assertThat(apk).doesNotContain("lib/x86/libhello-jni.so");
        SigningHelper.assertApkSignaturesVerify(apk);

        Apk armApk = project.getApk("armeabi-v7a", ApkType.DEBUG);
        assertThat(armApk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThat(armApk).doesNotContain("lib/mips/libhello-jni.so");
        assertThat(armApk).doesNotContain("lib/x86/libhello-jni.so");
        SigningHelper.assertApkSignaturesVerify(armApk);

        Apk mipsApk = project.getApk("mips", ApkType.DEBUG);
        assertThat(mipsApk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThat(mipsApk).contains("lib/mips/libhello-jni.so");
        assertThat(mipsApk).doesNotContain("lib/x86/libhello-jni.so");
        SigningHelper.assertApkSignaturesVerify(mipsApk);

        Apk x86Apk = project.getApk("x86", ApkType.DEBUG);
        assertThat(x86Apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThat(x86Apk).doesNotContain("lib/mips/libhello-jni.so");
        assertThat(x86Apk).contains("lib/x86/libhello-jni.so");
        SigningHelper.assertApkSignaturesVerify(x86Apk);
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedAndroidTest() throws Exception {
        IDevice device = adb.getDevice(AndroidVersionMatcher.thatUsesArt());
        project.executor()
                .with(StringOption.DEVICE_POOL_SERIAL, device.getSerialNumber())
                .executeConnectedCheck();
    }
}
