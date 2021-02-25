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
import static com.android.build.gradle.internal.cxx.configure.CmakeLocatorKt.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION;
import static com.android.build.gradle.options.BooleanOption.ENABLE_NATIVE_CONFIGURATION_FOLDING;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.models.ndk.NativeModule;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for CMake. */
public class CmakeJniLibTest {

    @Rule
    public GradleTestProject project;

    public CmakeJniLibTest() {
        project =
                GradleTestProject.builder()
                        .fromTestProject("ndkJniLib")
                        .addFile(HelloWorldJniApp.cmakeLists("lib"))
                        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
                        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
                        .setWithCmakeDirInLocalProp(true)
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        new File(project.getProjectDir(), "src/main/jni")
                .renameTo(new File(project.getProjectDir(), "src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion rootProject.latestCompileSdk\n"
                        + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                        + "}\n");

        // Convert externalNativeBuild { ndkbuild { path "Android.mk" } } to
        // externalNativeBuild { cmake { path "CMakeList.txt" } }
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "ndkBuild", "cmake");
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "Android.mk", "CMakeLists.txt");
        project.execute(
                "clean", "assembleDebug", "generateJsonModelDebug", "generateJsonModelRelease");
        if (ENABLE_NATIVE_CONFIGURATION_FOLDING.getDefaultValue()) {
            assertThat(project.getSubproject("lib").file("build/intermediates/cxx")).exists();
        } else {
            assertThat(project.getSubproject("lib").file("build/intermediates/cmake")).exists();
        }
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkApkContent() {
        GradleTestProject app = project.getSubproject("app");
        Apk gingerbreadUniversal =
                app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread");
        if (!gingerbreadUniversal.exists()) {
            throw new RuntimeException(String.format("Could not find %s", gingerbreadUniversal));
        }

        TruthHelper.assertThatApk(
                app.getApk("universal", GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(
                        app.getApk(
                                "armeabi-v7a", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        TruthHelper.assertThatApk(
                        app.getApk("x86", GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() {
        ModelBuilderV2.FetchResult<ModelContainerV2<NativeModule>> result =
                project.modelV2().fetchNativeModules(null, null);
        assertThat(dump(result))
                .isEqualTo(
                        "[:lib]\n"
                                + "> NativeModule:\n"
                                + "    - name                    = \"lib\"\n"
                                + "    > variants:\n"
                                + "       * NativeVariant:\n"
                                + "          * name = \"debug\"\n"
                                + "          > abis:\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"armeabi-v7a\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"arm64-v8a\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"x86\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"x86_64\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{F}\n"
                                + "          < abis\n"
                                + "       * NativeVariant:\n"
                                + "          * name = \"release\"\n"
                                + "          > abis:\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"armeabi-v7a\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"arm64-v8a\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"x86\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86/additional_project_files.txt{F}\n"
                                + "             * NativeAbi:\n"
                                + "                * name                            = \"x86_64\"\n"
                                + "                * sourceFlagsFile                 = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/compile_commands.json.bin{F}\n"
                                + "                * symbolFolderIndexFile           = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/symbol_folder_index.txt{F}\n"
                                + "                * buildFileIndexFile              = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/build_file_index.txt{F}\n"
                                + "                * additionalProjectFilesIndexFile = {PROJECT}/lib/build/intermediates/{RELEASE}/meta/x86_64/additional_project_files.txt{F}\n"
                                + "          < abis\n"
                                + "    < variants\n"
                                + "    - nativeBuildSystem       = CMAKE\n"
                                + "    - ndkVersion              = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "    - defaultNdkVersion       = \"{DEFAULT_NDK_VERSION}\"\n"
                                + "    - externalNativeBuildFile = {PROJECT}/lib/CMakeLists.txt{F}\n"
                                + "< NativeModule");
    }
}
