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

import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.RandomGradleBenchmark;
import com.android.build.gradle.integration.performance.BuildbotClient;
import com.android.build.gradle.integration.performance.DanaProfileUploader;
import com.android.build.gradle.integration.performance.DanaProfileUploader.Infos;
import com.android.build.gradle.integration.performance.DanaProfileUploader.SampleRequest;
import com.android.build.gradle.integration.performance.DanaProfileUploader.SerieRequest;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;

public class DanaProfileUploaderTest {
    private final String danaBaseUrl = "http://example.com";
    private final String danaProjectId = "test";
    private final String buildbotMasterUrl = "http://example.com";
    private final String buildbotBuilderName = "fred";

    private DanaProfileUploader uploader;
    private BuildbotClient client;

    private static class FakeBuildbotClient extends BuildbotClient {
        private final List<Change> changes;

        public FakeBuildbotClient(List<Change> changes) {
            super("", "");
            this.changes = changes;
        }

        @Override
        public List<Change> getChanges(long buildId) {
            return this.changes;
        }
    }

    @Before
    public void setUp() throws IOException {
        BuildbotClient.Change change = new BuildbotClient.Change();
        change.who = "samwho@google.com";
        change.at = "Fri 26 Jan 2018 05:34:29";
        change.revision = "ad751a1a723307f54f3f50ab3a1e61c685dbd124";
        change.revlink =
                "https://googleplex-android-review.git.corp.google.com/#/q/ad751a1a723307f54f3f50ab3a1e61c685dbd124";
        change.comments = "Be the change you want to see.";

        client = new FakeBuildbotClient(ImmutableList.of(change));

        uploader =
                DanaProfileUploader.create(
                        danaBaseUrl,
                        danaProjectId,
                        buildbotMasterUrl,
                        buildbotBuilderName,
                        DanaProfileUploader.Mode.NORMAL,
                        client);
    }

    private static void assertValidInfos(@Nullable Infos infos) {
        assertThat(infos).isNotNull();

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
    public void infos() throws ExecutionException {
        assertValidInfos(uploader.infos(1));
    }

    @Test
    public void flags() {
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

        // Make sure that we get the same result for the same object passed in multiple times.
        assertThat(DanaProfileUploader.flags(gbr)).isEqualTo(DanaProfileUploader.flags(gbr));
    }

    @Test
    public void seriesId() {
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

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
        GradleBenchmarkResult gbr = RandomGradleBenchmark.randomBenchmarkResult();

        assertThat(gbr.getProfile()).isNotNull();
        assertThat(gbr.getProfile().getSpanList()).isNotEmpty();

        for (GradleBuildProfileSpan span : gbr.getProfile().getSpanList()) {
            assertThat(DanaProfileUploader.description(gbr, span)).isNotNull();
        }
    }

    @Test
    public void sampleRequests() {
        Collection<SampleRequest> reqs =
                uploader.sampleRequests(RandomGradleBenchmark.randomBenchmarkResults());
        assertThat(reqs).isNotEmpty();

        for (DanaProfileUploader.SampleRequest req : reqs) {
            // make sure the various IDs make sense
            assertThat(req.projectId).isNotEmpty();

            for (DanaProfileUploader.Sample sample : req.samples) {
                // act-d baulks on 0 values, so we should not return them from sampleRequests()
                assertThat(sample.value).isGreaterThan(0L);

                assertThat(sample.buildId).isGreaterThan(0L);
                assertThat(sample.url).isNotNull();
            }
        }
    }

    @Test
    public void sampleRequestsEmpty() {
        Collection<SampleRequest> reqs = uploader.sampleRequests(Collections.emptyList());
        assertThat(reqs).isEmpty();
    }

    @Test
    public void buildRequest() throws ExecutionException {
        DanaProfileUploader.BuildRequest buildRequest = uploader.buildRequest(1);

        assertThat(buildRequest.build).isNotNull();
        assertThat(buildRequest.build.buildId).isGreaterThan(0L);
        assertThat(buildRequest.projectId).isNotEmpty();

        assertValidInfos(buildRequest.build.infos);
    }

    @Test
    public void serieRequests() {
        Collection<SampleRequest> sampleRequests =
                uploader.sampleRequests(RandomGradleBenchmark.randomBenchmarkResults());
        assertThat(sampleRequests).isNotEmpty();

        Collection<SerieRequest> serieRequests = uploader.serieRequests(sampleRequests);
        assertThat(serieRequests).isNotEmpty();

        for (SerieRequest serieRequest : serieRequests) {
            assertThat(serieRequest.projectId).isNotEmpty();
            assertThat(serieRequest.serieId).isNotEmpty();
        }

        long numIds = serieRequests.stream().map(s -> s.serieId).distinct().count();
        assertThat(numIds).isEqualTo(serieRequests.size()); // all series should have a unique id
    }
}
