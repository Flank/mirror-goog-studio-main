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
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.dump;
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.readAsFileIndex;
import static com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.builder.model.v2.models.ndk.NativeVariant;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
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

    private final String stl;
    private final boolean useV2NativeModel;
    @Rule
    public GradleTestProject project;

    @Parameterized.Parameters(name = "stl={0} useV2NativeModel={1}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(
                new Object[]{"c++_shared", false}, new Object[]{"c++_static", false},
                new Object[]{"c++_shared", true}, new Object[]{"c++_static", true});
    }

    public CmakeStlMatrixTest(String stl, boolean useV2NativeModel) {
        this.stl = stl;
        this.useV2NativeModel = useV2NativeModel;
        project =
                GradleTestProject.builder()
                        .fromTestApp(
                                HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
                        // TODO(159233213) Turn to ON when release configuration is cacheable
                        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .addGradleProperties(
                                BooleanOption.ENABLE_V2_NATIVE_MODEL.getPropertyName()
                                        + "="
                                        + useV2NativeModel)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
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
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"x86_64\");\n"
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
                        + "\n");
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

    @Test
    public void checkApkContent() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();

        lib = ZipHelper.extractFile(apk, "lib/x86_64/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkApkContentWithInjectedABI() throws IOException, InterruptedException {
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/x86_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/x86_64/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void checkModel() throws IOException {
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> result =
                    project.modelV2().fetchNativeModules(ImmutableList.of(), ImmutableList.of());
            assertThat(dump(result))
                    .isEqualTo(
                            "[:]\n"
                                    + "> NativeModule:\n"
                                    + "    - name                    = \"project\"\n"
                                    + "    > variants:\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"debug\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{!}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{!}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{!}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{!}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = CMAKE\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                    + "< NativeModule"
                    );
        } else {
            project.model()
                    .fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
            NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
            assertThat(model.getBuildSystems()).containsExactly(NativeBuildSystem.CMAKE.getTag());
            assertThat(model).hasExactBuildFilesShortNames("CMakeLists.txt");
            assertThat(model.getName()).isEqualTo("project");
            int abiCount = 2;
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
            TruthHelper.assertThat(model).hasVariantInfoBuildFolderForEachAbi();
        }
    }

    @Test
    public void checkClean() throws IOException {
        NativeAndroidProject model = null;
        NativeModule nativeModule = null;
        project.execute("clean", "assembleDebug", "assembleRelease");
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> result =
                    project.modelV2().fetchNativeModules(ImmutableList.of(), ImmutableList.of());
            assertThat(dump(result))
                    .isEqualTo(
                            "[:]\n"
                                    + "> NativeModule:\n"
                                    + "    - name                    = \"project\"\n"
                                    + "    > variants:\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"debug\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/compile_commands.json{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/debug/x86_64/compile_commands.json{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/debug/x86_64/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/debug/x86_64/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/armeabi-v7a/compile_commands.json{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/cmake/release/x86_64/compile_commands.json{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/cmake/release/x86_64/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/cmake/release/x86_64/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = CMAKE\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                    + "< NativeModule"
                    );

            nativeModule = result.getContainer().getSingleModel();
            for (NativeVariant variant : nativeModule.getVariants()) {
                for (NativeAbi abi : variant.getAbis()) {
                    for (File soFolder : readAsFileIndex(abi.getSymbolFolderIndexFile())) {
                        if (stl.endsWith("_shared")) {
                            assertThat(Arrays.asList(soFolder.list()))
                                    .containsExactly("libhello-jni.so", "lib" + stl + ".so");
                        } else {
                            assertThat(Arrays.asList(soFolder.list()))
                                    .containsExactly("libhello-jni.so");
                        }
                    }
                }
            }
        } else {
            model = project.model().fetch(NativeAndroidProject.class);
            assertThat(model).hasBuildOutputCountEqualTo(4);
            assertThat(model).allBuildOutputsExist();
            // CMake .o files are kept in -B folder which is under .externalNativeBuild/
            assertThat(model).hasExactObjectFilesInCxxFolder("hello-jni.c.o");
            // CMake .so files are kept in -DCMAKE_LIBRARY_OUTPUT_DIRECTORY folder which is under
            // build/
            if (stl.endsWith("_shared")) {
                assertThat(model)
                        .hasExactSharedObjectFilesInBuildFolder(
                                "libhello-jni.so", "lib" + stl + ".so");
            } else {
                assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
            }
        }

        project.execute("clean");

        if (useV2NativeModel) {
            for (NativeVariant variant : nativeModule.getVariants()) {
                for (NativeAbi abi : variant.getAbis()) {
                    for (File soFolder : readAsFileIndex(abi.getSymbolFolderIndexFile())) {
                        assertThat(soFolder).doesNotExist();
                    }
                }
            }
        } else {
            assertThat(model).noBuildOutputsExist();
            assertThat(model).hasExactObjectFilesInBuildFolder();
            assertThat(model).hasExactSharedObjectFilesInBuildFolder();
        }
    }
}
