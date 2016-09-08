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

import com.android.build.gradle.integration.common.fixture.GradleProfileUploader;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.ide.common.util.ReferenceHolder;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/**
 * Smoke test for the performance test infrastructure.
 */
public class PerformanceInfrastructureTest {

    private static final GradleProfileUploader.Uploader ASSERT_NO_UPLOAD =
            profile -> {
                throw new AssertionError("Should not be called");
            };

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void checkPerformanceDataGiven() throws IOException {
        // Because the profiler deals with lots of static state, make sure a sequence of actions
        // with and without the profiler enabled work correctly.
        getModelWithProfile();
        getModelWithoutProfile();
        buildWithProfile();
        buildWithoutProfile();
        buildWithProfile();
    }

    private void getModelWithProfile() throws IOException {
        ReferenceHolder<Logging.GradleBenchmarkResult> result = ReferenceHolder.empty();

        GradleProfileUploader.setUploader(result::setValue);

        project.model().recordBenchmark(Benchmark.ANTENNA_POD, BenchmarkMode.SYNC).getSingle();

        assertThat(result.getValue().getProfile().getSpanCount()).isGreaterThan(0);

    }

    private void getModelWithoutProfile() {
        GradleProfileUploader.setUploader(ASSERT_NO_UPLOAD);
        project.model().getSingle();
    }

    private void buildWithProfile() throws IOException {
        ReferenceHolder<Logging.GradleBenchmarkResult> result = ReferenceHolder.empty();

        GradleProfileUploader.setUploader(result::setValue);

        project.executor()
                .recordBenchmark(Benchmark.ANTENNA_POD, BenchmarkMode.BUILD_FULL)
                .run("assembleDebug");

        assertThat(result.getValue().getProfile().getSpanCount()).isGreaterThan(0);
    }

    private void buildWithoutProfile() {
        GradleProfileUploader.setUploader(ASSERT_NO_UPLOAD);
        project.executor().run("assembleDebug");
    }

}
