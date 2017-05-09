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
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
    public static ProjectScenario[] getParameters() {
        return new ProjectScenario[] {
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
            //ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
        };
    }

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeUberSkeleton(project);
        switch (projectScenario.getFlags().getMultiDex()) {
            case LEGACY:
                break;
            case NATIVE:
                TestFileUtils.searchAndReplace(
                        project.file("dependencies.gradle"), "(minSdkVersion *): \\d+,", "$1: 21,");
                break;
            default:
                throw new IllegalArgumentException("Unknown project scenario" + projectScenario);
        }
    }

    @Test
    public void runBenchmarks() throws Exception {
        Map<String, AndroidProject> models =
                project.model().ignoreSyncIssues().getMulti().getModelMap();

        PerformanceTestProjects.assertNoSyncErrors(models);

        // warm-up
        executor().run("clean");
        executor().run(":phthalic:assembleDebug");
        executor().run("clean");

        // recording data
        executor().recordBenchmark(BenchmarkMode.EVALUATION).run("tasks");
        executor().recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN).run(":phthalic:assembleDebug");
        executor().recordBenchmark(BenchmarkMode.NO_OP).run(":phthalic:assembleDebug");

        executor().run("clean");
        project.model().ignoreSyncIssues().recordBenchmark(BenchmarkMode.SYNC).getMulti();

        executor()
                .recordBenchmark(BenchmarkMode.GENERATE_SOURCES)
                .run(ModelHelper.getDebugGenerateSourcesCommands(models));

    }

    private RunGradleTasks executor() {
        return project.executor()
                .withEnableInfoLogging(false)
                .withUseDexArchive(projectScenario.useDexArchive())
                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                .with(BooleanOption.ENABLE_AAPT2, false)
                .withoutOfflineFlag();
    }
}
