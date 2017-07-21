package com.android.build.gradle.integration.component;

import static com.android.SdkConstants.ABI_INTEL_ATOM64;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.NativeModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.core.Abi;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Variant;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Test the return model of the NDK. */
@Category(SmokeTests.class)
public class NdkComponentModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldJniApp())
                    .useExperimentalGradleVersion(true)
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.model.application'\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        ndk {\n"
                        + "            moduleName \"hello-jni\"\n"
                        + "            CFlags.add(\"-DTEST_C_FLAG\")\n"
                        + "            cppFlags.add(\"-DTEST_CPP_FLAG\")\n"
                        + "            toolchain \"clang\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkNativeLibrariesInModel() throws IOException, InterruptedException {
        checkModel(
                ImmutableMap.of(
                        "debug",
                        ImmutableList.of(
                                SdkConstants.ABI_ARMEABI,
                                SdkConstants.ABI_ARMEABI_V7A,
                                SdkConstants.ABI_ARM64_V8A,
                                SdkConstants.ABI_INTEL_ATOM,
                                ABI_INTEL_ATOM64,
                                SdkConstants.ABI_MIPS,
                                SdkConstants.ABI_MIPS64)));
    }

    @Test
    public void checkTargetedAbi() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        ndk {\n"
                        + "            abiFilters.add(\"x86\")\n"
                        + "            abiFilters.add(\"armeabi-v7a\")\n"
                        + "        }\n"
                        + "        abis {\n"
                        + "            create(\"x86\") {\n"
                        + "                CFlags.add(\"-DX86\")\n"
                        + "            }\n"
                        + "            create(\"armeabi-v7a\") {\n"
                        + "                CFlags.add(\"-DARMEABI_V7A\")\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        AndroidProject model =
                checkModel(
                        ImmutableMap.of(
                                "debug",
                                ImmutableList.of(
                                        SdkConstants.ABI_ARMEABI_V7A,
                                        SdkConstants.ABI_INTEL_ATOM)));
        Collection<NativeLibrary> libs =
                ModelHelper.getVariant(model.getVariants(), "debug")
                        .getMainArtifact()
                        .getNativeLibraries();

        for (NativeLibrary nativeLibrary : libs) {
            if (nativeLibrary.getAbi().equals("x86")) {
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DX86");
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("-DARMEABI_V7A");
            } else {
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("-DX86");
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DARMEABI_V7A");
            }
        }
    }

    @Test
    public void checkNativeLibrariesWithSplits() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        splits.with {\n"
                        + "            abi {\n"
                        + "                enable true\n"
                        + "                reset()\n"
                        + "                include 'x86', 'armeabi-v7a', 'mips'\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        checkModel(
                ImmutableMap.of(
                        "debug",
                        ImmutableList.of(
                                SdkConstants.ABI_ARMEABI_V7A,
                                SdkConstants.ABI_INTEL_ATOM,
                                SdkConstants.ABI_MIPS)));
    }

    @Test
    public void checkNativeLibrariesWithSplitsAndUniversalApk()
            throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        splits.with {\n"
                        + "            abi {\n"
                        + "                enable true\n"
                        + "                reset()\n"
                        + "                include 'x86', 'armeabi-v7a', 'mips'\n"
                        + "                universalApk true\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        checkModel(
                ImmutableMap.of(
                        "debug",
                        ImmutableList.of(
                                SdkConstants.ABI_ARMEABI,
                                SdkConstants.ABI_ARMEABI_V7A,
                                SdkConstants.ABI_ARM64_V8A,
                                SdkConstants.ABI_INTEL_ATOM,
                                ABI_INTEL_ATOM64,
                                SdkConstants.ABI_MIPS,
                                SdkConstants.ABI_MIPS64)));
    }

    @Test
    public void checkNativeLibrariesWithAbiFilters() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android.productFlavors {\n"
                        + "        create(\"x86\") {\n"
                        + "            ndk.abiFilters.add(\"x86\")\n"
                        + "            dimension \"abi\"\n"
                        + "        }\n"
                        + "        create(\"arm\") {\n"
                        + "            ndk.abiFilters.add(\"armeabi-v7a\")\n"
                        + "            dimension \"abi\"\n"
                        + "        }\n"
                        + "        create(\"mips\") {\n"
                        + "            ndk.abiFilters.add(\"mips\")\n"
                        + "            dimension \"abi\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        checkModel(
                ImmutableMap.of(
                        "x86Debug",
                        ImmutableList.of(SdkConstants.ABI_INTEL_ATOM),
                        "armDebug",
                        ImmutableList.of(SdkConstants.ABI_ARMEABI_V7A),
                        "mipsDebug",
                        ImmutableList.of(SdkConstants.ABI_MIPS)));
    }

    @Test
    public void checkVariantSpecificFlags() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android.buildTypes {\n"
                        + "        debug {\n"
                        + "            ndk.CFlags.add(\"-DTEST_FLAG_DEBUG\")\n"
                        + "        }\n"
                        + "        release {\n"
                        + "            ndk.CFlags.add(\"-DTEST_FLAG_RELEASE\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    android.productFlavors {\n"
                        + "        create(\"f1\") {\n"
                        + "            ndk.CFlags.add(\"-DTEST_FLAG_F1\")\n"
                        + "            dimension \"foo\"\n"
                        + "        }\n"
                        + "        create(\"f2\") {\n"
                        + "            ndk.CFlags.add(\"-DTEST_FLAG_F2\")\n"
                        + "            dimension \"foo\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        AndroidProject model = project.executeAndReturnModel("assembleDebug").getOnlyModel();
        // noinspection ConstantConditions
        NativeLibrary f1Debug = getNativeLibrary(model, "f1Debug");
        assertThat(f1Debug.getCCompilerFlags()).contains("-DTEST_FLAG_DEBUG");
        assertThat(f1Debug.getCCompilerFlags()).contains("-DTEST_FLAG_F1");
        // noinspection ConstantConditions
        NativeLibrary f2Release = getNativeLibrary(model, "f2Release");
        assertThat(f2Release.getCCompilerFlags()).contains("-DTEST_FLAG_RELEASE");
        assertThat(f2Release.getCCompilerFlags()).contains("-DTEST_FLAG_F2");
    }

    @Test
    public void checkUsingAddOnStringForCompileSdkVersion()
            throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion = \"Google Inc.:Google APIs:"
                        + String.valueOf(GradleTestProject.LATEST_GOOGLE_APIS_VERSION)
                        + "\"\n"
                        + "    }\n"
                        + "}\n");
        AndroidProject model = project.executeAndReturnModel("assembleDebug").getOnlyModel();
        NativeLibrary lib = getNativeLibrary(model, "debug");
        assertThat(lib).isNotNull();
        for (String flag : lib.getCCompilerFlags()) {
            if (flag.contains("sysroot")) {
                assertThat(flag)
                        .contains(
                                "android-"
                                        + String.valueOf(
                                                GradleTestProject.LATEST_NDK_PLATFORM_VERSION));
            }
        }
    }

    @Test
    public void checkCompilerFlagsAreTransformedAsInOptionsTxt()
            throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        ndk {\n"
                        + "            CFlags.add(\"-Ipath with spaces\")\n"
                        + "            CFlags.add(\"-Ipath\twith\ttabs\")\n"
                        + "            CFlags.add(\"-Ipath\\\\with\\\\backslashes\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        AndroidProject model = project.executeAndReturnModel("assembleDebug").getOnlyModel();
        NativeLibrary lib = getNativeLibrary(model, "debug");
        assertThat(lib.getCCompilerFlags())
                .containsAllOf(
                        "\"-Ipath with spaces\"",
                        "\"-Ipath\twith\ttabs\"",
                        "-Ipath\\\\with\\\\backslashes");
    }

    /**
     * Verify resulting AndroidProject and NativeAndroidProject is as expected.
     *
     * @param variantAbi map of variant name to array of expected abi.
     */
    private AndroidProject checkModel(Map<String, List<String>> variantAbi)
            throws IOException, InterruptedException {
        project.execute("assembleDebug");
        checkNativeAndroidProject(variantAbi);
        return checkAndroidProject(variantAbi);
    }

    private AndroidProject checkAndroidProject(Map<String, List<String>> variantAbi)
            throws IOException {
        AndroidProject androidProject = project.model().getSingle().getOnlyModel();
        Collection<Variant> variants = androidProject.getVariants();

        for (Map.Entry<String, List<String>> entry : variantAbi.entrySet()) {
            Variant variant = ModelHelper.getVariant(variants, (String) entry.getKey());
            AndroidArtifact mainArtifact = variant.getMainArtifact();

            assertThat(mainArtifact.getNativeLibraries()).hasSize((entry.getValue()).size());
            for (NativeLibrary nativeLibrary : mainArtifact.getNativeLibraries()) {
                assertThat(nativeLibrary.getName()).isEqualTo("hello-jni");
                assertThat(nativeLibrary.getCCompilerFlags()).contains("-DTEST_C_FLAG");
                assertThat(nativeLibrary.getCCompilerFlags())
                        .contains("-gcc-toolchain"); // check clang specific flags
                assertThat(nativeLibrary.getCppCompilerFlags()).contains("-DTEST_CPP_FLAG");
                assertThat(nativeLibrary.getCppCompilerFlags())
                        .contains("-gcc-toolchain"); // check clang specific flags
                assertThat(nativeLibrary.getCSystemIncludeDirs()).isEmpty();
                assertThat(nativeLibrary.getCppSystemIncludeDirs()).isNotEmpty();
                File solibSearchPath =
                        Iterables.getFirst(nativeLibrary.getDebuggableLibraryFolders(), null);
                assertThat(solibSearchPath).isNotNull();
                assertThat(new File(solibSearchPath, "libhello-jni.so")).exists();
                assertThat(nativeLibrary.getCCompilerFlags()).doesNotContain("null");
                assertThat(nativeLibrary.getCppCompilerFlags()).doesNotContain("null");
            }

            Collection<String> expectedToolchainNames =
                    entry.getValue().stream().map(e -> "clang-" + e).collect(Collectors.toList());

            Collection<String> toolchainNames =
                    androidProject
                            .getNativeToolchains()
                            .stream()
                            .map(NativeToolchain::getName)
                            .collect(Collectors.toList());
            assertThat(toolchainNames).containsAllIn(expectedToolchainNames);
            Collection<String> nativeLibToolchains =
                    mainArtifact
                            .getNativeLibraries()
                            .stream()
                            .map(NativeLibrary::getToolchainName)
                            .collect(Collectors.toList());
            assertThat(nativeLibToolchains).containsExactlyElementsIn(expectedToolchainNames);
        }

        return androidProject;
    }

    private void checkNativeAndroidProject(Map<String, List<String>> variantAbi)
            throws IOException {
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        for (Map.Entry<String, List<String>> entry : variantAbi.entrySet()) {
            final String variantName = entry.getKey();
            Collection<NativeArtifact> artifacts =
                    model.getArtifacts()
                            .stream()
                            .filter(i -> i.getName().contains(variantName))
                            .collect(Collectors.toList());

            for (NativeArtifact artifact : artifacts) {
                List<String> cFlags =
                        NativeModelHelper.getCFlags(model, artifact)
                                .get(project.file("src/main/jni"));
                assertThat(cFlags).contains("-DTEST_C_FLAG");
                assertThat(cFlags).contains("-gcc-toolchain");
                assertThat(cFlags).doesNotContain("null");
                List<String> cppFlags =
                        NativeModelHelper.getCppFlags(model, artifact)
                                .get(project.file("src/main/jni"));
                assertThat(cppFlags).contains("-DTEST_CPP_FLAG");
                assertThat(cppFlags).contains("-gcc-toolchain");
                assertThat(cppFlags).doesNotContain("null");
                assertThat(artifact.getOutputFile()).exists();
            }

            Collection<String> toolchainNames =
                    model.getToolChains()
                            .stream()
                            .map(NativeToolchain::getName)
                            .collect(Collectors.toList());

            List<String> expectedToolChains =
                    ImmutableList.copyOf(Abi.values())
                            .stream()
                            .map(i -> "ndk-" + i.getName())
                            .collect(Collectors.toList());
            assertThat(toolchainNames).containsAllIn(expectedToolChains);
        }
    }

    @NonNull
    private static NativeLibrary getNativeLibrary(
            @NonNull AndroidProject model, @NonNull String variant) {
        Collection<NativeLibrary> nativeLibs =
                ModelHelper.getVariant(model.getVariants(), variant)
                        .getMainArtifact()
                        .getNativeLibraries();
        Preconditions.checkNotNull(nativeLibs);
        NativeLibrary first = Iterables.getFirst(nativeLibs, null);
        Preconditions.checkNotNull(first);
        return first;
    }
}
