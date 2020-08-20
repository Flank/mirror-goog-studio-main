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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.builder.model.v2.models.ndk.NativeVariant;
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

/** Assemble tests for ndkJniLib. */
@RunWith(Parameterized.class)
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
    private final boolean useV2NativeModel;

    public NdkBuildJniLibTest(boolean useV2NativeModel) {
        project =
                GradleTestProject.builder()
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .fromTestProject("ndkJniLib")
                        .addFile(androidMk)
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
        new File(project.getTestDir(), "lib/src/main/jni")
                .renameTo(new File(project.getTestDir(), "lib/src/main/cxx"));
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
    public void checkModel() throws IOException {
        if (useV2NativeModel) {
            ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> fetchResult =
                    project.modelV2().fetchNativeModules(null, null);
            assertThat(dump(fetchResult))
                    .isEqualTo(
                            "[:lib]\n"
                                    + "> NativeModule:\n"
                                    + "    - name                    = \"lib\"\n"
                                    + "    > variants:\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"debug\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/debug/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/debug/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/debug/arm64-v8a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/debug/x86/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/debug/x86/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/debug/x86/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/debug/x86_64/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/debug/x86_64/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/debug/x86_64/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "       * NativeVariant:\n"
                                    + "          * name = \"release\"\n"
                                    + "          > abis:\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"armeabi-v7a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/release/armeabi-v7a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/release/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/release/armeabi-v7a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"arm64-v8a\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/release/arm64-v8a/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/release/arm64-v8a/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/release/arm64-v8a/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/release/x86/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/release/x86/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/release/x86/build_file_index.txt{F}\n"
                                    + "             * NativeAbi:\n"
                                    + "                * name                  = \"x86_64\"\n"
                                    + "                * sourceFlagsFile       = {PROJECT}/lib/.cxx/ndkBuild/release/x86_64/compile_commands.json.bin{F}\n"
                                    + "                * symbolFolderIndexFile = {PROJECT}/lib/.cxx/ndkBuild/release/x86_64/symbol_folder_index.txt{F}\n"
                                    + "                * buildFileIndexFile    = {PROJECT}/lib/.cxx/ndkBuild/release/x86_64/build_file_index.txt{F}\n"
                                    + "          < abis\n"
                                    + "    < variants\n"
                                    + "    - nativeBuildSystem       = NDK_BUILD\n"
                                    + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                    + "    - externalNativeBuildFile = {PROJECT}/lib/src/main/cxx/Android.mk{F}\n"
                                    + "< NativeModule");
            NativeVariant debugVariant =
                    fetchResult.getContainer().getSingleModel().getVariants().stream()
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
                                        debugX86Abi.getSourceFlagsFile(),
                                        fetchResult.getNormalizer()))
                        .isEqualTo(
                                "sourceFile: {PROJECT}/lib/src/main/cxx/hello-jni.c{F}\n"
                                        + "compiler:   {NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang{F}\n"
                                        + "workingDir: {PROJECT}/lib{D}\n"
                                        + "flags:      [-target, i686-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/lib/src/main/cxx, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
            } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                assertThat(
                                dumpCompileCommandsJsonBin(
                                        debugX86Abi.getSourceFlagsFile(),
                                        fetchResult.getNormalizer()))
                        .isEqualTo(
                                "sourceFile: {PROJECT}/lib/src/main/cxx/hello-jni.c{F}\n"
                                        + "compiler:   {NDK_ROOT}/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe{F}\n"
                                        + "workingDir: {PROJECT}/lib{D}\n"
                                        + "flags:      [-target, i686-none-linux-android21, -fdata-sections, -ffunction-sections, -fstack-protector-strong, -funwind-tables, -no-canonical-prefixes, --sysroot, {NDK_ROOT}/build//../toolchains/llvm/prebuilt/windows-x86_64/sysroot, -g, -Wno-invalid-command-line-argument, -Wno-unused-command-line-argument, -D_FORTIFY_SOURCE=2, -fPIC, -O0, -UNDEBUG, -fno-limit-debug-info, -I{PROJECT}/lib/src/main/cxx, -DANDROID, -Wformat, -Werror=format-security, -mstackrealign]");
            }
        } else {
            // Make sure we can successfully get AndroidProject
            project.model().fetchAndroidProjects().getOnlyModelMap().get(":app");

            NativeAndroidProject model =
                    project.model().fetchMulti(NativeAndroidProject.class).get(":lib");
            assertThat(model).isNotNull();
            assertThat(model.getBuildFiles()).hasSize(1);
            assertThat(model.getName()).isEqualTo("lib");
            assertThat(model.getArtifacts())
                    .hasSize(2 * NdkHelper.getNdkInfo(project).getDefaultAbis().size());
            assertThat(model.getFileExtensions()).hasSize(1);

            for (File file : model.getBuildFiles()) {
                assertThat(file).isFile();
            }

            Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

            for (NativeArtifact artifact : model.getArtifacts()) {
                List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
                assertThat(pathElements).contains("obj");
                groupToArtifacts.put(artifact.getGroupName(), artifact);
            }

            assertThat(groupToArtifacts.keySet()).containsExactly("debug", "release");
            assertThat(groupToArtifacts.get("debug"))
                    .hasSize(groupToArtifacts.get("release").size());

            assertThat(model).hasVariantInfoBuildFolderForEachAbi();
        }
    }
}
