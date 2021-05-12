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
package com.android.build.gradle.external.gnumake;

import static com.android.build.gradle.external.gnumake.NdkSampleTestUtilKt.checkAllCommandsRecognized;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.truth.NativeBuildConfigValueSubject;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NdkSampleTest {

    // The purpose of this test parameter is to also test Linux synthetic file functions
    // even when running on Linux (and the same for Windows) so that when you're running
    // tests on Linux you can test whether your changes broken the corresponding synthetic
    // test on Windows (and vice-versa).
    @Parameterized.Parameters(name = "forceSyntheticFileFunctions = {0}")
    public static Collection<Object[]> data() {
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[] {false});
        result.add(new Object[] {true});
        return result;
    }

    private boolean forceSyntheticFileFunctions;

    public NdkSampleTest(boolean forceSyntheticFileFunctions) {
        this.forceSyntheticFileFunctions = forceSyntheticFileFunctions;
    }

    // Turn this flag to true to regenerate test JSON from preexisting baselines in the case that
    // output has intentionally changed.
    @SuppressWarnings("FieldCanBeLocal")
    private static final boolean REGENERATE_TEST_JSON_FROM_TEXT = false;

    @NonNull
    private static final String TEST_DATA_FOLDER =
            "tools/base/build-system/gradle-core/src/test/data/ndk-sample-baselines/";

    private static final ImmutableList<CommandClassifier.BuildTool> extraTestClassifiers =
            ImmutableList.of(
                    new NdkBuildWarningBuildTool(),
                    new NoOpBuildTool("bcc_compat"), // Renderscript
                    new NoOpBuildTool("llvm-rs-cc"), // Renderscript
                    new NoOpBuildTool("rm"),
                    new NoOpBuildTool("cd"),
                    new NoOpBuildTool("cp"),
                    new NoOpBuildTool("md"),
                    new NoOpBuildTool("del"),
                    new NoOpBuildTool("echo.exe"),
                    new NoOpBuildTool("mkdir"),
                    new NoOpBuildTool("echo"),
                    new NoOpBuildTool("copy"),
                    new NoOpBuildTool("install"),
                    new NoOpBuildTool("androideabi-strip"),
                    new NoOpBuildTool("android-strip"));

    /**
     * This build tool skips warning that can be emitted by ndk-build during -n -B processing.
     * Example, Android NDK: WARNING: APP_PLATFORM android-19 is larger than android:minSdkVersion
     * 14 in {ndkPath}/samples/HelloComputeNDK/AndroidManifest.xml
     */
    static class NdkBuildWarningBuildTool implements CommandClassifier.BuildTool {
        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList());
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.getExecutable().equals("Android");
        }
    }

    /**
     * This build tool recognizes a particular command and treats it as a build step with no inputs
     * and no outputs.
     */
    static class NoOpBuildTool implements CommandClassifier.BuildTool {
        @NonNull private final String executable;

        NoOpBuildTool(@NonNull String executable) {
            this.executable = executable;
        }

        @NonNull
        @Override
        public BuildStepInfo createCommand(@NonNull CommandLine command) {
            return new BuildStepInfo(command, Lists.newArrayList(), Lists.newArrayList(), false);
        }

        @Override
        public boolean isMatch(@NonNull CommandLine command) {
            return command.getExecutable().endsWith(executable);
        }
    }

    private static Map<String, String> getVariantConfigs() {
        return ImmutableMap.<String, String>builder()
                .put("debug", "NDK_DEBUG=1")
                .put("release", "NDK_DEBUG=0")
                .build();
    }

    @NonNull
    private static File getVariantBuildOutputFile(
            @NonNull File testPath,
            @NonNull String variant,
            int operatingSystem) {
        return TestUtils.resolveWorkspacePath(
                        TEST_DATA_FOLDER
                                + testPath.getName()
                                + "."
                                + variant
                                + "."
                                + getOsName(operatingSystem)
                                + ".txt")
                .toFile();
    }

    @NonNull
    private static String getOsName(int os) {
        switch (os) {
            case SdkConstants.PLATFORM_LINUX:
                return "linux";
            case SdkConstants.PLATFORM_DARWIN:
                return "darwin";
            case SdkConstants.PLATFORM_WINDOWS:
                return "windows";
            default:
                return "unknown";
        }
    }

    @NonNull
    private static File getJsonFile(@NonNull File testPath, int operatingSystem) {
        return TestUtils.resolveWorkspacePath(
                        TEST_DATA_FOLDER
                                + testPath.getName()
                                + "."
                                + getOsName(operatingSystem)
                                + ".json")
                .toFile();
    }

    @NonNull
    private static File getJsonBinFile(@NonNull File testPath, int operatingSystem) {
        Path folder = TestUtils.resolveWorkspacePath(TEST_DATA_FOLDER);
        return folder.resolve(
                        testPath.getName()
                                + "."
                                + getOsName(operatingSystem)
                                + "-compile_commands.json.bin")
                .toFile();
    }

    private NativeBuildConfigValues checkJson(String path) throws IOException {
        return checkJson(path, SdkConstants.PLATFORM_LINUX);
    }

    private static class NativeBuildConfigValues {
        List<NativeBuildConfigValue> configs = new ArrayList<>();
    }

    private NativeBuildConfigValues checkJson(String path, int operatingSystem) throws IOException {
        File androidMkPath = TestUtils.getNdk().resolve(path).toFile();
        Map<String, String> variantConfigs = getVariantConfigs();

        // Get the baseline config
        File baselineJsonFile = getJsonFile(androidMkPath, operatingSystem);
        File compileCommandsJsonBin =
                TestUtils.getTestOutputDir().resolve("compile_commands.json.bin").toFile();

        // Build the expected result
        OsFileConventions fileConventions = getPathHandlingPolicy(operatingSystem);

        NativeBuildConfigValues actualConfig = new NativeBuildConfigValues();
        for (String variantName : variantConfigs.keySet()) {
            NativeBuildConfigValueBuilder builder =
                    new NativeBuildConfigValueBuilder(
                            androidMkPath,
                            new File("{executeFromHere}"),
                            compileCommandsJsonBin,
                            fileConventions);
            File variantBuildOutputFile =
                    getVariantBuildOutputFile(androidMkPath, variantName, operatingSystem);
            String variantBuildOutputText = Joiner.on('\n')
                    .join(Files.readLines(variantBuildOutputFile, Charsets.UTF_8));

            builder.setCommands(
                    ImmutableList.of("echo", "build", "command"),
                    ImmutableList.of("echo", "clean", "command"),
                    variantName,
                    variantBuildOutputText);

            // Add extra command classifiers that are supposed to match all commands in the test.
            // The checks below will see whether there are extra commands we don't know about.
            // If there are unknown commands we need to evaluate whether they should be understood
            // by the parser or just ignored (added to extraTestClassifiers)
            List<CommandClassifier.BuildTool> testClassifiers = Lists.newArrayList();
            testClassifiers.addAll(CommandClassifier.DEFAULT_CLASSIFIERS);
            testClassifiers.addAll(extraTestClassifiers);
            List<CommandLine> commandLines =
                    CommandLineParser.parse(variantBuildOutputText, fileConventions);
            List<BuildStepInfo> recognized =
                    CommandClassifier.classify(
                            variantBuildOutputText, fileConventions, testClassifiers);
            checkAllCommandsRecognized(commandLines, recognized);
            checkExpectedCompilerParserBehavior(commandLines);

            actualConfig.configs.add(builder.build());
        }


        String actualResult = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(actualConfig);
        checkOutputsHaveAllowedExtensions(actualConfig);

        String testPathString = androidMkPath.toString();
        // actualResults contains JSon as text. JSon escapes back slash with a second backslash.
        // Backslash is also the directory separator on Windows. In order to properly replace
        // {testPath} we must follow the JSon escaping rule.
        testPathString = testPathString.replace("\\", "\\\\");
        actualResult = actualResult.replace(testPathString, "{testPath}");
        actualConfig = new Gson().fromJson(actualResult, NativeBuildConfigValues.class);

        if (!baselineJsonFile.exists() || REGENERATE_TEST_JSON_FROM_TEXT) {
            Files.asCharSink(baselineJsonFile, Charsets.UTF_8).write(actualResult);
        }

        // Build the baseline result.
        String baselineResult = Joiner.on('\n')
                .join(Files.readLines(baselineJsonFile, Charsets.UTF_8));

        NativeBuildConfigValues baselineConfig =
                new Gson().fromJson(baselineResult, NativeBuildConfigValues.class);
        assertThat(actualConfig.configs.size()).isEqualTo(baselineConfig.configs.size());
        for (int i = 0; i < actualConfig.configs.size(); ++i) {
            NativeBuildConfigValue config = actualConfig.configs.get(i);
            assertConfig(config).isEqualTo(baselineConfig.configs.get(i));
            assertConfig(config).hasUniqueLibraryNames();
        }
        return actualConfig;
    }

    @NonNull
    private OsFileConventions getPathHandlingPolicy(int scriptSourceOS) {
        if (scriptSourceOS == SdkConstants.currentPlatform() && !forceSyntheticFileFunctions) {
            // The script was created on the OS that is currently executing.
            // Just use the default path handler which defers to built-in Java file handling
            // functions.
            return AbstractOsFileConventions.createForCurrentHost();
        }
        // Otherwise, create a test-only path handler that will work from the current OS on
        // script -nB output that was produced on another host.
        switch (scriptSourceOS) {
            case SdkConstants.PLATFORM_WINDOWS:
                // Script -nB was produced on Windows but the current OS isn't windows.
                return getSyntheticWindowsPathHandlingPolicy();
            case SdkConstants.PLATFORM_LINUX:
                // Script -nB was produced on linux, but the current host is something else.
                // If the current host is Windows, then produce a handler for that will work
                return getSyntheticLinuxPathHandlingPolicy();
        }

        // If the current host is not Windows or Linux then it isn't a case we currently
        // have scripts for.
        throw new RuntimeException(
                "Need a cross-OS path handling policy for script source OS " + scriptSourceOS);
    }

    @NonNull
    private static OsFileConventions getSyntheticLinuxPathHandlingPolicy() {
        return new PosixFileConventions() {
            @Override
            public boolean isPathAbsolute(@NonNull String file) {
                return file.startsWith("/");
            }

            @NonNull
            @Override
            public String getFileName(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(pos + 1);
            }

            @NonNull
            @Override
            public String getFileParent(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(0, pos);
            }
        };
    }

    @NonNull
    private static OsFileConventions getSyntheticWindowsPathHandlingPolicy() {
        return new WindowsFileConventions() {
            @Override
            public boolean isPathAbsolute(@NonNull String file) {
                if (file.length() < 3) {
                    // Not enough space for a drive letter.
                    return false;
                }

                String segment = file.substring(1, 3);
                return segment.equals(":/") || segment.equals(":\\");
            }

            @NonNull
            @Override
            public String getFileName(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(pos + 1);
            }

            @NonNull
            @Override
            public String getFileParent(@NonNull String filename) {
                int pos = getLastIndexOfAnyFilenameSeparator(filename);
                return filename.substring(0, pos);
            }

            @NonNull
            @Override
            public File toFile(@NonNull String filename) {
                filename = filename.replace('\\', '/');
                return new File(filename);
            }

            @NonNull
            @Override
            public File toFile(@NonNull File parent, @NonNull String child) {
                return new File(parent.toString().replace('\\', '/'), child.replace('\\', '/'));
            }
        };
    }

    private static int getLastIndexOfAnyFilenameSeparator(String filename) {
        return Math.max(filename.lastIndexOf('\\'), filename.lastIndexOf('/'));
    }

    private static void checkOutputsHaveAllowedExtensions(
            @NonNull NativeBuildConfigValues configs) {
        for (NativeBuildConfigValue config : configs.configs) {
            checkNotNull(config.libraries);
            for (NativeLibraryValue library : config.libraries.values()) {
                // These are the three extensions that should occur. These align with what CMake does.
                checkNotNull(library.output);
                if (library.output.toString().endsWith(".so")) {
                    continue;
                }
                if (library.output.toString().endsWith(".a")) {
                    continue;
                }
                if (!library.output.toString().contains(".")) {
                    continue;
                }
                throw new RuntimeException(
                        String.format(
                                "Library output %s had an unexpected extension", library.output));
            }
        }
    }

    // Find the compiler commands and check their parse against expected parse.
    private static void checkExpectedCompilerParserBehavior(@NonNull List<CommandLine> commands) {
        for (CommandLine command : commands) {
            if (new CommandClassifier.NativeCompilerBuildTool().isMatch(command)) {
                for (String arg : command.getEscapedFlags()) {
                    if (arg.startsWith("-")) {
                        String trimmed = arg;
                        while (trimmed.startsWith("-")) {
                            trimmed = trimmed.substring(1);
                        }
                        boolean matched = false;
                        for (String withRequiredArgFlag : CompilerParser.WITH_REQUIRED_ARG_FLAGS) {
                            if (trimmed.startsWith(withRequiredArgFlag)) {
                                matched = true;
                            }
                        }

                        for (String withNoArgsFlag : CompilerParser.WITH_NO_ARG_FLAGS) {
                            if (trimmed.equals(withNoArgsFlag)) {
                                matched = true;
                            }
                        }

                        // Recognize -W style flag
                        if (trimmed.startsWith("W")) {
                            matched = true;
                        }

                        // joptsimple won't accept flags with +
                        if (trimmed.contains("+")) {
                            matched = true;
                        }

                        if (!matched) {
                            // If you get here, there is a new gcc or clang flag in a baseline test.
                            // For completeness, you should add this flag in CompilerParser.
                            throw new RuntimeException(
                                    "The flag " + arg + " was not a recognized compiler flag");

                        }
                    }
                }
            }
        }
    }

    /*
    Why is NativeBuildConfigValueSubject assertThat here?

        Current state
        -------------
        (1) NativeBuildConfigValue is in gradle-core package
        (2) NativeBuildConfigValueBuilder is in gradle-core package
        (3) therefore NativeBuildConfigValueBuilder tests are in gradle-core-test package
        (4) NativeBuildConfigValueSubject is in gradle-core-tests package
        (5) MoreTruth is in testutils package which does not reference gradle-core
        (6) therefore NativeBuildConfigValueSubject can't be in MoreTruth
        (7) also therefore I can't use MoreTruth from NativeBuildConfigValueBuilder tests

     */
    @NonNull
    public static NativeBuildConfigValueSubject assertConfig(
            @Nullable NativeBuildConfigValue project) {
        return assertAbout(NativeBuildConfigValueSubject.nativebuildConfigValues()).that(project);
    }

    @Test
    public void dontCheckInBaselineUpdaterFlags() {
        assertThat(REGENERATE_TEST_JSON_FROM_TEXT).isFalse();
    }

    @Test
    public void syntheticWindowsPathHandlingAbsolutePath() {
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:\\")).isTrue();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:/")).isTrue();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("\\")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("/")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("\\x")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("/x")).isFalse();
        assertThat(getSyntheticWindowsPathHandlingPolicy().isPathAbsolute("C:x")).isFalse();
    }



    // Related to b.android.com/227685 which caused wrong file path in src file when path was
    // relative to folder containing build.gradle. Fix was to make the path absolute by explicitly
    // rooting it under execute path.
    @Test
    public void cocos2d() throws IOException {
        NativeBuildConfigValue config = checkJson("samples/cocos2d").configs.get(0);
        // Expect relative paths to be rooted at execute path
        assertConfig(config)
                .hasSourceFileNames(
                        FileUtils.toSystemDependentPath(
                                "{executeFromHere}/../../../../external/bullet/"
                                        + "BulletMultiThreaded/btThreadSupportInterface.cpp"));
    }

    // Related to issuetracker.google.com/69110338. Covers case where there is a compiler flag
    // with spaces like -DMY_FLAG='my value'
    @Test
    public void singleQuotedDefine() throws IOException {
        NativeBuildConfigValue config = checkJson("samples/tick-in-define-repro").configs.get(0);
        assertConfig(config).hasExactLibrariesNamed("example-debug-armeabi-v7a");
        NativeSourceFileValue file =
                config.libraries.get("example-debug-armeabi-v7a").files.iterator().next();
        // Below is the actual fact we're trying to assert for this bug. We need to preserve
        // the single quotes around hello world
        assertThat(file.flags).contains("-DTOM='hello world'");
    }

    // Related to b.android.com/216676. Same source file name produces same target name.
    @Test
    public void duplicateSourceNames() throws IOException {
        NativeBuildConfigValues config = checkJson("samples/duplicate-source-names");
        assertConfig(config.configs.get(0))
                .hasExactLibrariesNamed(
                        "hello-jni-debug-mips",
                        "apple-debug-mips",
                        "hello-jni-debug-armeabi-v7a",
                        "banana-debug-x86_64",
                        "hello-jni-debug-armeabi",
                        "hello-jni-debug-x86_64",
                        "banana-debug-armeabi",
                        "apple-debug-arm64-v8a",
                        "apple-debug-armeabi",
                        "banana-debug-mips64",
                        "banana-debug-arm64-v8a",
                        "hello-jni-debug-mips64",
                        "banana-debug-mips",
                        "apple-debug-mips64",
                        "apple-debug-x86",
                        "banana-debug-x86",
                        "hello-jni-debug-arm64-v8a",
                        "hello-jni-debug-x86",
                        "banana-debug-armeabi-v7a",
                        "apple-debug-armeabi-v7a",
                        "apple-debug-x86_64");
        assertConfig(config.configs.get(1))
                .hasExactLibrariesNamed(
                        "apple-release-mips64",
                        "banana-release-armeabi",
                        "apple-release-mips",
                        "apple-release-armeabi",
                        "banana-release-arm64-v8a",
                        "hello-jni-release-mips64",
                        "apple-release-x86_64",
                        "hello-jni-release-arm64-v8a",
                        "banana-release-x86",
                        "hello-jni-release-armeabi",
                        "apple-release-x86",
                        "hello-jni-release-x86",
                        "hello-jni-release-x86_64",
                        "banana-release-armeabi-v7a",
                        "apple-release-arm64-v8a",
                        "banana-release-mips64",
                        "hello-jni-release-armeabi-v7a",
                        "apple-release-armeabi-v7a",
                        "banana-release-x86_64",
                        "hello-jni-release-mips",
                        "banana-release-mips");
    }

    // Related to b.android.com/218397. On Windows, the wrong target name was used because it
    // was passed through File class which caused slashes to be normalized to back slash.
    @Test
    public void windowsTargetName() throws IOException {
        NativeBuildConfigValues config =
                checkJson("samples/windows-target-name", SdkConstants.PLATFORM_WINDOWS);
        assertConfig(config.configs.get(0))
                .hasExactLibrariesNamed(
                        "hello-jni-debug-mips",
                        "hello-jni-debug-mips64",
                        "hello-jni-debug-armeabi-v7a",
                        "hello-jni-debug-arm64-v8a",
                        "hello-jni-debug-x86",
                        "hello-jni-debug-armeabi",
                        "hello-jni-debug-x86_64");
        assertConfig(config.configs.get(1))
                .hasExactLibrariesNamed(
                        "hello-jni-release-armeabi",
                        "hello-jni-release-x86",
                        "hello-jni-release-x86_64",
                        "hello-jni-release-mips64",
                        "hello-jni-release-armeabi-v7a",
                        "hello-jni-release-mips",
                        "hello-jni-release-arm64-v8a");
    }

    // Related to b.android.com/214626
    @Test
    public void localModuleFilename() throws IOException {
        checkJson("samples/LOCAL_MODULE_FILENAME");
    }

    @Test
    public void includeFlag() throws IOException {
        checkJson("samples/include-flag");
    }

    @Test
    public void clangExample() throws IOException {
        NativeBuildConfigValue config = checkJson("samples/clang").configs.get(0);
        // Assert that full paths coming from the ndk-build output aren't further qualified with
        // executution path.
        assertConfig(config)
                .hasExactSourceFileNames(
                        FileUtils.toSystemDependentPath(
                                "/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/"
                                        + "sources/android/cpufeatures/cpu-features.c"),
                        FileUtils.toSystemDependentPath(
                                "/usr/local/google/home/jomof/projects/"
                                        + "hello-neon1/app/src/main/cpp/helloneon.c"));
    }

    @Test
    public void neonExample() throws IOException {
        checkJson("samples/neon");
    }

    @Test
    public void ccacheExample() throws IOException {
        // CCache is turned on in ndk build by setting NDK_CCACHE to a path to ccache
        // executable.
        checkJson("samples/ccache");
    }

    @Test
    public void googleTestExample() throws IOException {
        NativeBuildConfigValue config = checkJson("samples/google-test-example").configs.get(0);
        assertConfig(config)
                .hasExactLibraryOutputs(
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_static.a"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/sample1_unittest"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libsample1.so"),
                        FileUtils.toSystemDependentPath(
                                "{NDK}/debug/obj/local/arm64-v8a/libgoogletest_main.a"));
    }

    @Test
    public void missingIncludeExample() throws IOException {
        checkJson("samples/missing-include");
    }

    @Test
    public void sanAngelesExample() throws IOException {
        checkJson("samples/san-angeles", SdkConstants.PLATFORM_LINUX);
    }

    // Related to issuetracker.google.com/175718909
    @Test
    public void ndk22StaticLibraryExampleLinux() throws IOException {
        checkJson("samples/r22", SdkConstants.PLATFORM_LINUX);
    }

    // Related to issuetracker.google.com/175718909
    @Test
    public void ndk22StaticLibraryExampleWindows() throws IOException {
        checkJson("samples/r22", SdkConstants.PLATFORM_WINDOWS);
    }

    @Test
    public void sanAngelesWindows() throws IOException {
        checkJson("samples/san-angeles", SdkConstants.PLATFORM_WINDOWS);
    }

    // input: support-files/ndk-sample-baselines/Teapot.json
    @Test
    public void teapot() throws IOException {
        checkJson("samples/Teapot");
    }

    // input: support-files/ndk-sample-baselines/native-audio.json
    @Test
    public void nativeAudio() throws IOException {
        checkJson("samples/native-audio");
    }

    // input: support-files/ndk-sample-baselines/native-codec.json
    @Test
    public void nativeCodec() throws IOException {
        checkJson("samples/native-codec");
    }

    // input: support-files/ndk-sample-baselines/native-media.json
    @Test
    public void nativeMedia() throws IOException {
        checkJson("samples/native-media");
    }

    // input: support-files/ndk-sample-baselines/native-plasma.json
    @Test
    public void nativePlasma() throws IOException {
        checkJson("samples/native-plasma");
    }

    // input: support-files/ndk-sample-baselines/bitmap-plasma.json
    @Test
    public void bitmapPlasm() throws IOException {
        checkJson("samples/bitmap-plasma");
    }

    // input: support-files/ndk-sample-baselines/native-activity.json
    @Test
    public void nativeActivity() throws IOException {
        checkJson("samples/native-activity");
    }

    // input: support-files/ndk-sample-baselines/HelloComputeNDK.json
    @Test
    public void helloComputeNDK() throws IOException {
        NativeBuildConfigValue config = checkJson("samples/HelloComputeNDK").configs.get(0);
        assertConfig(config)
                .hasExactLibraryOutputs(
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/x86/libhellocomputendk.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/armeabi-v7a/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/mips/libhellocomputendk.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/mips/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/libs/x86/librs.mono.so"),
                        FileUtils.toSystemDependentPath(
                                "/{ndkPath}/samples/HelloComputeNDK/obj/local/armeabi-v7a/libhellocomputendk.so"));
    }

    // input: support-files/ndk-sample-baselines/test-libstdc++.json
    @Test
    public void testLibstdcpp() throws IOException {
        checkJson("samples/test-libstdc++");
    }

    // input: support-files/ndk-sample-baselines/hello-gl2.json
    @Test
    public void helloGl2() throws IOException {
        checkJson("samples/hello-gl2");
    }

    // input: support-files/ndk-sample-baselines/two-libs.json
    @Test
    public void twoLibs() throws IOException {
        checkJson("samples/two-libs");
    }

    // input: support-files/ndk-sample-baselines/module-exports.json
    @Test
    public void moduleExports() throws IOException {
        checkJson("samples/module-exports");
    }
}
