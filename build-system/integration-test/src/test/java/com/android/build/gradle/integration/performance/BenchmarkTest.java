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

package com.android.build.gradle.integration.performance;

import com.google.common.base.Strings;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BenchmarkTest {
    @Test
    public void run() {
        List<Benchmark> benchmarks = new ArrayList<>();

        /*
         * Add all Benchmark objects to the list here.
         */
        benchmarks.addAll(AntennaPodBenchmarks.INSTANCE.get());
        benchmarks.addAll(MediumGradleProjectBenchmarks.INSTANCE.get());
        benchmarks.addAll(LargeGradleProjectBenchmarks.INSTANCE.get());

        /*
         * We sort the benchmarks to make sure they're in a predictable, stable order. This is
         * important because when we shard the running of these tests across machines, all of the
         * machines need this list to be in the same order so that they can run a non-overlapping
         * subset of them.
         */
        Collections.sort(
                benchmarks,
                Comparator.comparing(Benchmark::getScenario)
                        .thenComparing(Benchmark::getBenchmark)
                        .thenComparing(Benchmark::getBenchmarkMode));

        /*
         * Figure out which shard we are, or default to being the only shard, and then get the
         * correct sub-list of benchmarks to run based on that information.
         */
        int shardNum = Integer.parseInt(env("PERF_SHARD_NUM", "0"));
        int shardTotal = Integer.parseInt(env("PERF_SHARD_TOTAL", "1"));
        List<Benchmark> shard = PerformanceTestUtil.shard(benchmarks, shardNum, shardTotal);

        System.out.println(
                "got shard of size "
                        + shard.size()
                        + " with parameters PERF_SHARD_NUM="
                        + shardNum
                        + " and PERF_SHARD_TOTAL="
                        + shardTotal);

        /*
         * Allow the user to filter by scenario, benchmark and benchmarkMode. This makes it
         * easier to run specific benchmarks locally.
         */
        String scenario = System.getenv("PERF_SCENARIO");
        if (!Strings.isNullOrEmpty(scenario)) {
            System.out.println("filtering by ProjectScenario=" + scenario);
            shard = filterByScenario(shard, scenario);
        }

        String benchmark = System.getenv("PERF_BENCHMARK");
        if (!Strings.isNullOrEmpty(benchmark)) {
            System.out.println("filtering by Benchmark=" + benchmark);
            shard = filterByBenchmark(shard, benchmark);
        }

        String benchmarkMode = System.getenv("PERF_BENCHMARK_MODE");
        if (!Strings.isNullOrEmpty(benchmarkMode)) {
            System.out.println("filtering by BenchmarkMode=" + benchmarkMode);
            shard = filterByBenchmarkMode(shard, benchmarkMode);
        }

        if (shard.isEmpty()) {
            System.out.println("found no benchmarks to run, exiting");
            return;
        }

        System.out.println("running " + shard.size() + " benchmarks");
        /*
         * Run the benchmarks.
         */
        shard.forEach(Benchmark::run);
    }

    public static List<Benchmark> filterByScenario(List<Benchmark> benchmarks, String scenarioStr) {
        ProjectScenario scenario = ProjectScenario.valueOf(scenarioStr);
        return benchmarks
                .stream()
                .filter(b -> b.getScenario().equals(scenario))
                .collect(Collectors.toList());
    }

    public static List<Benchmark> filterByBenchmark(
            List<Benchmark> benchmarks, String benchmarkStr) {
        Logging.Benchmark benchmark = Logging.Benchmark.valueOf(benchmarkStr);
        return benchmarks
                .stream()
                .filter(b -> b.getBenchmark().equals(benchmark))
                .collect(Collectors.toList());
    }

    public static List<Benchmark> filterByBenchmarkMode(
            List<Benchmark> benchmarks, String benchmarkModeStr) {
        Logging.BenchmarkMode benchmarkMode = Logging.BenchmarkMode.valueOf(benchmarkModeStr);
        return benchmarks
                .stream()
                .filter(b -> b.getBenchmarkMode().equals(benchmarkMode))
                .collect(Collectors.toList());
    }

    private static String env(String name, String def) {
        String val = System.getenv(name);
        if (Strings.isNullOrEmpty(val)) {
            val = def;
        }
        return val;
    }
}
