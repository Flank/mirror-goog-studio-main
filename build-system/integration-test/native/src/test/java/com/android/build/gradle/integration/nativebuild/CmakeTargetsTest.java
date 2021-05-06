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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for CMake with targets clause. */
public class CmakeTargetsTest {
    @Rule public GradleTestProject project;

    public CmakeTargetsTest() {
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cpp").build())
                        .addFile(HelloWorldJniApp.cmakeListsMultiModule("."))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library1", "library1.cpp"))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library2", "library2.cpp"))
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
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
                        + "      cmake {\n"
                        + "        path \"CMakeLists.txt\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
    }

    @Test
    public void checkMultiTargets() {
        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86/liblibrary1.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        checkV2Model();
    }

    @Test
    public void checkSingleTarget() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "      externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            targets.addAll(\"library2\")\n"
                        + "          }\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n");

        project.execute("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86/liblibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/liblibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86/liblibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/liblibrary2.so");

        checkV2Model();
    }

    private void checkV2Model() {
        ModelBuilderV2.FetchResult<ModelContainerV2> result =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(ImmutableList.of(), ImmutableList.of()));
        assertThat(dump(result))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{F}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{F}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/additional_project_files.txt{F}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{F}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/arm64-v8a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = CMAKE\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                + "< NativeModule");
    }
}
