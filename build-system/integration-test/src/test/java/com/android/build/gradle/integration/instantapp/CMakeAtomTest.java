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

package com.android.build.gradle.integration.instantapp;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAtomBundle;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.truth.AtomBundleSubject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.ide.common.process.ProcessException;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Atom assemble tests for Cmake. */
public class CMakeAtomTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("singleAtom")
                    .addFile(HelloWorldJniApp.cmakeLists("atom"))
                    .addFile(HelloWorldJniApp.cppSource("atom/src/main/jni"))
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        // b.android.com/227451
        AssumeUtil.assumeResolveDependencyOnConfiguration();

        TestFileUtils.appendToFile(
                sProject.getSubproject(":atom").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void nativeLibsArePresent() throws IOException, ProcessException, InterruptedException {
        sProject.execute("clean", ":instantApp:assembleRelease");

        File atomBundle = sProject.getSubproject(":atom").getAtomBundle("release");
        AtomBundleSubject atomBundleSubject = assertThatAtomBundle(atomBundle);
        atomBundleSubject.contains("libs/lib/armeabi/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/armeabi-v7a/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/arm64-v8a/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/x86/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/x86_64/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/mips/libhello-jni.so");
        atomBundleSubject.contains("libs/lib/mips64/libhello-jni.so");

        File bundleNativeLib = ZipHelper.extractFile(atomBundle, "libs/lib/x86/libhello-jni.so");
        assertThatNativeLib(bundleNativeLib).isStripped();

        File atom = sProject.getSubproject(":instantApp").getAtom("atom", "release");
        ApkSubject apkSubject = assertThatApk(atom);
        apkSubject.contains("lib/armeabi/libhello-jni.so");
        apkSubject.contains("lib/armeabi-v7a/libhello-jni.so");
        apkSubject.contains("lib/arm64-v8a/libhello-jni.so");
        apkSubject.contains("lib/x86/libhello-jni.so");
        apkSubject.contains("lib/x86_64/libhello-jni.so");
        apkSubject.contains("lib/mips/libhello-jni.so");
        apkSubject.contains("lib/mips64/libhello-jni.so");

        File atomNativeLib = ZipHelper.extractFile(atom, "lib/x86/libhello-jni.so");
        assertThatNativeLib(atomNativeLib).isStripped();
    }
}
