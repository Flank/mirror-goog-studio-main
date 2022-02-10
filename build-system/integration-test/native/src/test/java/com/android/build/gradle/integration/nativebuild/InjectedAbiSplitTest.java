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

package com.android.build.gradle.integration.nativebuild;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION;
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.getSoFolderFor;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.testutils.truth.ZipFileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkLocation;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.cxx.configure.CMakeVersion;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Check cmake build with split and injected ABI. */
public class InjectedAbiSplitTest {
    @Rule
    public GradleTestProject sProject;

    public InjectedAbiSplitTest() {
        sProject =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().withCmake().build())
                        .setCmakeVersion(CMakeVersion.DEFAULT.getSdkFolderName())
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .create();
    }

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                sProject.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path 'CMakeLists.txt'\n"
                        + "        }\n"
                        + "    }\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            universalApk true\n"
                        + "            reset()\n"
                        + "            include 'arm64-v8a', 'x86'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkSingleBuildAbi() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "arm64-v8a")
                .run("clean", "assembleDebug");
        File arm64Apk =
                sProject.getApkAsFile(
                        "arm64-v8a", GradleTestProject.ApkType.DEBUG, ApkLocation.Intermediates);
        checkApkContent(arm64Apk, Abi.ARM64_V8A);

        assertThat(sProject.getApkAsFile("universal", GradleTestProject.ApkType.DEBUG))
                .doesNotExist();
        assertThat(sProject.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        assertThat(getSoFolderFor(sProject, Abi.ARM64_V8A)).exists();
        assertThat(getSoFolderFor(sProject, Abi.X86)).isNull();
        assertThat(getSoFolderFor(sProject, Abi.ARMEABI_V7A)).isNull();
        assertThat(getSoFolderFor(sProject, Abi.X86_64)).isNull();
    }

    @Test
    public void checkNormalBuild() throws Exception {
        sProject.executor().run("clean", "assembleDebug");
        File universalApk = sProject.getApkAsFile("universal", GradleTestProject.ApkType.DEBUG);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);

        File arm64Apk = sProject.getApkAsFile("arm64-v8a", GradleTestProject.ApkType.DEBUG);
        checkApkContent(arm64Apk, Abi.ARM64_V8A);

        File x86Apk = sProject.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG);
        checkApkContent(x86Apk, Abi.X86);

        assertThat(getSoFolderFor(sProject, Abi.ARM64_V8A)).exists();
        assertThat(getSoFolderFor(sProject, Abi.X86)).exists();
        assertThat(getSoFolderFor(sProject, Abi.ARMEABI_V7A)).isNull();
        assertThat(getSoFolderFor(sProject, Abi.X86_64)).isNull();
    }

    @Test
    public void checkEmptyListDoNotFilter() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "")
                .run("clean", "assembleDebug");
        File universalApk =
                sProject.getApkAsFile(
                        "universal", GradleTestProject.ApkType.DEBUG, ApkLocation.Intermediates);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);
    }

    @Test
    public void checkBuildOnlyTargetAbiCanBeDisabled() throws Exception {
        sProject.executor()
                .with(BooleanOption.BUILD_ONLY_TARGET_ABI, false)
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi")
                .run("clean", "assembleDebug");
        File universalApk =
                sProject.getApkAsFile(
                        "universal", GradleTestProject.ApkType.DEBUG, ApkLocation.Intermediates);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);
    }

    private void checkApkContent(File apk, Abi... abis) throws Exception {
        List<Abi> abiList = Arrays.asList(abis);
        for (Abi abi : NdkHelper.getAbiList(sProject)) {
            String path = "lib/" + abi.getTag() + '/' + "libhello-jni.so";
            assertThat(
                    apk,
                    it -> {
                        if (abiList.contains(abi)) {
                            it.contains(path);
                        } else {
                            it.doesNotContain(path);
                        }
                    });
        }
    }
}
