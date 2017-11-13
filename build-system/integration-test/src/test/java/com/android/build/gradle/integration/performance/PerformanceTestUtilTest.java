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

import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class PerformanceTestUtilTest {

    @Test
    public void checkGetEditType() {
        Set<PerformanceTestUtil.EditType> editTypes =
                EnumSet.allOf(PerformanceTestUtil.EditType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.EditType editType =
                        PerformanceTestUtil.getEditType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(editType.name());
                editTypes.remove(editType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have edit types.
            }
        }

        assertThat(editTypes).named("Edit types without a benchmark mode").isEmpty();
    }

    @Test
    public void checkGetgetSubProjectType() {
        Set<PerformanceTestUtil.SubProjectType> subprojectTypes =
                EnumSet.allOf(PerformanceTestUtil.SubProjectType.class);
        for (Logging.BenchmarkMode mode: PerformanceTestUtil.BENCHMARK_MODES) {
            try {
                PerformanceTestUtil.SubProjectType subProjectType =
                        PerformanceTestUtil.getSubProjectType(mode);
                // This is essentially a typo check.
                assertThat(mode.name()).contains(subProjectType.name());
                subprojectTypes.remove(subProjectType);
            } catch (IllegalStateException ignored) {
                // Some benchmark modes don't have subproject types types.
            }
        }

        assertThat(subprojectTypes).named("Subproject types without a benchmark mode").isEmpty();
    }

    /**
     * This test makes sure that for many different list sizes, all shards reconstruct back to the
     * original list.
     */
    @Test
    public void shardCorrectnessTest() {
        for (int listSize = 0; listSize < 20; listSize++) {
            List<Integer> list = new ArrayList<>(listSize);
            for (int i = 0; i < listSize; i++) {
                list.add(i);
            }

            for (int numShards = 1; numShards <= listSize; numShards++) {
                List<Integer> sharded = new ArrayList<>(listSize);
                for (int i = 0; i < numShards; i++) {
                    sharded.addAll(PerformanceTestUtil.shard(list, i, numShards));
                }

                assertThat(sharded).containsExactlyElementsIn(list);
            }
        }
    }

    /**
     * This test makes sure that shards are equally balanced. We don't want any shards to be
     * significantly larger than the others.
     */
    @Test
    public void shardBalanceTest() {
        for (int listSize = 1; listSize < 20; listSize++) {
            List<Integer> list = new ArrayList<>(listSize);
            for (int i = 0; i < listSize; i++) {
                list.add(i);
            }

            for (int numShards = 1; numShards <= listSize; numShards++) {
                List<Integer> shardSizes = new ArrayList<>(listSize);
                for (int i = 0; i < numShards; i++) {
                    shardSizes.add(PerformanceTestUtil.shard(list, i, numShards).size());
                }

                int range = Collections.max(shardSizes) - Collections.min(shardSizes);
                assertThat(range).isLessThan(2);
            }
        }
    }
}
