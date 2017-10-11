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
import com.android.testutils.TestUtils;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.Benchmark;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;

public class ActdProfileUploaderTest {
    private static final File GIT_ROOT = TestUtils.getWorkspaceFile("tools/base");

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

    private static Logging.GradleBenchmarkResult randomBenchmarkResult() {
        return Logging.GradleBenchmarkResult.newBuilder()
                .setBenchmarkMode(random(BenchmarkMode.values()))
                .setBenchmark(random(Benchmark.values()))
                .setFlags(
                        Flags.newBuilder()
                                .setAapt(random(Flags.Aapt.values()))
                                .setBranch(random(Flags.Branch.values()))
                                .setCompiler(random(Flags.Compiler.values()))
                                .setJacoco(random(Flags.Jacoco.values()))
                                .setMinification(random(Flags.Minification.values())))
                .setProfile(GradleBuildProfile.newBuilder().setBuildTime(new Random().nextLong()))
                .build();
    }

    @Test
    public void hostname() throws IOException {
        assertThat(ActdProfileUploader.hostname()).isNotEmpty();
    }

    @Test
    public void lastCommitJson() throws IOException {
        assertThat(ActdProfileUploader.lastCommitJson(GIT_ROOT)).isNotEmpty();
    }

    @Test
    public void infos() throws IOException {
        ActdProfileUploader.Infos infos = ActdProfileUploader.infos();

        assertThat(infos.abbrevHash).isNotEmpty();
        assertThat(infos.hash).isNotEmpty();
        assertThat(infos.authorEmail).isNotEmpty();
        assertThat(infos.authorName).isNotEmpty();
        assertThat(infos.subject).isNotEmpty();
    }

    @Test
    public void flags() throws IOException {
        GradleBenchmarkResult gbr = randomBenchmarkResult();
        assertThat(ActdProfileUploader.flags(gbr)).isNotEmpty();

        // Make sure that we get the same result for the same object passed in multiple times.
        assertThat(ActdProfileUploader.flags(gbr)).isEqualTo(ActdProfileUploader.flags(gbr));
    }

    @Test
    public void seriesId() throws IOException {
        GradleBenchmarkResult gbr = randomBenchmarkResult();
        assertThat(ActdProfileUploader.seriesId(gbr)).isNotEmpty();

        // Make sure that we get the same result for the same object passed in multiple times.
        assertThat(ActdProfileUploader.seriesId(gbr)).isEqualTo(ActdProfileUploader.seriesId(gbr));
    }

    @Test
    public void description() throws IOException {
        GradleBenchmarkResult gbr = randomBenchmarkResult();
        assertThat(ActdProfileUploader.description(gbr)).isNotEmpty();
    }
}
