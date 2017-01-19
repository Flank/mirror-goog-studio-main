/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.BuildModel;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.JackHelper;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MediumGradleProjectPerformanceMatrixTest {

    @Rule public final GradleTestProject project;
    @NonNull private final ProjectScenario projectScenario;

    public MediumGradleProjectPerformanceMatrixTest(@NonNull ProjectScenario projectScenario) {
        this.projectScenario = projectScenario;
        String heapSize;
        if (projectScenario.usesJack()) {
            // Jack takes too long with 1.5GB heap. With 6GB, the duration is reasonable.
            heapSize = "6G";
        } else {
            heapSize = "1536M";
        }
        project =
                GradleTestProject.builder()
                        .fromExternalProject("gradle-perf-android-medium")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.PERF_ANDROID_MEDIUM, projectScenario))
                        .withHeap(heapSize)
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[]{
                ProjectScenario.LEGACY_MULTIDEX,
                ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
                ProjectScenario.NATIVE_MULTIDEX,
                ProjectScenario.JACK_NATIVE_MULTIDEX,
                ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws IOException {
        switch (projectScenario) {
            case NATIVE_MULTIDEX:
            case DEX_ARCHIVE_NATIVE_MULTIDEX:
                TestFileUtils.searchAndReplace(
                        project.file("WordPress/build.gradle"),
                        "minSdkVersion( )* \\d+",
                        "minSdkVersion 21");
                break;
            case LEGACY_MULTIDEX:
            case DEX_ARCHIVE_LEGACY_MULTIDEX:
                break;
            case JACK_NATIVE_MULTIDEX:
                TestFileUtils.searchAndReplace(
                        project.file("WordPress/build.gradle"),
                        "minSdkVersion( )* \\d+",
                        "minSdkVersion 21");
                JackHelper.enableJack(project.file("WordPress/build.gradle"));
                disableCrashlyticsForJack();
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown project scenario" + projectScenario);
        }
        Files.copy(
                project.file("WordPress/gradle.properties-example").toPath(),
                project.file("WordPress/gradle.properties").toPath());

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "buildscript {\n"
                        + "    repositories {\n"
                        + "        maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                        + "'       }"
                        + "    }\n"
                        + "    dependencies {\n"
                        + "        classpath 'com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + "'\n"
                        + "    }\n"
                        + "}");

        List<Path> buildGradleFiles =
                ImmutableList.of(
                                "WordPress/build.gradle",
                                "libs/utils/WordPressUtils/build.gradle",
                                "libs/editor/example/build.gradle",
                                "libs/editor/WordPressEditor/build.gradle",
                                "libs/networking/WordPressNetworking/build.gradle",
                                "libs/analytics/WordPressAnalytics/build.gradle")
                        .stream()
                        .map(name -> project.file(name).toPath())
                        .collect(Collectors.toList());

        for (Path file : buildGradleFiles) {
            TestFileUtils.searchAndReplace(
                    file, "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'", "");

            TestFileUtils.searchAndReplace(
                    file,
                    "jcenter\\(\\)",
                    "maven { url '"
                            + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                            + "'}\njcenter()");

            TestFileUtils.searchAndReplace(
                    file,
                    "buildToolsVersion \"[^\"]+\"",
                    String.format("buildToolsVersion \"%s\"", AndroidBuilder.MIN_BUILD_TOOLS_REV));
        }

        //TODO: Upstream some of this?

        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "classpath 'com\\.novoda:bintray-release:0\\.3\\.4'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "apply plugin: 'com\\.novoda\\.bintray-release'",
                "");
        TestFileUtils.searchAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "publish \\{[\\s\\S]*\\}",
                "");

        TestFileUtils.searchAndReplace(
                project.file("libs/networking/WordPressNetworking/build.gradle"),
                "maven \\{ url 'http://wordpress-mobile\\.github\\.io/WordPress-Android' \\}",
                "");

        TestFileUtils.searchAndReplace(
                project.file("libs/networking/WordPressNetworking/build.gradle"),
                "compile 'org\\.wordpress:utils:1\\.11\\.0'",
                "releaseCompile "
                        + "project(path:':libs:utils:WordPressUtils', configuration: 'release')\n"
                        + "    debugCompile "
                        + "project(path:':libs:utils:WordPressUtils', configuration: 'debug')\n");
        TestFileUtils.searchAndReplace(
                project.file("libs/analytics/WordPressAnalytics/build.gradle"),
                "compile 'org\\.wordpress:utils:1\\.11\\.0'",
                "releaseCompile "
                        + "project(path:':libs:utils:WordPressUtils', configuration: 'release')\n"
                        + "    debugCompile "
                        + "project(path:':libs:utils:WordPressUtils', configuration: 'debug')\n");

        // There is an extraneous BOM in the values-ja/strings.xml
        Files.copy(
                project.file("WordPress/src/main/res/values-en-rCA/strings.xml").toPath(),
                project.file("WordPress/src/main/res/values-ja/strings.xml").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void runBenchmarks() throws Exception {
        // Warm up
        model().getMulti();
        executor().run("assemble");
        executor().run("clean");

        executor().recordBenchmark(BenchmarkMode.EVALUATION).run("tasks");

        Map<String, AndroidProject> model =
                model().recordBenchmark(BenchmarkMode.SYNC).getMulti().getModelMap();
        assertThat(model.keySet()).contains(":WordPress");


        executor()
                .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                .withArgument("-Pandroid.injected.generateSourcesOnly=true")
                .run(
                        ModelHelper.getGenerateSourcesCommands(
                                model,
                                project ->
                                        project.equals(":WordPress") ? "vanillaDebug" : "debug"));

        executor().run("clean");

        FileUtils.cleanOutputDir(project.executor().getBuildCacheDir());

        executor().recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN).run("assembleVanillaDebug");

        executor().recordBenchmark(BenchmarkMode.NO_OP).run("assembleVanillaDebug");

        executor().run("clean");

        executor()
                .recordBenchmark(BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN)
                .run("assembleVanillaDebugAndroidTest");
    }

    @NonNull
    private BuildModel model() {
        return project.model().withoutOfflineFlag();
    }

    @NonNull
    private RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .disablePreDexBuildCache()
                .disableAaptV2()
                .withtUseDexArchive(projectScenario.useDexArchive())
                .withoutOfflineFlag();
    }

    /** Removes the crashlytics plugin, and any dependencies on it. */
    private void disableCrashlyticsForJack() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("WordPress").getBuildFile(),
                "apply plugin: 'io\\.fabric'",
                "");
        TestFileUtils.searchAndReplace(
                project.getSubproject("WordPress").getBuildFile(),
                "variant\\.generateBuildConfig\\.dependsOn\\(generateCrashlyticsConfig\\)",
                "");
    }
}
