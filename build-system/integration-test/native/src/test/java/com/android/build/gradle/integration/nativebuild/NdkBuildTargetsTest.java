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
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.dumpCompileCommandsJsonBin;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeVariant;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for ndk-build with targets clause. */
public class NdkBuildTargetsTest {

    @Rule public final GradleTestProject project;

    public NdkBuildTargetsTest() {
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cpp").build())
                        .addFile(HelloWorldJniApp.androidMkMultiModule("src/main/cpp"))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library1", "library1.cpp"))
                        .addFile(
                                HelloWorldJniApp.libraryCpp(
                                        "src/main/cpp/library2", "library2.cpp"))
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
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
                        + "    defaultConfig {\n"
                        + "      externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "          arguments.addAll(\"NDK_TOOLCHAIN_VERSION:=clang\")\n"
                        + "          cFlags.addAll(\"-DTEST_C_FLAG\", \"-DTEST_C_FLAG_2\")\n"
                        + "          cppFlags.addAll(\"-DTEST_CPP_FLAG\")\n"
                        + "          abiFilters.addAll(\"armeabi-v7a\", \"x86\", \"x86_64\")\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "    externalNativeBuild {\n"
                        + "      ndkBuild {\n"
                        + "        path \"src/main/cpp/Android.mk\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n"
                        + "\n");
    }

    @Test
    public void checkSingleTarget() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    defaultConfig {\n"
                        + "      externalNativeBuild {\n"
                        + "          ndkBuild {\n"
                        + "            targets.addAll(\"mylibrary2\")\n"
                        + "          }\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n");

        project.executor().run("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        // These were filtered out because they weren't in ndkBuild.targets
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libmylibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86/libmylibrary1.so");
        assertThatApk(apk).doesNotContain("lib/x86_64/libmylibrary1.so");
        // These weren't filtered out because they were in ndkBuild.targets
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary2.so");

        assertV2Model();
    }

    @Test
    public void checkMultiTargets() throws IOException, InterruptedException {
        project.executor().run("clean", "assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary1.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary1.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary1.so");
        assertThatApk(apk).contains("lib/armeabi-v7a/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86/libmylibrary2.so");
        assertThatApk(apk).contains("lib/x86_64/libmylibrary2.so");

        project.model().fetchAndroidProjectsAllowSyncIssues();

        assertV2Model();
    }

    private void assertV2Model() {
        ModelBuilderV2.FetchResult<ModelContainerV2> fetchResult =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(ImmutableList.of(), ImmutableList.of()));
        Truth.assertThat(dump(fetchResult))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/x86/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/x86/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = NDK_BUILD\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/src/main/cpp/Android.mk{F}\n"
                                + "< NativeModule");
        NativeVariant debugVariant =
                fetchResult.getContainer().getSingleNativeModule().getVariants().stream()
                        .filter(variant -> variant.getName().equals("debug"))
                        .findFirst()
                        .get();
        NativeAbi debugX86Abi =
                debugVariant.getAbis().stream()
                        .filter(abi -> abi.getName().equals("x86"))
                        .findFirst()
                        .get();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            assertThat(
                            dumpCompileCommandsJsonBin(
                                    debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer()))
                    .isEqualTo(
                            "sourceFile: {PROJECT}/src/main/cpp/library1/library1.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library1, -I{PROJECT}/src/main/cpp/library1, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]\n"
                                    + "\n"
                                    + "sourceFile: {PROJECT}/src/main/cpp/library2/library2.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library2, -I{PROJECT}/src/main/cpp/library2, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            String dump =
                    dumpCompileCommandsJsonBin(
                            debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer());
            assertThat(dump)
                    .isEqualTo(
                            "sourceFile: {PROJECT}/src/main/cpp/library1/library1.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library1, -I{PROJECT}/src/main/cpp/library1, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]\n"
                                    + "\n"
                                    + "sourceFile: {PROJECT}/src/main/cpp/library2/library2.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library2, -I{PROJECT}/src/main/cpp/library2, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            assertThat(
                            dumpCompileCommandsJsonBin(
                                    debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer()))
                    .isEqualTo(
                            "sourceFile: {PROJECT}/src/main/cpp/library1/library1.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library1, -I{PROJECT}/src/main/cpp/library1, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]\n"
                                    + "\n"
                                    + "sourceFile: {PROJECT}/src/main/cpp/library2/library2.cpp{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, i686-none-linux-android16, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fno-exceptions, -fno-rtti, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/cpp/library2, -I{PROJECT}/src/main/cpp/library2, -DTEST_C_FLAG, -DTEST_C_FLAG_2, -DTEST_CPP_FLAG, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
        }
    }
}
