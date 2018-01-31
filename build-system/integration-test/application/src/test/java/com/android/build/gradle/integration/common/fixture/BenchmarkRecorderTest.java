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

package com.android.build.gradle.integration.common.fixture;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.performance.BenchmarkRecorder;
import com.android.build.gradle.integration.performance.BuildbotClient;
import com.android.build.gradle.integration.performance.ProjectScenario;
import com.android.testutils.TestUtils;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BenchmarkRecorderTest {
    private final List<GradleBenchmarkResult> benchmarkResults = new ArrayList<>();

    HelloWorldApp app = KotlinHelloWorldApp.forPlugin("com.android.application");
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(app).enableProfileOutput().create();

    BenchmarkRecorder recorder;

    ProjectScenario scenario = ProjectScenario.DEX_OUT_OF_PROCESS;
    Benchmark benchmark = Benchmark.ANTENNA_POD;

    @Before
    public void setUp() throws Exception {
        recorder =
                new BenchmarkRecorder(
                        new ProfileCapturer(project),
                        Arrays.asList(benchmarkResults::addAll),
                        BuildbotClient.forTesting(Collections.emptyList()));
    }

    @Test
    public void checkPerformanceDataGiven() throws Throwable {
        Timestamp minimumTimestamp = Timestamps.fromMillis(System.currentTimeMillis());
        int currentYear = LocalDateTime.now().getYear();

        // Because the profiler deals with lots of static state in the
        // plugin, make sure a sequence of actions with and without the
        // profiler enabled work correctly.
        recorder.record(
                scenario,
                benchmark,
                BenchmarkMode.SYNC,
                () -> project.model().fetchAndroidProjects());
        project.model().fetchAndroidProjects();
        recorder.record(
                scenario,
                benchmark,
                BenchmarkMode.BUILD__FROM_CLEAN,
                () -> project.execute("assembleDebug"));
        project.executor().run("assembleDebug");
        recorder.record(
                scenario, benchmark, BenchmarkMode.NO_OP, () -> project.execute("assembleDebug"));

        recorder.uploadAsync();
        BenchmarkRecorder.awaitUploads(5, TimeUnit.SECONDS);

        assertThat(benchmarkResults).hasSize(3);
        assertThat(benchmarkResults.get(0).getBenchmarkMode())
                .isEqualTo(Logging.BenchmarkMode.SYNC);
        assertThat(benchmarkResults.get(1).getBenchmarkMode())
                .isEqualTo(Logging.BenchmarkMode.BUILD__FROM_CLEAN);
        assertThat(benchmarkResults.get(2).getBenchmarkMode())
                .isEqualTo(Logging.BenchmarkMode.NO_OP);
        assertThat(benchmarkResults.get(0).getProfile().getSpanCount()).isGreaterThan(0);
        assertThat(benchmarkResults.get(1).getProfile().getSpanCount()).isGreaterThan(0);
        assertThat(benchmarkResults.get(2).getProfile().getSpanCount()).isGreaterThan(0);

        // Check that the variant info is populated
        GradleBuildProject aProject = benchmarkResults.get(0).getProfile().getProject(0);
        assertThat(aProject.getCompileSdk()).isEqualTo(GradleTestProject.getCompileSdkHash());
        assertThat(aProject.getKotlinPluginVersion())
                .isEqualTo(TestUtils.getKotlinVersionForTests());
        GradleBuildVariant aVariant = aProject.getVariant(0);
        assertThat(aVariant.getMinSdkVersion().getApiLevel()).isEqualTo(3);
        assertThat(aVariant.hasTargetSdkVersion()).named("has target sdk version").isFalse();
        assertThat(aVariant.hasMaxSdkVersion()).named("has max sdk version").isFalse();

        // Check that the timestamps are sane
        assertThat(benchmarkResults.get(0).getTimestamp().getSeconds())
                .isAtLeast(minimumTimestamp.getSeconds());
        assertThat(benchmarkResults.get(1).getTimestamp())
                .isEqualTo(benchmarkResults.get(0).getTimestamp());
        assertThat(benchmarkResults.get(2).getTimestamp())
                .isEqualTo(benchmarkResults.get(0).getTimestamp());

        // The next bits are to make sure nothing crazy goes wrong with the dates. I accidentally
        // introduced a bug once that set all of the dates to some time in 1970, so this would
        // guard against that sort of thing.
        LocalDateTime minDate =
                LocalDateTime.ofEpochSecond(minimumTimestamp.getSeconds(), 0, ZoneOffset.UTC);
        assertThat(minDate.getYear()).isEqualTo(currentYear);

        for (GradleBenchmarkResult result : benchmarkResults) {
            LocalDateTime date =
                    LocalDateTime.ofEpochSecond(
                            result.getTimestamp().getSeconds(), 0, ZoneOffset.UTC);
            assertThat(date.getYear()).isEqualTo(currentYear);
        }
    }
}
