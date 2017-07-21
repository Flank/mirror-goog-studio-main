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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for ndk-build. */
@RunWith(Parameterized.class)
public class NdkBuildBasicProjectTest {

    private boolean isModel;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().build())
                    .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
                    .useExperimentalGradleVersion(isModel)
                    .create();

    @Parameterized.Parameters(name = "model = {0}")
    public static Collection<Object[]> data() {
        return new ArrayList<Object[]>(
                Arrays.asList(
                        new ArrayList<Boolean>(Arrays.asList(false)).toArray(),
                        new ArrayList<Boolean>(Arrays.asList(true)).toArray()));
    }

    public NdkBuildBasicProjectTest(boolean isModel) {
        this.isModel = isModel;
    }

    @Before
    public void setUp() throws IOException {
        String plugin =
                isModel
                        ? "apply plugin: 'com.android.model.application'"
                        : "apply plugin: 'com.android.application'";
        String modelBefore = isModel ? "model { " : "";
        String modelAfter = isModel ? " }" : "";
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + plugin
                        + "\n"
                        + modelBefore
                        + "\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        defaultConfig {\n"
                        + "          externalNativeBuild {\n"
                        + "              ndkBuild {\n"
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          ndkBuild {\n"
                        + "            path \"src/main/jni/Android.mk\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + modelAfter
                        + "\n"
                        + "\n"
                        + modelBefore
                        + "\n"
                        + "    android.packagingOptions {\n"
                        + "        doNotStrip \"*/armeabi-v7a/libhello-jni.so\"\n"
                        + "    }\n"
                        + modelAfter
                        + "\n");
        if (!isModel) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "\n"
                            + "android {\n"
                            + "    applicationVariants.all { variant ->\n"
                            + "        assert !variant.getExternalNativeBuildTasks().isEmpty()\n"
                            + "        for (def task : variant.getExternalNativeBuildTasks()) {\n"
                            + "            assert task.getName() == \"externalNativeBuild\" + variant.getName().capitalize()\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n");
        }
    }

    @Test
    public void checkApkContent() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isNotStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void chekApkContentWithInjectedAbi() throws IOException, InterruptedException {
        // Pass invalid-abi, x86 and armeabi. The first (invalid-abi) should be ignored because
        // it is not valid for the build . The second (x86) should be the one chosen to build.
        // Finally, armeabi is valid but it will be ignored because x86 is "preferred".
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "invalid-abi,x86,armeabi")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        assertThat(project.file("build/intermediates/manifests/full/debug/AndroidManifest.xml"))
                .contains("android:testOnly=\"true\"");
    }

    @Test
    public void checkModel() throws IOException {
        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.NDK_BUILD.getName());
        assertThat(model.getBuildFiles()).hasSize(1);
        assertThat(model.getName()).isEqualTo("project");
        int abiCount = 3;
        assertThat(model.getArtifacts()).hasSize(abiCount * 2);
        assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            assertThat(pathElements).doesNotContain("lib");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(model).hasArtifactGroupsNamed("debug", "release");
        assertThat(model).hasArtifactGroupsOfSize(abiCount);
    }

    @Test
    public void checkClean() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(6);
        assertThat(model).allBuildOutputsExist();
        assertThat(model).hasExactObjectFilesInBuildFolder("hello-jni.o");
        assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
        project.execute("clean");
        assertThat(model).noBuildOutputsExist();
        assertThat(model).hasExactObjectFilesInBuildFolder();
        assertThat(model).hasExactSharedObjectFilesInBuildFolder();
    }

    @Test
    public void checkCleanAfterAbiSubset() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).hasBuildOutputCountEqualTo(6);

        List<File> allBuildOutputs = Lists.newArrayList();
        for (NativeArtifact artifact : model.getArtifacts()) {
            assertThat(artifact.getOutputFile()).isFile();
            allBuildOutputs.add(artifact.getOutputFile());
        }

        // Change the build file to only have "x86"
        String plugin =
                isModel
                        ? "apply plugin: 'com.android.model.application'"
                        : "apply plugin: 'com.android.application'";
        String modelBefore = isModel ? "model { " : "";
        String modelAfter = isModel ? " }" : "";
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + plugin
                        + "\n"
                        + modelBefore
                        + "\n"
                        + "    android {\n"
                        + "        defaultConfig {\n"
                        + "          externalNativeBuild {\n"
                        + "              ndkBuild {\n"
                        + "                abiFilters.clear();\n"
                        + "                abiFilters.addAll(\"x86\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + modelAfter
                        + "\n");
        project.execute("clean");

        // All build outputs should no longer exist, even the non-x86 outputs
        for (File output : allBuildOutputs) {
            assertThat(output).doesNotExist();
        }
    }
}
