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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkC;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkCpp;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.androidMkGoogleTest;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.applicationMk;
import static com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp.cmakeLists;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.external.gson.NativeLibraryValue;
import com.android.build.gradle.external.gson.NativeSourceFileValue;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.NativeBuildSystem;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** General Model tests */
@RunWith(Parameterized.class)
public class NativeModelTest {
    private enum Compiler {
        GCC,
        CLANG,
        IRRELEVANT  // indicates if the compiler being used is irrelevant to the test
    }

    private enum Config {
        ANDROID_MK_FILE_C_CLANG(
                "  apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path \"src/main/cpp/Android.mk\"\n"
                        + "                        buildStagingDirectory \"relative/path\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkC("src/main/cpp")),
                false,
                1,
                2,
                7,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                14,
                "relative/path"),
        NDK_BUILD_JOBS_FLAG(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path \"src/main/cpp/Android.mk\"\n"
                        + "                        buildStagingDirectory \"ABSOLUTE_PATH\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\",\n"
                        + "                                \"-j8\",\n"
                        + "                                \"--jobs=8\",\n"
                        + "                                \"-j\", \"8\",\n"
                        + "                                \"--jobs\", \"8\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkC("src/main/cpp")),
                false,
                1,
                2,
                7,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                14,
                "ABSOLUTE_PATH"),
        ANDROID_MK_FILE_CPP_CLANG(
                " apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                externalNativeBuild {\n"
                        + "                    ndkBuild {\n"
                        + "                        path file(\"src/main/cpp/Android.mk\")\n"
                        + "                    }\n"
                        + "                }\n"
                        + "                defaultConfig {\n"
                        + "                    externalNativeBuild {\n"
                        + "                        ndkBuild {\n"
                        + "                            arguments \"NDK_TOOLCHAIN_VERSION:=clang\"\n"
                        + "                            cFlags \"-DTEST_C_FLAG\"\n"
                        + "                            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "                        }\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                2,
                7,
                Compiler.CLANG,
                NativeBuildSystem.NDK_BUILD,
                14,
                ".externalNativeBuild"),
        ANDROID_MK_GOOGLE_TEST(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(
                        androidMkGoogleTest("src/main/cpp"),
                        new TestSourceFile(
                                "src/main/cpp",
                                "hello-jni-unittest.cc",
                                "#include <limits.h>\n"
                                        + "#include \"sample1.h\"\n"
                                        + "#include \"gtest/gtest.h\"\n"
                                        + "TEST(EqualsTest, One) {\n"
                                        + "  EXPECT_EQ(1, 1);\n"
                                        + "}")),
                true,
                4,
                2,
                7,
                Compiler.IRRELEVANT,
                NativeBuildSystem.NDK_BUILD,
                0,
                ".externalNativeBuild"),
        ANDROID_MK_FILE_CPP_GCC(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                arguments \"NDK_TOOLCHAIN_VERSION:=4.9\"\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                2,
                7,
                Compiler.GCC,
                NativeBuildSystem.NDK_BUILD,
                14,
                ".externalNativeBuild"),
        ANDROID_MK_FILE_CPP_GCC_VIA_APPLICATION_MK(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path file(\"src/main/cpp/Android.mk\")\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp"), applicationMk("src/main/cpp")),
                true,
                1,
                2,
                7,
                Compiler.GCC,
                NativeBuildSystem.NDK_BUILD,
                14,
                ".externalNativeBuild"),
        ANDROID_MK_CUSTOM_BUILD_TYPE(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        ndkBuild {\n"
                        + "            path \"src/main/cpp/Android.mk\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            ndkBuild {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    buildTypes {\n"
                        + "        myCustomBuildType {\n"
                        + "             externalNativeBuild {\n"
                        + "              ndkBuild {\n"
                        + "                cppFlags \"-DCUSTOM_BUILD_TYPE\"\n"
                        + "              }\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(androidMkCpp("src/main/cpp")),
                true,
                1,
                3,
                7,
                Compiler.IRRELEVANT,
                NativeBuildSystem.NDK_BUILD,
                21,
                ".externalNativeBuild"),
        CMAKELISTS_FILE_CPP(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            buildStagingDirectory \"ABSOLUTE_PATH\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "            cmake {\n"
                        + "                cFlags \"-DTEST_C_FLAG\"\n"
                        + "                cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                true,
                1,
                2,
                7,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                14,
                "ABSOLUTE_PATH"),
        CMAKELISTS_ARGUMENTS(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "            buildStagingDirectory \"relative/path\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            arguments \"-DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG\"\n"
                        + "            cFlags \"-DTEST_C_FLAG\"\n"
                        + "            abiFilters \"armeabi-v7a\", \"armeabi\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                true,
                1,
                2,
                2,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                4,
                "relative/path"),
        CMAKELISTS_FILE_C(
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path \"CMakeLists.txt\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "    defaultConfig {\n"
                        + "        externalNativeBuild {\n"
                        + "          cmake {\n"
                        + "            cFlags \"-DTEST_C_FLAG\"\n"
                        + "            cppFlags \"-DTEST_CPP_FLAG\"\n"
                        + "          }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                ImmutableList.of(cmakeLists(".")),
                false,
                1,
                2,
                7,
                Compiler.IRRELEVANT,
                NativeBuildSystem.CMAKE,
                14,
                ".externalNativeBuild");

        public String buildGradle;
        private final List<TestSourceFile> extraFiles;
        public final boolean isCpp;
        public final int targetCount;
        public final int variantCount;
        public final int abiCount;
        public final Compiler compiler;
        public final NativeBuildSystem buildSystem;
        public final int expectedBuildOutputs;
        public String nativeBuildOutputPath;

        Config(String buildGradle, List<TestSourceFile> extraFiles, boolean isCpp, int targetCount,
                int variantCount, int abiCount, Compiler compiler,
                NativeBuildSystem buildSystem, int expectedBuildOutputs, String nativeBuildOutputPath) {
            this.buildGradle = buildGradle;
            this.extraFiles = extraFiles;
            this.isCpp = isCpp;
            this.targetCount = targetCount;
            this.variantCount = variantCount;
            this.abiCount = abiCount;
            this.compiler = compiler;
            this.buildSystem = buildSystem;
            this.expectedBuildOutputs = expectedBuildOutputs;
            this.nativeBuildOutputPath = nativeBuildOutputPath;
        }

        public GradleTestProject create() {
            GradleTestProject project = GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder()
                        .withNativeDir("cpp")
                        .useCppSource(isCpp)
                        .build())
                    .addFiles(extraFiles)
                    .create();

            return project;
        }
    }

    @Rule public final GradleTestProject project;

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "model = {0}")
    public static Object[][] data() {
        return new Object[][] {
            {Config.NDK_BUILD_JOBS_FLAG},
            {Config.ANDROID_MK_FILE_C_CLANG},
            {Config.ANDROID_MK_FILE_CPP_CLANG},
            {Config.ANDROID_MK_GOOGLE_TEST},
            {Config.ANDROID_MK_FILE_CPP_GCC},
            // disabled due to http://b.android.com/230228
            // {Config.ANDROID_MK_FILE_CPP_GCC_VIA_APPLICATION_MK},
            {Config.ANDROID_MK_CUSTOM_BUILD_TYPE},
            {Config.CMAKELISTS_FILE_C},
            {Config.CMAKELISTS_FILE_CPP},
            {Config.CMAKELISTS_ARGUMENTS},
        };
    }

    private final Config config;

    public NativeModelTest(Config config) {
        this.config = config;
        this.project = config.create();
    }

    @Before
    public void setup() throws Exception {
        if (config.nativeBuildOutputPath.equals("ABSOLUTE_PATH")) {
            File tempOutputDirectory = temporaryFolder.newFolder("absolute_path");
            config.buildGradle =
                    config.buildGradle.replace(
                            "ABSOLUTE_PATH", tempOutputDirectory.getAbsolutePath());
            config.nativeBuildOutputPath = tempOutputDirectory.getAbsolutePath();
        }

        TestFileUtils.appendToFile(project.getBuildFile(), config.buildGradle);
    }

    @Test
    public void checkModel() throws Exception {
        AndroidProject androidProject = project.model().getSingle().getOnlyModel();
        assertThat(androidProject.getSyncIssues()).hasSize(0);
        NativeAndroidProject model = project.model().getSingle(NativeAndroidProject.class);
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo("project");
        assertThat(model.getArtifacts())
                .hasSize(config.targetCount * config.variantCount * config.abiCount);

        // Settings should be non-empty but the count depends on flags and build system because
        // settings are the distinct set of flags.
        assertThat(model.getSettings()).isNotEmpty();
        assertThat(model.getToolChains()).hasSize(config.variantCount * config.abiCount);
        assertThat(model).hasArtifactGroupsOfSize(config.targetCount * config.abiCount);

        for (File file : model.getBuildFiles()) {
            assertThat(file).isFile();
        }

        for (NativeArtifact artifact : model.getArtifacts()) {
            File parent = artifact.getOutputFile().getParentFile();
            List<String> parents = Lists.newArrayList();
            while(parent != null) {
                parents.add(parent.getName());
                parent = parent.getParentFile();
            }
            assertThat(parents).contains("obj");
            assertThat(parents).doesNotContain("lib");
        }

        if (config.variantCount == 2) {
            checkDefaultVariants(model);
        } else {
            checkCustomVariants(model);
        }

        if (config.isCpp) {
            checkIsCpp(model);
        } else {
            checkIsC(model);
        }

        if (config.compiler == Compiler.GCC) {
            checkGcc(model);
        } else if (config.compiler == Compiler.CLANG) {
            checkClang(model);
        }
        checkProblematicCompilerFlags(model);
        checkNativeBuildOutputPath(config, project);
    }

    @Test
    public void checkUpToDate() throws Exception {
        File jsonFile = getJsonFile("debug", "armeabi");

        // Initially, JSON file doesn't exist
        assertThat(jsonFile).doesNotExist();

        // Syncing once, causes the JSON to exist
        project.model().getSingle(NativeAndroidProject.class);
        assertThat(jsonFile).exists();
        long originalTimeStamp = getHighestResolutionTimeStamp(jsonFile);

        // Syncing again, leaves the JSON unchanged
        NativeAndroidProject nativeProject = project.model().getSingle(NativeAndroidProject.class);
        assertThat(originalTimeStamp).isEqualTo(
                getHighestResolutionTimeStamp(jsonFile));

        // Touch each buildFile and check that JSON is regenerated in response
        assertThat(nativeProject.getBuildFiles()).isNotEmpty();
        for (File buildFile : nativeProject.getBuildFiles()) {
            assertThat(buildFile).exists();
            spinTouch(buildFile, originalTimeStamp);
            project.model().getSingle(NativeAndroidProject.class);
            long newTimeStamp = getHighestResolutionTimeStamp(jsonFile);
            assertThat(newTimeStamp).isGreaterThan(originalTimeStamp);
            originalTimeStamp = newTimeStamp;
        }

        String originalFileHash = FileUtils.sha1(jsonFile);

        // Replace flags in the build file and check that JSON is regenerated
        TestFileUtils.searchAndReplace(project.getBuildFile(), "-DTEST_", "-DTEST_CHANGED_");
        nativeProject = project.model().getSingle(NativeAndroidProject.class);
        assertThat(FileUtils.sha1(jsonFile)).isNotEqualTo(originalFileHash);

        // Check that the newly written flags are there.
        if (config.isCpp) {
            checkIsChangedCpp(nativeProject);
        } else {
            checkIsChangedC(nativeProject);
        }

        // Do a clean and check that the JSON is not regenerated
        originalTimeStamp = getHighestResolutionTimeStamp(jsonFile);
        project.execute("clean");
        nativeProject = project.model().getSingle(NativeAndroidProject.class);
        assertThat(originalTimeStamp).isEqualTo(getHighestResolutionTimeStamp(jsonFile));
        assertThat(nativeProject).noBuildOutputsExist();

        // If there are expected build outputs then build them and check results
        if (config.expectedBuildOutputs > 0) {
            project.execute("assemble");
            assertThat(nativeProject).hasBuildOutputCountEqualTo(config.expectedBuildOutputs);
            assertThat(nativeProject).allBuildOutputsExist();

            // Make sure clean removes the known build outputs.
            project.execute("clean");
            assertThat(nativeProject).noBuildOutputsExist();

            // But clean shouldn't change the JSON because it is outside of the build/ folder.
            assertThat(originalTimeStamp).isEqualTo(getHighestResolutionTimeStamp(jsonFile));
        }
    }

    static NativeBuildConfigValue getNativeBuildConfigValue(File json)
            throws FileNotFoundException {
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(
                                File.class,
                                new TypeAdapter() {

                                    @Override
                                    public void write(JsonWriter jsonWriter, Object o)
                                            throws IOException {}

                                    @Override
                                    public Object read(JsonReader jsonReader) throws IOException {
                                        return new File(jsonReader.nextString());
                                    }
                                })
                        .create();
        return gson.fromJson(new FileReader(json),
                NativeBuildConfigValue.class);
    }

    /**
     * Related to b.android.com/214558 Clean commands should never have -j (or --jobs) parameter
     * because gnu make doesn't handle this well.
     */
    @Test
    public void checkNdkBuildCleanHasNoJobsFlags() throws Exception {
        if (config.buildSystem == NativeBuildSystem.NDK_BUILD) {
            project.model().getSingle(NativeAndroidProject.class);
            NativeBuildConfigValue buildConfig =
                    getNativeBuildConfigValue(getJsonFile("debug", "armeabi"));
            for (String cleanCommand : buildConfig.cleanCommands) {
                assertThat(cleanCommand).doesNotContain("-j");
            }
        }
    }

    @Test
    public void checkDebugVsRelease() throws Exception {
        // Sync
        project.model().getSingle(NativeAndroidProject.class);

        File debugJson = getJsonFile("debug", "armeabi");
        File releaseJson = getJsonFile("release", "armeabi");

        NativeBuildConfigValue debug = getNativeBuildConfigValue(debugJson);
        NativeBuildConfigValue release = getNativeBuildConfigValue(releaseJson);

        Set<String> releaseFlags = uniqueFlags(release);
        Set<String> debugFlags = uniqueFlags(debug);

        // Look at flags that are only in release build. Should at least contain -DNDEBUG and -Os
        Set<String> releaseOnlyFlags = Sets.newHashSet(releaseFlags);
        releaseOnlyFlags.removeAll(debugFlags);
        assertThat(releaseOnlyFlags)
        // TODO Put '.named' back when this test source file is translated from groovy to java
                //.named("release only build flags")
                .containsAllOf("-DNDEBUG", "-Os");

        // Look at flags that are only in debug build. Should at least contain -O0
        Set<String> debugOnlyFlags = Sets.newHashSet(debugFlags);
        debugOnlyFlags.removeAll(releaseFlags);
        assertThat(debugOnlyFlags)
        // TODO Put '.named' back when this test source file is translated from groovy to java
                //.named("debug only build flags")
                .contains("-O0");
    }

    private static Set<String> uniqueFlags(NativeBuildConfigValue config) {
        Set<String> flags = Sets.newHashSet();
        for (NativeLibraryValue library : config.libraries.values()) {
            for (NativeSourceFileValue file : library.files) {
                flags.addAll(StringHelper.tokenizeString(file.flags));
            }
        }
        return flags;
    }

    /*
    The best file system timestamp is millisecond and lower resolution is available depending on
    operating system and Java versions. This implementation of touch makes sure that the new
    timestamp isn't the same as the old timestamp by spinning until the clock increases.
     */
    private static void spinTouch(File file, long lastTimestamp) throws IOException {
        file.setLastModified(System.currentTimeMillis());
        while (getHighestResolutionTimeStamp(file) == lastTimestamp) {
            file.setLastModified(System.currentTimeMillis());
        }
    }

    private static long getHighestResolutionTimeStamp(File file) throws IOException {
        return Files.getLastModifiedTime(
                file.toPath()).toMillis();
    }

    private File getExternalNativeBuildRootOutputFolder() {
        return new File(FileUtils.join(project.getBuildFile().getParent(), ".externalNativeBuild"));
    }

    private File getJsonFile(String variantName, String abi) {
        return ExternalNativeBuildTaskUtils.getOutputJson(
                FileUtils.join(
                        buildNativeBuildOutputPath(config, project),
                        config.buildSystem.getName(),
                        variantName),
                abi);
    }

    private static void checkDefaultVariants(NativeAndroidProject model) {
        assertThat(model).hasArtifactGroupsNamed("debug", "release");

        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DCUSTOM_BUILD_TYPE");
        }
    }

    private static void checkCustomVariants(NativeAndroidProject model) {
        assertThat(model).hasArtifactGroupsNamed("debug", "release", "myCustomBuildType");

        boolean sawCustomVariantFLag = false;
        for (NativeSettings settings : model.getSettings()) {
            List<String> flags = settings.getCompilerFlags();
            if (flags.contains("-DCUSTOM_BUILD_TYPE")) {
                assertThat(settings).containsCompilerFlag("-DCUSTOM_BUILD_TYPE");
                sawCustomVariantFLag = true;
            }
        }
        assertThat(sawCustomVariantFLag).isTrue();
    }

    private static void checkGcc(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlagStartingWith("-gcc-toolchain");
        }
    }

    private static void checkClang(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlagStartingWith("-gcc-toolchain");
        }
    }

    private static void checkProblematicCompilerFlags(NativeAndroidProject model) {
        for (NativeSettings settings : model.getSettings()) {
            // These flags are known to cause problems, see b.android.com/215555 and
            // b.android.com/213429. They should be stripped (or not present) by JSON producer.
            assertThat(settings).doesNotContainCompilerFlag("-MMD");
            assertThat(settings).doesNotContainCompilerFlag("-MP");
            assertThat(settings).doesNotContainCompilerFlag("-MT");
            assertThat(settings).doesNotContainCompilerFlag("-MQ");
            assertThat(settings).doesNotContainCompilerFlag("-MG");
            assertThat(settings).doesNotContainCompilerFlag("-M");
            assertThat(settings).doesNotContainCompilerFlag("-MM");
        }
    }

    private static void checkIsC(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        assertThat(model.getFileExtensions()).doesNotContainEntry("cpp", "c++");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlag("-DTEST_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
        }
    }

    private static void checkIsCpp(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");
        assertThat(model.getFileExtensions()).doesNotContainEntry("c", "c");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).containsCompilerFlag("-DTEST_CPP_FLAG");
        }
    }

    private static void checkIsChangedC(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("c", "c");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
            assertThat(settings).containsCompilerFlag("-DTEST_CHANGED_C_FLAG");
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CHANGED_CPP_FLAG");
        }
    }

    private static void checkIsChangedCpp(NativeAndroidProject model) {
        assertThat(model.getFileExtensions()).containsEntry("cpp", "c++");
        for (NativeSettings settings : model.getSettings()) {
            assertThat(settings).doesNotContainCompilerFlag("-DTEST_CPP_FLAG");
            assertThat(settings).containsCompilerFlag("-DTEST_CHANGED_CPP_FLAG");
        }
    }

    private static void checkNativeBuildOutputPath(Config config, GradleTestProject project) {
        NativeBuildSystem buildSystem = config.buildSystem;
        File outputDir = buildNativeBuildOutputPath(config, project);

        switch (buildSystem) {
            case CMAKE:
                outputDir = FileUtils.join(outputDir, "cmake");
                break;
            case NDK_BUILD:
                outputDir = FileUtils.join(outputDir, "ndkBuild");
                break;
            default:
                return;
        }

        assertThat(outputDir).exists();
        assertThat(outputDir).isDirectory();
    }

    private static File buildNativeBuildOutputPath(Config config, GradleTestProject project) {
        String nativeBuildOutputPath = config.nativeBuildOutputPath;
        File projectDir = project.getTestDir();

        File outputDir = new File(nativeBuildOutputPath);
        if (!outputDir.isAbsolute()) {
            outputDir = FileUtils.join(projectDir, nativeBuildOutputPath);
        }

        return outputDir;
    }
}
