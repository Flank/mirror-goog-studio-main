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
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.util.Map;
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
                        .withHeap("1536M")
                        .create();
    }

    @Parameterized.Parameters(name = "{0}")
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[] {
            ProjectScenario.LEGACY_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
            ProjectScenario.NATIVE_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeWordpress(project);
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
            default:
                throw new IllegalArgumentException(
                        "Unknown project scenario" + projectScenario);
        }
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
                .withUseDexArchive(projectScenario.useDexArchive());
    }
}
