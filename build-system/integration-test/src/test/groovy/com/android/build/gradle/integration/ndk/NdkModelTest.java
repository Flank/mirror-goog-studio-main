/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.ndk;

import static com.android.SdkConstants.ABI_ARM64_V8A;
import static com.android.SdkConstants.ABI_ARMEABI;
import static com.android.SdkConstants.ABI_ARMEABI_V7A;
import static com.android.SdkConstants.ABI_INTEL_ATOM;
import static com.android.SdkConstants.ABI_INTEL_ATOM64;
import static com.android.SdkConstants.ABI_MIPS;
import static com.android.SdkConstants.ABI_MIPS64;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test the return model of the NDK.
 */
public class NdkModelTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "    defaultConfig {\n"
                + "        ndk {\n"
                + "            moduleName \"hello-jni\"\n"
                + "            cFlags = \"-DTEST_FLAG\"\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
    }

    @Test
    public void checkNativeLibrariesInModel() {
        checkModel(ImmutableMap.of(
                "debug", Lists.newArrayList(
                        ABI_ARMEABI, ABI_ARMEABI_V7A, ABI_ARM64_V8A,
                        ABI_INTEL_ATOM, ABI_INTEL_ATOM64,
                        ABI_MIPS, ABI_MIPS64)));
    }

    @Test
    public void checkNativeLibrariesWithSplits() throws IOException {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android {\n"
                + "    splits {\n"
                + "        abi {\n"
                + "            enable true\n"
                + "            reset()\n"
                + "            include 'x86', 'armeabi-v7a', 'mips'\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        checkModel(ImmutableMap.of(
                "debug", Lists.newArrayList(ABI_ARMEABI_V7A, ABI_INTEL_ATOM, ABI_MIPS)));
    }

    @Test
    public void checkNativeLibrariesWithSplitsAndUniversalApk() throws IOException {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android {\n"
                + "    splits {\n"
                + "        abi {\n"
                + "            enable true\n"
                + "            reset()\n"
                + "            include 'x86', 'armeabi-v7a', 'mips'\n"
                + "            universalApk true\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        checkModel(ImmutableMap.of(
                "debug", Lists.newArrayList(
                        ABI_ARMEABI, ABI_ARMEABI_V7A, ABI_ARM64_V8A,
                        ABI_INTEL_ATOM, ABI_INTEL_ATOM64,
                        ABI_MIPS, ABI_MIPS64)));
    }

    @Test
    public void checkNativeLibrariesWithAbiFilters() throws IOException {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android {\n"
                + "    productFlavors {\n"
                + "        x86 {\n"
                + "            ndk {\n"
                + "                abiFilter \"x86\"\n"
                + "            }\n"
                + "        }\n"
                + "        arm {\n"
                + "            ndk {\n"
                + "                abiFilters \"armeabi-v7a\"\n"
                + "            }\n"
                + "        }\n"
                + "        mips {\n"
                + "            ndk {\n"
                + "                abiFilter \"mips\"\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        checkModel(ImmutableMap.of(
                "x86Debug", Lists.newArrayList(ABI_INTEL_ATOM),
                "armDebug", Lists.newArrayList(ABI_ARMEABI_V7A),
                "mipsDebug", Lists.newArrayList(ABI_MIPS)));
    }

    @Test
    public void checkUsingAddOnStringForCompileSdkVersion() throws IOException {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        TestFileUtils.appendToFile(project.getBuildFile(),
                "android {\n"
                + "    compileSdkVersion \"Google Inc.:Google APIs:" + GradleTestProject.LATEST_GOOGLE_APIS_VERSION + "\"\n"
                + "}\n");

        AndroidProject model = project.executeAndReturnModel("assembleDebug").getOnlyModel();

        Variant variant = ModelHelper.getVariant(model.getVariants(), "debug");
        assertThat(variant).isNotNull();
        AndroidArtifact artifact = variant.getMainArtifact();
        assertThat(artifact).isNotNull();
        Collection<NativeLibrary> nativeLibraries = artifact.getNativeLibraries();
        assertThat(nativeLibraries).isNotNull();
        NativeLibrary lib = Iterables.getFirst(nativeLibraries, null);
        for (String flag : lib.getCCompilerFlags()) {
            if (flag.contains("sysroot")) {
                int expected =
                        NdkHelper.getPlatformSupported(
                                project.getNdkDir(),
                                Integer.toString(GradleTestProject.LATEST_GOOGLE_APIS_VERSION));
                assertThat(flag).contains("android-" + expected);
            }
        }
    }

    /**
     * Verify resulting model is as expected.
     *
     * @param variantToolchains map of variant name to array of expected toolchains.
     */
    private void checkModel(Map<String, List<String>> variantToolchains) {

        AndroidProject model = project.executeAndReturnModel("assembleDebug").getOnlyModel();

        Collection<Variant> variants = model.getVariants();
        for (Map.Entry<String, List<String>> entry : variantToolchains.entrySet()) {
            Variant variant = ModelHelper.getVariant(variants, entry.getKey());
            AndroidArtifact mainArtifact = variant.getMainArtifact();

            assertThat(mainArtifact.getNativeLibraries()).hasSize(((Collection)entry.getValue()).size());
            for (NativeLibrary nativeLibrary : mainArtifact.getNativeLibraries()) {
                assertThat(nativeLibrary.getName()).isEqualTo("hello-jni");
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DTEST_FLAG");
                assertThat(nativeLibrary.getCppCompilerFlags()).contains("-DTEST_FLAG");
                assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
                assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty();
                File solibSearchPath = nativeLibrary.getDebuggableLibraryFolders().get(0);
                assertThat(new File(solibSearchPath, "libhello-jni.so")).isFile();
            }

            Collection<String> expectedToolchainNames = entry.getValue().stream()
                    .map(s -> "gcc-" + s).collect(Collectors.toList());
            Collection<String> toolchainNames = model.getNativeToolchains().stream()
                    .map(NativeToolchain::getName).collect(Collectors.toList());

            assertThat(toolchainNames).containsAllIn(expectedToolchainNames);
            Collection<String> nativeLibToolchains = mainArtifact.getNativeLibraries().stream()
                    .map(NativeLibrary::getToolchainName).collect(Collectors.toList());
            assertThat(nativeLibToolchains).containsExactlyElementsIn(expectedToolchainNames);
        }
    }
}
