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

import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.base.Strings;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
/**
 * This is the main entry point for our continuous, buildbot-run Gradle benchmarks. These tests are
 * run for every commit submitted to the tools/base subtree.
 *
 * <p>Running locally:
 *
 * <pre>
 *   $ cd $SRC/tools
 *   $ ./gradlew --info :base:build-system:integration-test:application:performanceTest \
 *      -D:base:build-system:integration-test:application:performanceTest.single=BenchmarkTest
 * </pre>
 *
 * There are also a bunch of environment variables you can use to limit the set of tests run.
 *
 * <pre>
 *   PERF_SHARD_NUM and PERF_SHARD_TOTAL
 *
 *      These control the "sharding" of the tests. Each test is a self-contained entity that can be
 *      run independently. Because of the trait, we're able to split the full set of tests up and
 *      run subsets, which is useful for running these tests across multiple machines. Shards are
 *      always deterministic, e.g. if you specify the same PERF_SHARD_NUM and PERF_SHARD_TOTAL,
 *      you will get the same subset of tests every time.
 *
 *   PERF_SCENARIO
 *
 *      This controls the ProjectScenario. If you only want to run tests using
 *      ProjectScenario.NATIVE_MULTIDEX, for example, you can specify PERF_SCENARIO=NATIVE_MULTIDEX.
 *      See {@code ProjectScenario} for more options.
 *
 *   PERF_BENCHMARK
 *
 *      This controls the Benchmark. Confusingly named, you can think of a "Benchmark" in this
 *      context as a project, e.g. AntennaPod or WordPress. See {@code Logging.Benchmark} for the
 *      options here, but an example would be PERF_BENCHMARK=PERF_ANDROID_MEDIUM.
 *
 *   PERF_BENCHMARK_MODE
 *
 *      This controls the BenchmarkMode. If you're only interested in how long it takes to build
 *      from clean, for example, you could set PERF_BENCHMARK_MODE=BUILD__FROM_CLEAN. See
 *      {@code Logging.BenchmarkMode} for more examples.
 * </pre>
 *
 * You can set all or none of these environment variables to run whatever subset of benchmarks you
 * want. Setting none of them will default to running the full set of benchmarks.
 */
public class BenchmarkTest {
    @Test
    public void run() throws Exception {
        if (GradleTestProject.USE_LATEST_NIGHTLY_GRADLE_VERSION
                && GradleTestProject.GRADLE_TEST_VERSION.equals(
                        BasePlugin.GRADLE_MIN_VERSION.toString())) {
            String msg =
                    "Running tests with gradle nightly skipped as the minimum plugin version is equal to the latest nightly version.";
            System.err.println(msg);
            throw new AssumptionViolatedException(msg);
        }

        long start = System.nanoTime();
        List<Benchmark> benchmarks = new ArrayList<>();

        /*
         * Add all Benchmark objects to the list here.
         */
        benchmarks.addAll(AntennaPodBenchmarks.INSTANCE.get());
        benchmarks.addAll(LargeGradleProjectBenchmarks.INSTANCE.get());
        benchmarks.addAll(MediumGradleProjectBenchmarks.INSTANCE.get());

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
            int before = shard.size();
            shard = filterByScenario(shard, scenario);
            int after = shard.size();
            System.out.println(
                    "Filtered by ProjectScenario="
                            + scenario
                            + ", excluded "
                            + (before - after)
                            + " benchmarks");
        }

        String benchmark = System.getenv("PERF_BENCHMARK");
        if (!Strings.isNullOrEmpty(benchmark)) {
            int before = shard.size();
            shard = filterByBenchmark(shard, benchmark);
            int after = shard.size();
            System.out.println(
                    "Filtered by Benchmark="
                            + benchmark
                            + ", excluded "
                            + (before - after)
                            + " benchmarks");
        }

        String benchmarkMode = System.getenv("PERF_BENCHMARK_MODE");
        if (!Strings.isNullOrEmpty(benchmarkMode)) {
            int before = shard.size();
            shard = filterByBenchmarkMode(shard, benchmarkMode);
            int after = shard.size();
            System.out.println(
                    "Filtered by BenchmarkMode="
                            + benchmarkMode
                            + ", excluded "
                            + (before - after)
                            + " benchmarks");
        }

        if (shard.isEmpty()) {
            System.out.println("Found no benchmarks to run, exiting");
            return;
        }

        /*
         * After all of the sharding and filtering, we want to randomise the order that the
         * benchmarks run in, in order to make sure there are no temporal dependencies between them.
         *
         * We generate a seed beforehand and print it to the logs to make temporal dependencies
         * easier to debug when they happen, as the user will then be able to reproduce the order
         * the benchmarks were run in by setting the PERF_SHUFFLE_SEED environment variable.
         */
        Long shuffleSeed = shuffleSeed();
        System.out.println(
                "Shuffling benchmarks with seed: "
                        + shuffleSeed
                        + ", specify PERF_SHUFFLE_SEED="
                        + shuffleSeed
                        + " when re-running locally if you need to ensure that the benchmarks run in the same order");
        Collections.shuffle(shard, new Random(shuffleSeed));

        System.out.println("Running " + shard.size() + " benchmarks");
        Duration benchmarkDuration = Duration.ofNanos(0);
        Duration recordedDuration = Duration.ofNanos(0);

        /*
         * Run the benchmarks.
         */
        int i = 0;
        System.out.println();
        List<BenchmarkResult> failed = new ArrayList<>();
        for (Benchmark b : shard) {
            i++;
            BenchmarkResult result = b.run();

            benchmarkDuration = benchmarkDuration.plus(result.getTotalDuration());
            recordedDuration = recordedDuration.plus(result.getRecordedDuration());

            System.out.println(i + "/" + shard.size());
            System.out.println(result);
            System.out.println();

            if (result.getException() != null) {
                failed.add(result);
            }
        }

        BenchmarkRecorder.awaitUploads(15, TimeUnit.MINUTES);

        Duration totalDuration = Duration.ofNanos(System.nanoTime() - start);
        System.out.println("Total recorded duration: " + recordedDuration);
        System.out.println("Total benchmark duration: " + benchmarkDuration);
        System.out.println("Overall duration: " + totalDuration);

        if (!failed.isEmpty()) {
            System.out.println();
            System.out.println("Some benchmarks did not complete successfully: ");
            System.out.println();

            for (BenchmarkResult result : failed) {
                System.out.println(result);
                System.out.println();
                System.out.println("full stack trace:");
                result.getException().printStackTrace();
                System.out.println();
                System.out.println("Command to re-run locally: " + result.getBenchmark().command());
            }

            throw new AssertionError(failed.size() + " benchmarks failed");
        }
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

    private static Long shuffleSeed() {
        String shuffleSeed = System.getenv("PERF_SHUFFLE_SEED");
        if (!Strings.isNullOrEmpty(shuffleSeed)) {
            return Long.parseLong(shuffleSeed);
        }

        return ThreadLocalRandom.current().nextLong();
    }
}
