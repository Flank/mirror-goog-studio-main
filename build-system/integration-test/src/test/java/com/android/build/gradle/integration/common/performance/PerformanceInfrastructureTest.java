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

package com.android.build.gradle.integration.common.performance;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.build.gradle.integration.performance.ProjectScenario;
import com.android.ide.common.util.ReferenceHolder;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Smoke test for the performance test infrastructure. */
public class PerformanceInfrastructureTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    private final List<Logging.GradleBenchmarkResult> benchmarkResults = new ArrayList<>();

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void checkPerformanceDataGiven() throws Throwable {

        GradleTestProject project =
                GradleTestProject.builder()
                        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                        .forBenchmarkRecording(
                                new BenchmarkRecorder(
                                        Logging.Benchmark.ANTENNA_POD,
                                        ProjectScenario.DEX_OUT_OF_PROCESS,
                                        benchmarkResults::addAll))
                        .create();

        // Manually evaluate the project rule, so we can assert about the uploads that happen after
        // the evaluation is complete.

        ReferenceHolder<Timestamp> minimumTimestamp = ReferenceHolder.empty();

        project.apply(
                        new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                // Because the profiler deals with lots of static state in the
                                // plugin, make sure a sequence of actions with and without the
                                // profiler enabled work correctly.
                                project.model().recordBenchmark(BenchmarkMode.SYNC).getSingle();
                                project.model().getSingle();
                                project.executor()
                                        .recordBenchmark(BenchmarkMode.BUILD__FROM_CLEAN)
                                        .run("assembleDebug");
                                project.executor().run("assembleDebug");
                                project.executor()
                                        .recordBenchmark(BenchmarkMode.NO_OP)
                                        .run("assembleDebug");
                                minimumTimestamp.setValue(
                                        Timestamps.fromMillis(System.currentTimeMillis()));
                            }
                        },
                        Description.createTestDescription(
                                PerformanceInfrastructureTest.class, "checkPerformanceDataGiven"))
                .evaluate();

        assertThat(benchmarkResults).hasSize(3);
        assertThat(benchmarkResults.get(0).getBenchmarkMode()).isEqualTo(BenchmarkMode.SYNC);
        assertThat(benchmarkResults.get(1).getBenchmarkMode())
                .isEqualTo(BenchmarkMode.BUILD__FROM_CLEAN);
        assertThat(benchmarkResults.get(2).getBenchmarkMode()).isEqualTo(BenchmarkMode.NO_OP);
        assertThat(benchmarkResults.get(0).getProfile().getSpanCount()).isGreaterThan(0);
        assertThat(benchmarkResults.get(1).getProfile().getSpanCount()).isGreaterThan(0);
        assertThat(benchmarkResults.get(2).getProfile().getSpanCount()).isGreaterThan(0);

        // Check that the variant info is populated
        GradleBuildProject aProject = benchmarkResults.get(0).getProfile().getProject(0);
        assertThat(aProject.getCompileSdk()).isEqualTo(GradleTestProject.getCompileSdkHash());
        GradleBuildVariant aVariant = aProject.getVariant(0);
        assertThat(aVariant.getMinSdkVersion().getApiLevel()).isEqualTo(3);
        assertThat(aVariant.hasTargetSdkVersion()).named("has target sdk version").isFalse();
        assertThat(aVariant.hasMaxSdkVersion()).named("has max sdk version").isFalse();
        // Check that the timestamp is written after the build.
        assertThat(benchmarkResults.get(0).getTimestamp().getSeconds())
                .isAtLeast(minimumTimestamp.getValue().getSeconds());
        assertThat(benchmarkResults.get(1).getTimestamp())
                .isEqualTo(benchmarkResults.get(0).getTimestamp());
        assertThat(benchmarkResults.get(2).getTimestamp())
                .isEqualTo(benchmarkResults.get(0).getTimestamp());
    }
}
