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
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
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
import java.util.Arrays;
import java.util.Collection;
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
        project =
                GradleTestProject.builder()
                        .fromExternalProject("gradle-perf-android-medium")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.PERF_ANDROID_MEDIUM, projectScenario))
                        .withHeap("20G")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] { // TODO: do we want to include legacy multidex?
                    {ProjectScenario.NATIVE_MULTIDEX}, // {ProjectScenario.LEGACY_MULTIDEX},
                });
    }

    @Before
    public void initializeProject() throws IOException {

        switch (projectScenario) {
            case NATIVE_MULTIDEX:
                break;
            case LEGACY_MULTIDEX:
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }

        Files.copy(
                project.file("gradle.properties-example").toPath(),
                project.file("gradle.properties").toPath());
        Files.copy(
                project.file("WordPress/gradle.properties-example").toPath(),
                project.file("WordPress/gradle.properties").toPath());

        Files.write(
                project.file("local.properties").toPath(),
                ImmutableList.of("sdk.dir=" + System.getenv("ANDROID_HOME")));

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
    }

    @Test
    public void runBenchmarks() throws Exception {
        project.model().getMulti();

        project.executor().run("assemble");
        project.executor().run("clean");

        Map<String, AndroidProject> model =
                project.model().recordBenchmark(BenchmarkMode.SYNC).getMulti();
        assertThat(model.keySet()).contains(":WordPress");

        project.executor()
                .recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN)
                .run("assembleVanillaDebug");

        project.executor().recordBenchmark(BenchmarkMode.NO_OP).run("assembleVanillaDebug");
    }
}
