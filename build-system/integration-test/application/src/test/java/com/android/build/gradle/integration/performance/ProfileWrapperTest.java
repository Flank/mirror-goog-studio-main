/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

public class ProfileWrapperTest {
    @Test
    public void testWrapperProtoMakesSense() {
        Logging.GradleBenchmarkResult result =
                new ProfileWrapper()
                        .wrap(
                                GradleBuildProfile.newBuilder().build(),
                                Logging.Benchmark.ANTENNA_POD,
                                Logging.BenchmarkMode.BUILD__FROM_CLEAN,
                                ProjectScenario.D8_LEGACY_MULTIDEX);

        assertThat(result.getHostname()).isNotEmpty();
        assertThat(result.getBenchmark()).isEqualTo(Logging.Benchmark.ANTENNA_POD);
        assertThat(result.getBenchmarkMode()).isEqualTo(Logging.BenchmarkMode.BUILD__FROM_CLEAN);
        assertThat(result.getFlags()).isEqualTo(ProjectScenario.D8_LEGACY_MULTIDEX.getFlags());
        assertThat(result.getUsername()).isNotEmpty();
        assertThat(result.hasProfile()).isTrue();

        // The next bits are to make sure nothing crazy goes wrong with the dates. I accidentally
        // introduced a bug once that set all of the dates to some time in 1970, so this would
        // guard against that sort of thing.
        assertThat(
                        LocalDateTime.ofEpochSecond(
                                result.getTimestamp().getSeconds(), 0, ZoneOffset.UTC))
                .isGreaterThan(LocalDateTime.now().minus(Duration.ofDays(1)));
    }
}
