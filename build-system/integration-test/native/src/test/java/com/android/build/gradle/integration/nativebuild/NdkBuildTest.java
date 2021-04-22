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
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.getSoFolderFor;
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.readAsFileIndex;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeVariant;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for ndk-build. */
public class NdkBuildTest {

    @Rule public final GradleTestProject project;

    public NdkBuildTest() {
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().build())
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
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
                        + "              ndkBuild {\n"
                        + "                abiFilters.addAll(\"armeabi-v7a\", \"arm64-v8a\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "        externalNativeBuild {\n"
                        + "          ndkBuild {\n"
                        + "            path \"src/main/jni/Android.mk\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    android.packagingOptions {\n"
                        + "        doNotStrip \"*/armeabi-v7a/libhello-jni.so\"\n"
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
    public void apk() throws IOException, InterruptedException {
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).hasVersionCode(1);
        assertThatApk(apk).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).contains("lib/arm64-v8a/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isNotStripped();

        lib = ZipHelper.extractFile(apk, "lib/arm64-v8a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();
    }

    @Test
    public void injectedAbi() throws IOException, InterruptedException {
        // Pass invalid-abi, arm64-v8a and armeabi. The first (invalid-abi) should be ignored because
        // it is not valid for the build . The second (arm64-v8a) should be the one chosen to build.
        // Finally, armeabi is valid but it will be ignored because arm64-v8a is "preferred".
        project.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "invalid-abi,arm64-v8a,armeabi")
                .run("clean", "assembleDebug");
        Apk apk = project.getApk("debug");
        assertThatApk(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/armeabi/libhello-jni.so");
        assertThatApk(apk).contains("lib/arm64-v8a/libhello-jni.so");
        assertThatApk(apk).doesNotContain("lib/arm64-v8a_64/libhello-jni.so");

        File lib = ZipHelper.extractFile(apk, "lib/arm64-v8a/libhello-jni.so");
        TruthHelper.assertThatNativeLib(lib).isStripped();

        assertThat(project.file("build/intermediates/packaged_manifests/debug/AndroidManifest.xml"))
                .contains("android:testOnly=\"true\"");
    }

    @Test
    public void model() {
        ModelBuilderV2.FetchResult<ModelContainerV2> fetchResult =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(
                                        ImmutableList.of("debug"), ImmutableList.of("arm64-v8a")));
        assertThat(dump(fetchResult))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/arm64-v8a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = NDK_BUILD\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                + "< NativeModule");
        NativeVariant debugVariant =
                fetchResult.getContainer().getSingleNativeModule().getVariants().stream()
                        .filter(variant -> variant.getName().equals("debug"))
                        .findFirst()
                        .get();
        NativeAbi debugX86Abi =
                debugVariant.getAbis().stream()
                        .filter(abi -> abi.getName().equals("arm64-v8a"))
                        .findFirst()
                        .get();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            assertThat(
                            dumpCompileCommandsJsonBin(
                                    debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer()))
                    .isEqualTo(
                            "sourceFile: {PROJECT}/src/main/jni/hello-jni.c{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, aarch64-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fpic, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/jni, -DANDROID, -Wformat, -Werror=format-security]");
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            assertThat(
                            dumpCompileCommandsJsonBin(
                                    debugX86Abi.getSourceFlagsFile(), fetchResult.getNormalizer()))
                    .isEqualTo(
                            "sourceFile: {PROJECT}/src/main/jni/hello-jni.c{F}\n"
                                    + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe{F}\n"
                                    + "workingDir: {PROJECT}/{D}\n"
                                    + "flags:      [-target, aarch64-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fpic, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/jni, -DANDROID, -Wformat, -Werror=format-security]");
        }
        File soFolder = getSoFolderFor(project, Abi.ARM64_V8A);
        assertThat(readAsFileIndex(debugX86Abi.getSymbolFolderIndexFile()))
                .containsExactly(soFolder);
        assertThat(
                        readAsFileIndex(
                                debugX86Abi.getBuildFileIndexFile(), fetchResult.getNormalizer()))
                .containsExactly("{PROJECT}/src/main/jni/Android.mk{F}");
    }

    @Test
    public void clean() throws Exception {
        project.executor()
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .run("clean", "assembleDebug", "assembleRelease");
        ModelBuilderV2.FetchResult<ModelContainerV2> fetchResult =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(ImmutableList.of(), ImmutableList.of()));
        assertThat(dump(fetchResult))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = NDK_BUILD\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                + "< NativeModule");
        NativeVariant debugVariant =
                fetchResult.getContainer().getSingleNativeModule().getVariants().stream()
                        .filter(variant -> variant.getName().equals("debug"))
                        .findFirst()
                        .get();
        NativeAbi debugX86Abi =
                debugVariant.getAbis().stream()
                        .filter(abi -> abi.getName().equals("arm64-v8a"))
                        .findFirst()
                        .get();
        File soFolder = readAsFileIndex(debugX86Abi.getSymbolFolderIndexFile()).get(0);
        assertThat(Arrays.asList(soFolder.list())).contains("libhello-jni.so");
        project.execute("clean");
        assertThat(soFolder).doesNotExist();
    }

    @Test
    public void abiSubset() throws Exception {
        List<File> allBuildOutputs = Lists.newArrayList();
        project.executor()
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .run("clean", "assembleDebug", "assembleRelease");
        ModelBuilderV2.FetchResult<ModelContainerV2> fetchResult =
                project.modelV2()
                        .fetchNativeModules(
                                new NativeModuleParams(ImmutableList.of(), ImmutableList.of()));
        assertThat(dump(fetchResult))
                .isEqualTo(
                        "[:]\n"
                                + "> NativeModule:\n"
                                + "   - name                    = \"project\"\n"
                                + "   > variants:\n"
                                + "      > debug:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - arm64-v8a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/build/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = NDK_BUILD\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                + "< NativeModule");
        fetchResult.getContainer().getSingleNativeModule().getVariants().stream()
                .flatMap(variant -> variant.getAbis().stream())
                .flatMap(abi -> readAsFileIndex(abi.getSymbolFolderIndexFile()).stream())
                .flatMap(folder -> Arrays.stream(folder.listFiles()))
                .forEach(allBuildOutputs::add);

        // Change the build file to only have "arm64-v8a"
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "    android {\n"
                        + "        defaultConfig {\n"
                        + "          externalNativeBuild {\n"
                        + "              ndkBuild {\n"
                        + "                abiFilters.clear();\n"
                        + "                abiFilters.addAll(\"arm64-v8a\")\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n");
        project.execute("clean");

        // All build outputs should no longer exist, even the non-arm64-v8a outputs
        for (File output : allBuildOutputs) {
            assertThat(output).doesNotExist();
        }
    }
}
