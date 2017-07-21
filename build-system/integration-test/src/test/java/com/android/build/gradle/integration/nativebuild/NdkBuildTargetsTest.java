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
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.google.common.collect.ArrayListMultimap;
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

/** Assemble tests for ndk-build with targets clause. */
@RunWith(Parameterized.class)
public class NdkBuildTargetsTest {

    private boolean isModel;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cpp").build())
                    .addFile(HelloWorldJniApp.androidMkMultiModule("src/main/cpp"))
                    .addFile(HelloWorldJniApp.libraryCpp("src/main/cpp/library1", "library1.cpp"))
                    .addFile(HelloWorldJniApp.libraryCpp("src/main/cpp/library2", "library2.cpp"))
                    .useExperimentalGradleVersion(isModel)
                    .create();

    @Parameterized.Parameters(name = "model = {0}")
    public static Collection<Object[]> data() {
        return new ArrayList<Object[]>(
                Arrays.asList(
                        new ArrayList<Boolean>(Arrays.asList(false)).toArray(),
                        new ArrayList<Boolean>(Arrays.asList(true)).toArray()));
    }

    public NdkBuildTargetsTest(boolean isModel) {
        this.isModel = isModel;
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
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
                        + "            ndkBuild {\n"
                        + "              arguments.addAll(\"NDK_TOOLCHAIN_VERSION:=clang\")\n"
                        + "              targets.addAll(\"mylibrary2\")\n"
                        + "              cFlags.addAll(\"-DTEST_C_FLAG\", \"-DTEST_C_FLAG_2\")\n"
                        + "              cppFlags.addAll(\"-DTEST_CPP_FLAG\")\n"
                        + "              abiFilters.addAll(\"armeabi-v7a\", \"armeabi\", \"x86\", \"x86_64\")\n"
                        + "            }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          ndkBuild {\n"
                        + "            path \"src/main/cpp/Android.mk\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + modelAfter
                        + "\n");
        project.execute("clean", "assembleDebug");
    }

    @Test
    public void checkApkContent() throws IOException {
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasVersionCode(1);
        // These were filtered out because they weren't in ndkBuild.targets
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("lib/armeabi-v7a/libmylibrary1.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("lib/armeabi/libmylibrary1.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("lib/x86/libmylibrary1.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContain("lib/x86_64/libmylibrary1.so");
        // These weren't filtered out because they were in ndkBuild.targets
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/armeabi-v7a/libmylibrary2.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/armeabi/libmylibrary2.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/x86/libmylibrary2.so");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .contains("lib/x86_64/libmylibrary2.so");
    }

    @Test
    public void checkModel() throws IOException {
        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        TruthHelper.assertThat(model).isNotNull();
        TruthHelper.assertThat(model.getBuildSystems())
                .containsExactly(NativeBuildSystem.NDK_BUILD.getName());
        TruthHelper.assertThat(model.getBuildFiles()).hasSize(1);
        TruthHelper.assertThat(model.getName()).isEqualTo("project");
        // All targets are present in the model, even those not specified in ndkBuild.targets.
        // This is so the user can view, edit, and navigate between targets in Android Studio.
        TruthHelper.assertThat(model.getArtifacts()).hasSize(16);
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
        TruthHelper.assertThat(model).hasArtifactGroupsOfSize(8);
    }
}
