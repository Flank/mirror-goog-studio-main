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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.v2.models.ndk.NativeAbi;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.builder.model.v2.models.ndk.NativeVariant;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
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

/** Assemble tests for Cmake. */
@RunWith(Parameterized.class)
public class CmakeStlMatrixTest {

    private final String stl;
    @Rule
    public GradleTestProject project;

    @Parameterized.Parameters(name = "stl={0}")
    public static Collection<Object[]> data() {
        return ImmutableList.of(new Object[] {"c++_shared"}, new Object[] {"c++_static"});
    }

    public CmakeStlMatrixTest(String stl) {
        this.stl = stl;
        project =
                GradleTestProject.builder()
                        .fromTestApp(
                                HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
                        // TODO(159233213) Turn to ON when release configuration is cacheable
                        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
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
    public void checkModel() {
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
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{!}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{!}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}\n"
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

    @Test
    public void checkClean() {
        project.execute("clean", "assembleDebug", "assembleRelease");
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
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{F}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{DEBUG}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{F}\n"
                                + "         < abis\n"
                                + "      < debug\n"
                                + "      > release:\n"
                                + "         > abis:\n"
                                + "            - armeabi-v7a:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{F}\n"
                                + "            - x86_64:\n"
                                + "               - sourceFlagsFile                 = {PROJECT}/.cxx/{RELEASE}/x86_64/compile_commands.json.bin{F}\n"
                                + "               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{F}\n"
                                + "               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{F}\n"
                                + "         < abis\n"
                                + "      < release\n"
                                + "   < variants\n"
                                + "   - nativeBuildSystem       = CMAKE\n"
                                + "   - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "   - externalNativeBuildFile = {PROJECT}/CMakeLists.txt{F}\n"
                                + "< NativeModule");

        NativeModule nativeModule = result.getContainer().getSingleNativeModule();
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

        List<File> soFolders = new ArrayList<File>();
        for (NativeVariant variant : nativeModule.getVariants()) {
            for (NativeAbi abi : variant.getAbis()) {
                for (File soFolder : readAsFileIndex(abi.getSymbolFolderIndexFile())) {
                    soFolders.add(soFolder);
                }
            }
        }

        project.execute("clean");

        for (File soFolder : soFolders) {
            assertThat(soFolder).doesNotExist();
        }
    }
}
