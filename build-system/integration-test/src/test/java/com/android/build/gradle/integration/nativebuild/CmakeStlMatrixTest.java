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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for Cmake. */
@RunWith(Parameterized.class)
public class CmakeStlMatrixTest {

    private boolean isModel;
    private String stl;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").build())
                    .addFile(HelloWorldJniApp.cmakeLists("."))
                    .useExperimentalGradleVersion(isModel)
                    .create();

    @Parameterized.Parameters(name = "model = {0} stl = {1}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(
                        new Object[]{false, "system"},
                        new Object[]{false, "c++_shared"},
                        new Object[]{false, "gnustl_shared"},
                        new Object[]{false, "stlport_shared"},
                        new Object[]{false, "c++_static"},
                        new Object[]{false, "gnustl_static"},
                        new Object[]{false, "stlport_static"},
                        new Object[]{true, "system"},
                        new Object[]{true, "c++_shared"},
                        new Object[]{true, "gnustl_shared"},
                        new Object[]{true, "stlport_shared"},
                        new Object[]{true, "c++_static"},
                        new Object[]{true, "gnustl_static"},
                        new Object[]{true, "stlport_static"});
    }

    public CmakeStlMatrixTest(boolean isModel, String stl) {
        this.isModel = isModel;
        this.stl = stl;
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
                        + "              cmake {\n"
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\");\n"
                        + "                cFlags.addAll(\"-DTEST_C_FLAG\", \"-DTEST_C_FLAG_2\")\n"
                        + "                cppFlags.addAll(\"-DTEST_CPP_FLAG\")\n"
                        + "                targets.addAll(\"hello-jni\")\n"
                        + "                arguments.addAll(\"-DANDROID_STL="
                        + stl
                        + "\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "          }\n"
                        + "        }\n"
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
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        TruthHelper.assertThatApk(apk).hasVersionCode(1);
        TruthHelper.assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(apk).contains("lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkApkContentWithInjectedABI() throws IOException, InterruptedException {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        TruthHelper.assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        TruthHelper.assertThatApk(apk).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkModel() throws IOException {
        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        TruthHelper.assertThat(model.getBuildSystems())
                .containsExactly(NativeBuildSystem.CMAKE.getName());
        TruthHelper.assertThat(model.getBuildFiles()).hasSize(1);
        TruthHelper.assertThat(model.getName()).isEqualTo("project");
        int abiCount = 3;
        TruthHelper.assertThat(model.getArtifacts()).hasSize(abiCount * 2);
        TruthHelper.assertThat(model.getFileExtensions()).hasSize(1);

        for (File file : model.getBuildFiles()) {
            TruthHelper.assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.getArtifacts()) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            TruthHelper.assertThat(pathElements).contains("obj");
            TruthHelper.assertThat(pathElements).doesNotContain("lib");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        TruthHelper.assertThat(model).hasArtifactGroupsNamed("debug", "release");
        TruthHelper.assertThat(model).hasArtifactGroupsOfSize(abiCount);
    }

    @Test
    public void checkClean() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        TruthHelper.assertThat(model).hasBuildOutputCountEqualTo(6);
        TruthHelper.assertThat(model).allBuildOutputsExist();
        // CMake .o files are kept in -B folder which is under .externalNativeBuild/
        TruthHelper.assertThat(model)
                .hasExactObjectFilesInExternalNativeBuildFolder("hello-jni.c.o");
        // CMake .so files are kept in -DCMAKE_LIBRARY_OUTPUT_DIRECTORY folder which is under build/
        if (stl.endsWith("_shared")) {
            TruthHelper.assertThat(model)
                    .hasExactSharedObjectFilesInBuildFolder("libhello-jni.so", "lib" + stl + ".so");
        } else {
            TruthHelper.assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
        }

        project.execute("clean");
        TruthHelper.assertThat(model).noBuildOutputsExist();
        TruthHelper.assertThat(model).hasExactObjectFilesInBuildFolder();
        TruthHelper.assertThat(model).hasExactSharedObjectFilesInBuildFolder();
    }

    @Test
    public void checkCleanAfterAbiSubset() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        TruthHelper.assertThat(model).hasBuildOutputCountEqualTo(6);

        List<File> allBuildOutputs = Lists.newArrayList();
        for (NativeArtifact artifact : model.getArtifacts()) {
            TruthHelper.assertThat(artifact.getOutputFile()).isFile();
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
                        + "              cmake {\n"
                        + "                abiFilters.clear();\n"
                        + "                abiFilters.addAll(\"x86\");\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + modelAfter
                        + "\n");
        project.execute("clean");

        // All build outputs should no longer exist, even the non-x86 outputs
        for (File output : allBuildOutputs) {
            TruthHelper.assertThat(output).doesNotExist();
        }
    }
}
