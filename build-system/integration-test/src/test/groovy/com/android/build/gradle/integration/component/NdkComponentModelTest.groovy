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

package com.android.build.gradle.integration.component

import com.android.SdkConstants
import com.android.build.gradle.integration.common.category.SmokeTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.NativeModelHelper
import com.android.build.gradle.internal.core.Abi
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeLibrary
import com.android.builder.model.Variant
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

/**
 * Test the return model of the NDK.
 */
@CompileStatic
@Category(SmokeTests.class)
class NdkComponentModelTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .useExperimentalGradleVersion(true)
            .create()

    @Before
    void setUp() {
        project.buildFile <<
"""
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
        ndk {
            moduleName "hello-jni"
            CFlags.add("-DTEST_C_FLAG")
            cppFlags.add("-DTEST_CPP_FLAG")
            toolchain "clang"
        }
    }
}
"""
    }

    @Test
    void "check native libraries in model"() {
        checkModel(
                debug : [
                    SdkConstants.ABI_ARMEABI,
                    SdkConstants.ABI_ARMEABI_V7A,
                    SdkConstants.ABI_ARM64_V8A,
                    SdkConstants.ABI_INTEL_ATOM,
                    SdkConstants.ABI_INTEL_ATOM64,
                    SdkConstants.ABI_MIPS,
                    SdkConstants.ABI_MIPS64
                ]);
    }

    @Test
    void "check targeted ABI"() {
project.buildFile <<
"""
model {
    android {
        ndk {
            abiFilters.add("x86")
            abiFilters.add("armeabi-v7a")
        }
        abis {
            create("x86") {
                CFlags.add("-DX86")
            }
            create("armeabi-v7a") {
                CFlags.add("-DARMEABI_V7A")
            }
        }
    }
}
"""
        AndroidProject model = checkModel(
                debug : [
                        SdkConstants.ABI_ARMEABI_V7A,
                        SdkConstants.ABI_INTEL_ATOM
                        ]);
        Collection<NativeLibrary> libs =
                ModelHelper.getVariant(model.getVariants(), "debug").getMainArtifact().getNativeLibraries()

        for (NativeLibrary nativeLibrary : libs) {
            if (nativeLibrary.getAbi() == "x86") {
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DX86")
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("-DARMEABI_V7A")
            } else {
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("-DX86")
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DARMEABI_V7A")
            }
        }
    }

    @Test
    void "check native libraries with splits"() {
        project.buildFile <<
"""
model {
    android {
        splits.with {
            abi {
                enable true
                reset()
                include 'x86', 'armeabi-v7a', 'mips'
            }
        }
    }
}
"""
        checkModel(
                debug: [SdkConstants.ABI_ARMEABI_V7A, SdkConstants.ABI_INTEL_ATOM, SdkConstants.ABI_MIPS]);
    }

    @Test
    void "check native libraries with splits and universalApk"() {
        project.buildFile <<
                """
model {
    android {
        splits.with {
            abi {
                enable true
                reset()
                include 'x86', 'armeabi-v7a', 'mips'
                universalApk true
            }
        }
    }
}
"""
        checkModel(
                debug : [
                        SdkConstants.ABI_ARMEABI,
                        SdkConstants.ABI_ARMEABI_V7A,
                        SdkConstants.ABI_ARM64_V8A,
                        SdkConstants.ABI_INTEL_ATOM,
                        SdkConstants.ABI_INTEL_ATOM64,
                        SdkConstants.ABI_MIPS,
                        SdkConstants.ABI_MIPS64
                ]);
    }

    @Test
    void "check native libraries with abiFilters"() {
        project.buildFile <<
                """
model {
    android.productFlavors {
        create("x86") {
            ndk.abiFilters.add("x86")
        }
        create("arm") {
            ndk.abiFilters.add("armeabi-v7a")
        }
        create("mips") {
            ndk.abiFilters.add("mips")
        }
    }
}
"""
        checkModel(
                x86Debug : [SdkConstants.ABI_INTEL_ATOM],
                armDebug : [SdkConstants.ABI_ARMEABI_V7A],
                mipsDebug : [SdkConstants.ABI_MIPS]);
    }

    @Test
    void "check variant specific flags"() {
        project.buildFile <<
                """
model {
    android.buildTypes {
        debug {
            ndk.CFlags.add("-DTEST_FLAG_DEBUG")
        }
        release {
            ndk.CFlags.add("-DTEST_FLAG_RELEASE")
        }
    }
    android.productFlavors {
        create("f1") {
            ndk.CFlags.add("-DTEST_FLAG_F1")
        }
        create("f2") {
            ndk.CFlags.add("-DTEST_FLAG_F2")
        }
    }
}
"""
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        NativeLibrary f1Debug = ModelHelper.getVariant(model.getVariants(), "f1Debug").getMainArtifact()
                .getNativeLibraries().first()
        assertThat(f1Debug.getCCompilerFlags()).contains("-DTEST_FLAG_DEBUG")
        assertThat(f1Debug.getCCompilerFlags()).contains("-DTEST_FLAG_F1")
        NativeLibrary f2Release = ModelHelper.getVariant(model.getVariants(), "f2Release").getMainArtifact()
                .getNativeLibraries().first()
        assertThat(f2Release.getCCompilerFlags()).contains("-DTEST_FLAG_RELEASE")
        assertThat(f2Release.getCCompilerFlags()).contains("-DTEST_FLAG_F2")
    }

    @Test
    void "check using add on string for compileSdkVersion"() {
        project.buildFile <<
"""
model {
    android {
        compileSdkVersion = "Google Inc.:Google APIs:$GradleTestProject.DEFAULT_COMPILE_SDK_VERSION"
    }
}
"""
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        NativeLibrary lib = ModelHelper.getVariant(model.getVariants(), "debug").getMainArtifact()
                .getNativeLibraries().first()
        for (String flag : lib.getCCompilerFlags()) {
            if (flag.contains("sysroot")) {
                assertThat(flag).contains("android-${GradleTestProject.LATEST_NDK_PLATFORM_VERSION}")
            }
        }
    }

    @Test
    void "check that compiler flags are transformed as in options.txt" () {
        project.buildFile <<
                """
model {
    android {
        ndk {
            CFlags.add("-Ipath with spaces")
            CFlags.add("-Ipath\twith\ttabs")
            CFlags.add("-Ipath\\\\with\\\\backslashes")
        }
    }
}
"""
        AndroidProject model = project.executeAndReturnModel("assembleDebug")
        NativeLibrary lib = ModelHelper.getVariant(model.getVariants(), "debug").getMainArtifact()
                .getNativeLibraries().first()
        assertThat(lib.getCCompilerFlags()).containsAllOf(
                "\"-Ipath with spaces\"",
                "\"-Ipath\twith\ttabs\"",
                "-Ipath\\\\with\\\\backslashes");
    }

    /**
     * Verify resulting AndroidProject and NativeAndroidProject is as expected.
     *
     * @param variantAbi map of variant name to array of expected abi.
     */
    private AndroidProject checkModel(Map variantAbi) {
        project.execute("assembleDebug")
        checkNativeAndroidProject(variantAbi)
        return checkAndroidProject(variantAbi)
    }

    private AndroidProject checkAndroidProject(Map variantAbi) {
        AndroidProject androidProject = project.model().getSingle();
        Collection<Variant> variants = androidProject.getVariants()

        for (Map.Entry entry : variantAbi) {
            Variant variant = ModelHelper.getVariant(variants, (String) entry.getKey())
            AndroidArtifact mainArtifact = variant.getMainArtifact()

            assertThat(mainArtifact.getNativeLibraries()).hasSize(((Collection)entry.getValue()).size())
            for (NativeLibrary nativeLibrary : mainArtifact.getNativeLibraries()) {
                assertThat(nativeLibrary.getName()).isEqualTo("hello-jni")
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DTEST_C_FLAG");
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-gcc-toolchain");  // check clang specific flags
                assertThat(nativeLibrary.getCppCompilerFlags()).contains("-DTEST_CPP_FLAG");
                assertThat(nativeLibrary.getCppCompilerFlags()).contains("-gcc-toolchain");  // check clang specific flags
                assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
                assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty();
                File solibSearchPath = nativeLibrary.getDebuggableLibraryFolders().first()
                assertThat(new File(solibSearchPath, "libhello-jni.so")).exists()
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("null");
                assertThat(nativeLibrary.getCppCompilerFlags()).doesNotContain("null");
            }

            Collection<String> expectedToolchainNames = entry.getValue().collect { "clang-" + it }
            Collection<String> toolchainNames = androidProject.getNativeToolchains().collect { it.getName() }
            assertThat(toolchainNames).containsAllIn(expectedToolchainNames)
            Collection<String> nativeLibToolchains = mainArtifact.getNativeLibraries().
                    collect { it.getToolchainName() }
            assertThat(nativeLibToolchains).containsExactlyElementsIn(expectedToolchainNames)
        }
        return androidProject
    }

    private void checkNativeAndroidProject(Map variantAbi) {
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        for (Map.Entry entry : variantAbi) {
            String variantName = (String) entry.getKey();
            Collection<NativeArtifact> artifacts = model.getArtifacts().findAll{
                artifact -> artifact.getName().contains(variantName) }

            for (NativeArtifact artifact : artifacts) {
                List<String> cFlags =
                        NativeModelHelper.getCFlags(model, artifact).get(project.file("src/main/jni"));
                assertThat(cFlags).contains("-DTEST_C_FLAG");
                assertThat(cFlags).contains("-gcc-toolchain");
                // There is no C++ flags as there is no C++ source files.
                assertThat(NativeModelHelper.getFlatCppFlags(model, artifact)).isEmpty()
                assertThat(artifact.getOutputFile()).exists();
            }

            Collection<String> toolchainNames = model.getToolChains().collect { it.getName() }
            assertThat(toolchainNames).containsAllIn(
                    Abi.values().collect { Abi it -> "ndk-" + it.getName() } )
        }
    }
}
