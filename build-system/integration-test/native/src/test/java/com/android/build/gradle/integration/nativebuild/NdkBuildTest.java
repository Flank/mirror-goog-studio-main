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
import static com.android.build.gradle.integration.common.fixture.model.NativeUtilsKt.readAsFileIndex;
import static com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
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
import com.google.common.collect.Lists;
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

/** Assemble tests for ndk-build. */
@RunWith(Parameterized.class)
public class NdkBuildTest {

    @Rule public final GradleTestProject project;
    private final boolean useV2NativeModel;

    public NdkBuildTest(boolean useV2NativeModel) {
        project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldJniApp.builder().build())
                        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
                        .addGradleProperties(
                                BooleanOption.ENABLE_V2_NATIVE_MODEL.getPropertyName()
                                        + "="
                                        + useV2NativeModel)
                        .create();
        this.useV2NativeModel = useV2NativeModel;
    }

    @Parameterized.Parameters(name = "useV2NativeModel={0}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(new Object[] {false}, new Object[] {true});
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
    public void model() throws IOException {
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> fetchResult =
                    project.modelV2()
                            .fetchNativeModules(
                                    ImmutableList.of("debug"), ImmutableList.of("arm64-v8a"));
            assertThat(dump(fetchResult))
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
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt{!}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/compile_commands.json.bin{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/build_file_index.txt{!}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/compile_commands.json.bin{!}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/symbol_folder_index.txt{!}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/build_file_index.txt{!}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = NDK_BUILD\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                    + "< NativeModule");
            NativeVariant debugVariant =
                    fetchResult.getContainer().getSingleModel().getVariants().stream()
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
                                debugX86Abi.getSourceFlagsFile(),
                                fetchResult.getNormalizer()))
                        .isEqualTo(
                                "sourceFile: {PROJECT}/src/main/jni/hello-jni.c{F}\n"
                                        + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang{F}\n"
                                        + "workingDir: {PROJECT}/{D}\n"
                                        + "flags:      [-target, aarch64-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fpic, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/jni, -DANDROID, -Wformat, -Werror=format-security]");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                assertThat(
                        dumpCompileCommandsJsonBin(
                                debugX86Abi.getSourceFlagsFile(),
                                fetchResult.getNormalizer()))
                        .isEqualTo(
                                "sourceFile: {PROJECT}/src/main/jni/hello-jni.c{F}\n"
                                        + "compiler:   {ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe{F}\n"
                                        + "workingDir: {PROJECT}/{D}\n"
                                        + "flags:      [-target, aarch64-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {ANDROID_NDK}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fpic, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/src/main/jni, -DANDROID, -Wformat, -Werror=format-security]");
            }
            assertThat(
                    readAsFileIndex(
                            debugX86Abi.getSymbolFolderIndexFile(),
                            fetchResult.getNormalizer()))
                    .containsExactly(
                            "{PROJECT}/build/intermediates/ndkBuild/debug/obj/local/arm64-v8a{!}");
            assertThat(
                    readAsFileIndex(
                            debugX86Abi.getBuildFileIndexFile(),
                            fetchResult.getNormalizer()))
                    .containsExactly("{PROJECT}/src/main/jni/Android.mk{F}");
        } else {
            project.model()
                    .fetchAndroidProjects(); // Make sure we can successfully get AndroidProject
            NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
            assertThat(model.getBuildSystems())
                    .containsExactly(NativeBuildSystem.NDK_BUILD.getTag());
            assertThat(model.getBuildFiles()).hasSize(1);
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
        }
    }

    @Test
    public void clean() throws IOException {
        project.execute("clean", "assembleDebug", "assembleRelease");
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> fetchResult =
                    project.modelV2().fetchNativeModules(ImmutableList.of(), ImmutableList.of());
            assertThat(dump(fetchResult))
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
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = NDK_BUILD\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                    + "< NativeModule");
            NativeVariant debugVariant =
                    fetchResult.getContainer().getSingleModel().getVariants().stream()
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
        } else {
            NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
            assertThat(model).hasBuildOutputCountEqualTo(4);
            assertThat(model).allBuildOutputsExist();
            assertThat(model).hasExactObjectFilesInBuildFolder("hello-jni.o");
            assertThat(model).hasExactSharedObjectFilesInBuildFolder("libhello-jni.so");
            project.execute("clean");
            assertThat(model).noBuildOutputsExist();
            assertThat(model).hasExactObjectFilesInBuildFolder();
            assertThat(model).hasExactSharedObjectFilesInBuildFolder();
        }
    }

    @Test
    public void abiSubset() throws IOException {
        List<File> allBuildOutputs = Lists.newArrayList();
        project.execute("clean", "assembleDebug", "assembleRelease");
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> fetchResult =
                    project.modelV2().fetchNativeModules(ImmutableList.of(), ImmutableList.of());
            assertThat(dump(fetchResult))
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
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/debug/arm64-v8a/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/.cxx/ndkBuild/release/arm64-v8a/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = NDK_BUILD\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/src/main/jni/Android.mk{F}\n"
                                    + "< NativeModule");
            fetchResult.getContainer().getSingleModel().getVariants().stream()
                    .flatMap(variant -> variant.getAbis().stream())
                    .flatMap(abi -> readAsFileIndex(abi.getSymbolFolderIndexFile()).stream())
                    .flatMap(folder -> Arrays.stream(folder.listFiles()))
                    .forEach(allBuildOutputs::add);
        } else {
            NativeAndroidProject model = project.model().fetch(NativeAndroidProject.class);
            assertThat(model).hasBuildOutputCountEqualTo(4);

            for (NativeArtifact artifact : model.getArtifacts()) {
                assertThat(artifact.getOutputFile()).isFile();
                allBuildOutputs.add(artifact.getOutputFile());
            }
        }

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
