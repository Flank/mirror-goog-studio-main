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

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(FilterableParameterized.class)
public class LargeGradleProjectPerformanceMatrixTest {

    @Rule public final GradleTestProject project;
    @NonNull private final ProjectScenario projectScenario;

    public LargeGradleProjectPerformanceMatrixTest(@NonNull ProjectScenario projectScenario) {
        this.projectScenario = projectScenario;
        project =
                GradleTestProject.builder()
                        .fromExternalProject("android-studio-gradle-test")
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.PERF_ANDROID_LARGE, projectScenario))
                        .withHeap("20G")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {// TODO: do we want to include legacy multidex?
                    {ProjectScenario.NATIVE_MULTIDEX},// {ProjectScenario.LEGACY_MULTIDEX},
                });
    }

    @Before
    public void initializeProject() throws IOException {

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                "maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("CUSTOM_REPO"))
                        + "'} \n"
                        + "        maven { url '"
                        + FileUtils.toSystemIndependentPath(System.getenv("ANDROID_HOME"))
                        + "/extras/android/m2repository' };"
                        + "        jcenter()");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                "classpath 'com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + "'");

        TestFileUtils.searchAndReplace(
                project.file("dependencies.gradle"),
                "buildToolsVersion: '\\d+.\\d+.\\d+',",
                "buildToolsVersion: '25.0.0'");

        // Fix project compilation.
        TestFileUtils.searchAndReplace(
                project.file("outissue/cyclus/build.gradle"),
                "dependencies \\{",
                "dependencies {\n    compile deps.support.appCompat");

        switch (projectScenario) {
            case NATIVE_MULTIDEX:
                TestFileUtils.searchAndReplace(
                        project.file("dependencies.gradle"),
                        "minSdkVersion( )*: \\d+,",
                        "minSdkVersion : 21,");
                break;
            case LEGACY_MULTIDEX:
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }
    }

    @Test
    public void runBenchmarks() throws Exception {
        Map<String, AndroidProject> model = project.model().ignoreSyncIssues().getMulti();

        model.forEach(
                (path, project) ->
                        Truth.assertThat(
                                        project.getSyncIssues()
                                                .stream()
                                                .filter(
                                                        issue ->
                                                                issue.getSeverity()
                                                                        == SyncIssue.SEVERITY_ERROR)
                                                .collect(Collectors.toList()))
                                .isEmpty());

        // TODO: warm up (This is really slow already)

        project.executor()
                .withEnableInfoLogging(false)
                .recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN)
                .run("assembleDebug");

        project.executor()
                .withEnableInfoLogging(false)
                .recordBenchmark(BenchmarkMode.NO_OP)
                .run("assembleDebug");

    }
}
