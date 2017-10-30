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

package com.android.build.gradle.integration.common.performance;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.performance.ActdProfileUploader;
import com.android.build.gradle.integration.performance.ActdProfileUploader.SampleRequest;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.common.collect.Lists;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import com.google.wireless.android.sdk.stats.GradleTaskExecution;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

public class ActdProfileUploaderTest {
    private final int buildId = 1234;
    private final String actdBaseUrl = "http://example.com";
    private final String actdProjectId = "test";
    private final String buildbotMasterUrl = "http://example.com";
    private final String buildbotBuilderName = "fred";

    private ActdProfileUploader uploader;

    @Before
    public void setUp() {
        uploader =
                ActdProfileUploader.create(
                        buildId,
                        actdBaseUrl,
                        actdProjectId,
                        buildbotMasterUrl,
                        buildbotBuilderName);

        ActdProfileUploader.BuildbotResponse buildInfo = new ActdProfileUploader.BuildbotResponse();
        buildInfo.sourceStamp.changes =
                new ActdProfileUploader.Change[] {new ActdProfileUploader.Change()};
        buildInfo.sourceStamp.changes[0].comments = "comments";
        buildInfo.sourceStamp.changes[0].rev = "00000000000000000000000000000000000000";
        buildInfo.sourceStamp.changes[0].revlink = "http://example.com";
        buildInfo.sourceStamp.changes[0].who = "you@google.com";
        uploader.setBuildInfo(buildInfo);
    }

    /**
     * Gets a random enum value for a given array of proto enums.
     *
     * <p>Example:
     *
     * <pre>
     *     BenchmarkMode bm = random(BenchmarkMode.values());
     * </pre>
     */
    private static <T extends ProtocolMessageEnum> T random(T[] array) {
        T t;
        while (true) {
            t = array[new Random().nextInt(array.length)];
            try {
                t.getNumber();
                return t;
            } catch (IllegalArgumentException e) {
                // it's not possible to call .getNumber() on the UNRECOGNIZED enum element, doing so
                // causes an IllegalArgumentException. We do nothing in this catch block in order
                // to loop again and grab a different random enum element.
            }
        }
    }

    private static long randomPositiveLong() {
        long value = Math.abs(new Random().nextLong());
        if (value == Long.MIN_VALUE) {
            value = Long.MAX_VALUE;
        }

        // to make sure samples aren't filtered out, we add the threshold if it's below it
        if (value < ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD) {
            value += ActdProfileUploader.BENCHMARK_VALUE_THRESHOLD;
        }

        return value;
    }

    private static Logging.GradleBenchmarkResult randomBenchmarkResult() {
        Flags flags =
                Flags.newBuilder()
                        .setAapt(random(Flags.Aapt.values()))
                        .setBranch(random(Flags.Branch.values()))
                        .setCompiler(random(Flags.Compiler.values()))
                        .setJacoco(random(Flags.Jacoco.values()))
                        .setMinification(random(Flags.Minification.values()))
                        .build();

        GradleTaskExecution.Builder task =
                GradleTaskExecution.newBuilder()
                        .setType(random(GradleTaskExecutionType.values()).getNumber());

        GradleBuildProfileSpan.Builder span =
                GradleBuildProfileSpan.newBuilder()
                        .setDurationInMs(randomPositiveLong())
                        .setTask(task);

        if (span.getTask().getType() == GradleTaskExecutionType.TRANSFORM_VALUE) {
            GradleTransformExecution.Builder transform =
                    GradleTransformExecution.newBuilder()
                            .setType(random(GradleTransformExecutionType.values()).getNumber());

            span.setTransform(transform);
        }

        GradleBuildProfile.Builder profile =
                GradleBuildProfile.newBuilder().setBuildTime(randomPositiveLong()).addSpan(span);

        return Logging.GradleBenchmarkResult.newBuilder()
                .setBenchmarkMode(random(BenchmarkMode.values()))
                .setBenchmark(random(Benchmark.values()))
                .setFlags(flags)
                .setProfile(profile)
                .build();
    }

