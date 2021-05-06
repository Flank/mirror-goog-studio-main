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
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeVariant;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for ndkJniLib. */
public class NdkBuildJniLibTest {

    private static final TestSourceFile androidMk =
            new TestSourceFile(
                    "lib/src/main/jni",
                    "Android.mk",
                    "LOCAL_PATH := $(call my-dir)\n"
                            + "\n"
                            + "include $(CLEAR_VARS)\n"
                            + "\n"
                            + "LOCAL_MODULE    := hello-jni\n"
                            + "LOCAL_SRC_FILES := hello-jni.c\n"
                            + "\n"
                            + "include $(BUILD_SHARED_LIBRARY)");

    @Rule public final GradleTestProject project;

    public NdkBuildJniLibTest() {
        project =
                GradleTestProject.builder()
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .fromTestProject("ndkJniLib")
                        .addFile(androidMk)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        new File(project.getProjectDir(), "lib/src/main/jni")
                .renameTo(new File(project.getProjectDir(), "lib/src/main/cxx"));

        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 21\n"
                        + "    }\n"
                        + "    externalNativeBuild {\n"
                        + "      ndkBuild {\n"
                        + "        path \"src/main/cxx/Android.mk\"\n"
                        + "      }\n"
                        + "    }\n"
                        + "}\n");
        project.execute(
                "clean", "assembleDebug", "generateJsonModelDebug", "generateJsonModelRelease");
    }

    @Test
    public void checkVersionCode() {
        GradleTestProject app = project.getSubproject("app");
        assertThatApk(app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1000123);
        assertThatApk(app.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1100123);
        assertThatApk(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .hasVersionCode(1300123);
        assertThatApk(app.getApk("universal", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .hasVersionCode(2000123);
        assertThatApk(
                        app.getApk(
                                "armeabi-v7a", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .hasVersionCode(2100123);
        assertThatApk(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .hasVersionCode(2300123);
    }

    @Test
    public void checkApkContent() {
        GradleTestProject app = project.getSubproject("app");

        assertThatApk(app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(
                        app.getApk(
                                "armeabi-v7a", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("x86", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() {
        ModelBuilderV2.FetchResult<ModelContainerV2> fetchResult =
                project.modelV2().fetchNativeModules(new NativeModuleParams());
        Truth.assertThat(dump(fetchResult))
                .isEqualTo(
                        "[:app]\n"
                                + "[:lib]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"lib\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{DEBUG}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{DEBUG}/x86/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{RELEASE}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{RELEASE}/x86/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/lib/build/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = NDK_BUILD\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/lib/src/main/cxx/Android.mk{F}\n"
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
                            "sourceFile: {PROJECT}/lib/src/main/cxx/hello-jni.c{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang{F}\n"
                                    + "workingDir: {PROJECT}/lib{D}\n"
                                    + "flags:      [-target, i686-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/lib/src/main/cxx, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            assertThat(
                            dumpCompileCommandsJsonBin(
                                    debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer()))
                    .isEqualTo(
                            "sourceFile: {PROJECT}/lib/src/main/cxx/hello-jni.c{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe{F}\n"
                                    + "workingDir: {PROJECT}/lib{D}\n"
                                    + "flags:      [-target, i686-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/lib/src/main/cxx, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
        }
    }
}
