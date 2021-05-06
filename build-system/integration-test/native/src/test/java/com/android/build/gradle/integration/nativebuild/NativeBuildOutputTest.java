/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;
import static com.android.build.gradle.internal.cxx.configure.ConstantsKt.CXX_DEFAULT_CONFIGURATION_SUBFOLDER;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests expected build output */
public class NativeBuildOutputTest {

    private static String zeroLibraryCmakeLists =
            "cmake_minimum_required(VERSION 3.4.1)\n"
                    + "file(GLOB SRC src/main/cpp/hello-jni.cpp)\n"
                    + "set(CMAKE_VERBOSE_MAKEFILE ON)";

    private static String cmakeLists =
            "cmake_minimum_required(VERSION 3.4.1)\n"
                    + "file(GLOB SRC src/main/cpp/hello-jni.cpp)\n"
                    + "set(CMAKE_VERBOSE_MAKEFILE ON)\n"
                    + "add_library(hello-jni SHARED ${SRC})\n"
                    + "target_link_libraries(hello-jni log)";

    private static String androidMk =
            "LOCAL_PATH := $(call my-dir)\n"
                    + "include $(CLEAR_VARS)\n"
                    + "LOCAL_MODULE    := hello-jni\n"
                    + "LOCAL_SRC_FILES := hello-jni.cpp\n"
                    + "include $(BUILD_SHARED_LIBRARY)";

    @Rule public GradleTestProject project;

    public NativeBuildOutputTest() {
        project =
                GradleTestProject.builder()
                        .fromTestApp(
                                HelloWorldJniApp.builder()
                                        .withNativeDir("cpp")
                                        .useCppSource(true)
                                        .build())
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .create();
    }

