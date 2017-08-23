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

import com.android.build.gradle.integration.common.fixture.BuildScriptGenerator;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
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
        return ImmutableList.of(new Object[] {false}, new Object[] {true});
    }

    public NdkBuildTargetsTest(boolean isModel) {
        this.isModel = isModel;
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                new BuildScriptGenerator(
                                "apply plugin: '${application_plugin}'\n"
                                        + "\n"
                                        + "${model_start}\n"
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
                                        + "${model_end}\n"
                                        + "\n")
                        .build(isModel));
    }

    @Test
    public void checkSingleTarget() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                new BuildScriptGenerator(
                                "${model_start}\n"
                                        + "    android {\n"
                                        + "        defaultConfig {\n"
                                        + "          externalNativeBuild {\n"
                                        + "              ndkBuild {\n"
                                        + "                targets.addAll(\"mylibrary2\")\n"
                                        + "              }\n"
                                        + "          }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "${model_end}\n")
                        .build(isModel));

        project.executor().run("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        // These were filtered out because they weren't in ndkBuild.targets
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libmylibrary1.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libmylibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86/libmylibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/libmylibrary1.so");
        // These weren't filtered out because they were in ndkBuild.targets
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary2.so");
        assertThatApk(apk).contains("lib/armeabi/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary2.so");

        project.model().getSingle(); // Make sure we can successfully get AndroidProject
        assertModel(project.model().getSingle(NativeAndroidProject.class));
    }

    @Test
    public void checkMultiTargets() throws IOException, InterruptedException {
        project.executor().run("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary1.so");
        assertThatApk(apk).contains("lib/armeabi/libmylibrary1.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary1.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary2.so");
        assertThatApk(apk).contains("lib/armeabi/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary2.so");

        project.model().getSingle(); // Make sure we can successfully get AndroidProject

        assertModel(project.model().getSingle(NativeAndroidProject.class));
    }

    private static void assertModel(NativeAndroidProject model) throws IOException {
        assertThat(model).isNotNull();
        assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.NDK_BUILD.getName());
        assertThat(model.getBuildFiles()).hasSize(1);
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts()).hasSize(16);
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
        assertThat(model).hasArtifactGroupsOfSize(8);
    }
}