    private static List<GradleBenchmarkResult> randomBenchmarkResults() {
        int count = new Random().nextInt(10) + 5; // must always be greater than 0
        List<GradleBenchmarkResult> results = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            results.add(randomBenchmarkResult());
        }
        return results;
    }

    @Test
    public void infos() throws IOException {
        ActdProfileUploader.Infos infos = uploader.infos();

        assertThat(infos.abbrevHash).isNotEmpty();
        assertThat(infos.abbrevHash).doesNotContain("\n");
        assertThat(infos.abbrevHash).doesNotContain(" ");
        assertThat(infos.abbrevHash.length()).isLessThan(infos.hash.length());

        assertThat(infos.hash).isNotEmpty();
        assertThat(infos.hash).doesNotContain("\n");
        assertThat(infos.hash).doesNotContain(" ");

        assertThat(infos.authorEmail).isNotEmpty();
        assertThat(infos.authorEmail).doesNotContain("\n");
        assertThat(infos.authorEmail).contains("@google.com");

        assertThat(infos.authorName).isNotEmpty();
        assertThat(infos.authorName).doesNotContain("\n");

        assertThat(infos.subject).isNotEmpty();
    }

    @Test
    public void infosManualTrigger() throws IOException {
        uploader.setBuildInfo(new ActdProfileUploader.BuildbotResponse());

        ActdProfileUploader.Infos infos = uploader.infos();

        assertThat(infos.abbrevHash).isNotEmpty();
        assertThat(infos.abbrevHash).doesNotContain("\n");
        assertThat(infos.abbrevHash).doesNotContain(" ");
        assertThat(infos.abbrevHash.length()).isLessThan(infos.hash.length());

        assertThat(infos.hash).isNotEmpty();
        assertThat(infos.hash).doesNotContain("\n");
        assertThat(infos.hash).doesNotContain(" ");

        assertThat(infos.authorEmail).isNotEmpty();
        assertThat(infos.authorEmail).doesNotContain("\n");
        assertThat(infos.authorEmail).contains("@google.com");

        assertThat(infos.authorName).isNotEmpty();
        assertThat(infos.authorName).doesNotContain("\n");

        assertThat(infos.subject).isNotEmpty();
    }

    @Test
    public void flags() {
        GradleBenchmarkResult gbr = randomBenchmarkResult();
        assertThat(ActdProfileUploader.flags(gbr)).isNotEmpty();

        // Make sure that we get the same result for the same object passed in multiple times.
        assertThat(ActdProfileUploader.flags(gbr)).isEqualTo(ActdProfileUploader.flags(gbr));
    }

    @Test
    public void seriesId() {
        GradleBenchmarkResult gbr = randomBenchmarkResult();

        assertThat(gbr.getProfile()).isNotNull();
        assertThat(gbr.getProfile().getSpanList()).isNotEmpty();

        for (GradleBuildProfileSpan span : gbr.getProfile().getSpanList()) {
            assertThat(uploader.seriesId(gbr, span)).isNotEmpty();

            // Make sure that we get the same result for the same object passed in multiple times.
            assertThat(uploader.seriesId(gbr, span)).isEqualTo(uploader.seriesId(gbr, span));
        }
    }

    @Test
    public void description() {
        GradleBenchmarkResult gbr = randomBenchmarkResult();

        assertThat(gbr.getProfile()).isNotNull();
        assertThat(gbr.getProfile().getSpanList()).isNotEmpty();

        for (GradleBuildProfileSpan span : gbr.getProfile().getSpanList()) {
            assertThat(ActdProfileUploader.description(gbr, span)).isNotNull();
        }
    }

    @Test
    public void sampleRequests() throws IOException {
        Collection<SampleRequest> reqs = uploader.sampleRequests(randomBenchmarkResults());
        assertThat(reqs).isNotEmpty();

        for (ActdProfileUploader.SampleRequest req : reqs) {
            // act-d baulks on 0 values, so we should not return them from sampleRequests()
            assertThat(req.sample.value).isGreaterThan(0L);

            // make sure the various IDs make sense
            assertThat(req.projectId).isNotEmpty();
            assertThat(req.sample.buildId).isGreaterThan(0L);
        }
    }

    @Test
    public void sampleRequestsEmpty() throws IOException {
        Collection<SampleRequest> reqs = uploader.sampleRequests(Arrays.asList());
        assertThat(reqs).isEmpty();
    }
}