    @Before
    public void setup() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "android.compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n");
    }

    @Test
    public void checkNdkBuildErrorInSourceCode() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'");
        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);
        Files.write(
                project.file("src/main/cpp/hello-jni.cpp").toPath(),
                ImmutableList.of("xx"),
                StandardCharsets.UTF_8);

        checkFailed(ImmutableList.of("'xx'"), ImmutableList.of());
    }

    @Test
    public void checkCMakeSTLWithUserDefinedOutputLocation() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.cmake.path 'CMakeLists.txt'\n"
                        + "    defaultConfig.externalNativeBuild.cmake {\n"
                        + "            arguments '-DANDROID_STL=c++_shared'\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(
                project.file("CMakeLists.txt"),
                cmakeLists
                        + "\n"
                        + "set_target_properties(hello-jni PROPERTIES LIBRARY_OUTPUT_DIRECTORY ../../../custom-output/debug/${ANDROID_ABI})");

        checkSucceeded(
                ImmutableList.of("external build set its own library output location"),
                ImmutableList.of(),
                true);

        Apk apk = project.getApk("debug");
        assertThat(apk).contains("lib/x86/libhello-jni.so");
        assertThat(apk).contains("lib/x86/libc++_shared.so");

        // Make sure the .so in the original location is still there.
        File original = project.getBuildFile().getParentFile();
        original =
                new File(
                        original,
                        CXX_DEFAULT_CONFIGURATION_SUBFOLDER
                                + "/custom-output/debug/arm64-v8a/libhello-jni.so");
        assertThat(original).isFile();
    }

    // Make sure that the STDOUT of ndk-build -n doesn't appear for the user.
    @Test
    public void checkNdkBuildNoDashNOutput() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n");

        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);

        checkSucceeded(ImmutableList.of(), ImmutableList.of("install", "rm -f"));
    }

    @Test
    public void checkCMakeErrorInSourceCode() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.cmake.path 'CMakeLists.txt'\n");

        TestFileUtils.appendToFile(project.file("CMakeLists.txt"), cmakeLists);
        Files.write(
                project.file("src/main/cpp/hello-jni.cpp").toPath(),
                ImmutableList.of("xx"),
                StandardCharsets.UTF_8);

        checkFailed(ImmutableList.of("'xx'"), ImmutableList.of());
    }

    // Related to b.android.com/219899 -- no libraries in CMake caused a NullReferenceException
    @Test
    public void checkCMakeNoLibraries() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.cmake.path 'CMakeLists.txt'\n");

        TestFileUtils.appendToFile(project.file("CMakeLists.txt"), zeroLibraryCmakeLists);
        checkSucceeded(ImmutableList.of(), ImmutableList.of());
    }

    @Test
    public void checkMissingCMakeLists() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.cmake.path 'non/existent/CMakeLists.txt'\n");

        checkFailed(
                ImmutableList.of(
                        FileUtils.toSystemDependentPath("non/existent/CMakeLists.txt"),
                        "doesn't exist"),
                ImmutableList.of("cmake.path", "CMakeLists.txt but that file doesn't exist"));
    }

    @Test
    public void checkNdkBuildUnrecognizedAbi() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n"
                        + "    defaultConfig.externalNativeBuild.ndkBuild {\n"
                        + "        abiFilters '-unrecognized-abi-' // <-- error\n"
                        + "    \n}"
                        + "}\n");

        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);

        checkFailed(
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["),
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["));
    }

    @Test
    public void checkUnrecognizedNdkAbi() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n"
                        + "    defaultConfig.ndk.abiFilters '-unrecognized-abi-' // <-- error\n"
                        + "}");
        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);

        checkFailed(
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["),
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["));
    }

    // In this test, ndk.abiFilters and ndkBuild.abiFilters only have "x86" in common.
    // Only "x86" should be built
    @Test
    public void checkNdkIntersectNativeBuild() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n"
                        + "    defaultConfig {\n"
                        + "      ndk.abiFilters 'armeabi-v7a', 'x86'\n"
                        + "      externalNativeBuild.ndkBuild.abiFilters 'x86_64', 'x86'\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);
    }

    // In this test, ndk.abiFilters and ndkBuild.abiFilters have nothing in common.
    // Nothing should be built
    @Test
    public void checkNdkEmptyIntersectNativeBuild() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n"
                        + "    defaultConfig {\n"
                        + "        ndk.abiFilters 'armeabi-v7a', 'x86_64'\n"
                        + "        externalNativeBuild.ndkBuild.abiFilters 'x86'\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);
        checkSucceeded(ImmutableList.of(), ImmutableList.of("x86"));
    }

    @Test
    public void checkCMakeUnrecognizedAbi() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.cmake.path 'src/main/cpp/CMakeLists.txt'\n"
                        + "    defaultConfig.externalNativeBuild.cmake {\n"
                        + "            abiFilters \"-unrecognized-abi-\" // <-- error\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(project.file("src/main/cpp/CMakeLists.txt"), cmakeLists);

        checkFailed(
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["),
                ImmutableList.of(
                        "ABIs [-unrecognized-abi-] are not supported for platform. Supported ABIs are ["));
    }

    @Test
    public void checkMissingAndroidMk() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.ndkBuild.path 'non/existent/Android.mk' "
                        + "// <-- error\n");

        checkFailed(
                ImmutableList.of(
                        FileUtils.toSystemDependentPath("non/existent/Android.mk"),
                        "doesn't exist"),
                ImmutableList.of("ndkBuild.path", "Android.mk but that file doesn't exist"));
    }

    @Test
    public void checkNdkBuildUnrecognizedTarget() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.ndkBuild.path 'src/main/cpp/Android.mk'\n"
                        + "    defaultConfig.externalNativeBuild.ndkBuild {\n"
                        + "                targets '-unrecognized-target-' // <-- error\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(project.file("src/main/cpp/Android.mk"), androidMk);

        checkFailed(
                ImmutableList.of(
                        "Unexpected native build target -unrecognized-target-",
                        "Valid values are: hello-jni"),
                ImmutableList.of());
    }

    @Test
    public void checkCMakeWrongTarget() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.cmake.path 'CMakeLists.txt'\n"
                        + "    defaultConfig.externalNativeBuild.cmake {\n"
                        + "            targets \"-unrecognized-target-\" // <-- error\n"
                        + "    }\n"
                        + "}");

        TestFileUtils.appendToFile(project.file("CMakeLists.txt"), cmakeLists);

        checkFailed(
                ImmutableList.of(
                        "Unexpected native build target -unrecognized-target-",
                        "Valid values are: hello-jni"),
                ImmutableList.of());
    }

    @Test
    public void checkCMakeExternalLib() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    externalNativeBuild.cmake.path 'CMakeLists.txt'\n"
                        + "    defaultConfig.externalNativeBuild.cmake.abiFilters 'x86_64'\n"
                        + "}");

        // CMakeLists.txt that references an external library. The library doesn't exist but that
        // doesn't really matter since we're only testing that the resulting model looks right.
        TestFileUtils.appendToFile(
                project.file("CMakeLists.txt"),
                "cmake_minimum_required(VERSION 3.4.1)\n"
                        + "add_library(lib_gmath STATIC IMPORTED )\n"
                        + "set_target_properties(lib_gmath PROPERTIES IMPORTED_LOCATION\n"
                        + "    ./gmath/lib/${ANDROID_ABI}/libgmath.a)\n"
                        + "file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)\n"
                        + "message(${SRC})\n"
                        + "set(CMAKE_VERBOSE_MAKEFILE ON)\n"
                        + "add_library(hello-jni SHARED ${SRC})\n"
                        + "target_link_libraries(hello-jni log)\n");
        Files.write(
                project.file("src/main/cpp/hello-jni.cpp").toPath(),
                ImmutableList.of("void main() {}"),
                StandardCharsets.UTF_8);

        ModelBuilderV2.FetchResult<ModelContainerV2> result =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(ImmutableList.of(), ImmutableList.of()));
        for (Map<String, ModelContainerV2.ModelInfo> map :
                result.getContainer().getInfoMaps().values()) {
            for (ModelContainerV2.ModelInfo modelInfo : map.values()) {
                assertThat(modelInfo.getIssues().getSyncIssues()).isEmpty();
            }
        }
        assertThat(dump(result))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      - debug:\n"
                                + "         - abis:\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "      - release:\n"
                                + "         - abis:\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = CMAKE\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                + "< NativeModule");
    }

    @Test
    public void checkCMakeBuildOutput() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.externalNativeBuild.cmake.path 'CMakeLists.txt'\n");

        TestFileUtils.appendToFile(project.file("CMakeLists.txt"), cmakeLists);

        checkSucceeded(
                ImmutableList.of(),
                ImmutableList.of(
                        "Building CXX",
                        FileUtils.toSystemIndependentPath(FileUtils.join("cpp", "hello-jni.cpp")),
                        FileUtils.join("x86", "libhello-jni.so")));
    }

    private void checkSucceeded(List<String> expectInStdout, List<String> dontExpectInStdout)
            throws IOException, InterruptedException {
        checkSucceeded(expectInStdout, dontExpectInStdout, false);
    }

    private void checkSucceeded(
            List<String> expectInStdout, List<String> dontExpectInStdout, boolean enableInfoLogging)
            throws IOException, InterruptedException {
        // Check the build
        GradleBuildResult result =
                project.executor().withEnableInfoLogging(enableInfoLogging).run("assembleDebug");

        try (Scanner stdout = result.getStdout()) {
            ScannerSubject scannerSubject = ScannerSubject.assertThat(stdout);
            for (String expect : expectInStdout) {
                scannerSubject.contains(expect);
            }
            for (String dontExpect : dontExpectInStdout) {
                scannerSubject.doesNotContain(dontExpect);
            }
        }
    }

    private void checkFailed(List<String> expectInStderr, List<String> expectInExceptionRootCause)
            throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .expectFailure()
                        .withEnableInfoLogging(false)
                        .run("assembleDebug");
        for (String expect : expectInStderr) {
            try (Scanner stderr = result.getStderr()) {
                ScannerSubject.assertThat(stderr).contains(expect);
            }
        }

        String rootCause = Throwables.getRootCause(result.getException()).getMessage();
        for (String trace : expectInExceptionRootCause) {
            assertThat(rootCause).contains(trace);
        }
    }
}
