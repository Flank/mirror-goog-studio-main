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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.category.PerformanceTests;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.concurrent.NotThreadSafe;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
 *
 *   RUNS_PER_BENCHMARK
 *
 *      In order to get a good, clear signal of how long a benchmark really takes you need to run
 *      the benchmark more than once. This environment variable tells the code how many times you
 *      want to run each benchmark, and you should assume that the lowest value is the best signal.
 *
 *   PERF_SKIP_CLEANUP
 *
 *      Set this to any non-null value in order to leave environment files around when running this
 *      from the standalone jar.
 * </pre>
 *
 * You can set all or none of these environment variables to run whatever subset of benchmarks you
 * want. Setting none of them will default to running the full set of benchmarks.
 */
@NotThreadSafe
public class BenchmarkTest {
    @Nullable private static List<ProfileUploader> UPLOADERS;
    @Nullable private static BenchmarkEnvironment BENCHMARK_ENVIRONMENT;

    public static void main(String... args) throws Exception {
        Path tmp = Files.createTempDirectory("BenchmarkTest");

        try {
            System.out.println("inflating benchmark environment from jar...");
            BENCHMARK_ENVIRONMENT = BenchmarkEnvironment.fromJar(tmp);
            BenchmarkTest benchmarkTest = new BenchmarkTest();
            System.out.println("done, beginning benchmark tests...");
            benchmarkTest.run();
        } finally {
            try {
                if (System.getenv("PERF_SKIP_CLEANUP") == null) {
                    MoreFiles.deleteRecursively(tmp);
                }
            } catch (IOException e) {
                // we tried
            }
        }
    }

    @NonNull
    private static List<ProfileUploader> getUploaders() {
        if (UPLOADERS == null) {
            List<ProfileUploader> uploaders = new ArrayList<>();

            try {
                uploaders.add(GoogleStorageProfileUploader.getInstance());
            } catch (Exception e) {
                System.out.println(
                        "couldn't add GoogleStorageProfileUploader to the list of default uploaders, reason: "
                                + e
                                + ", this means that benchmark results will not be uploaded to GCS");
            }

            UPLOADERS = ImmutableList.copyOf(uploaders);
        }

        return UPLOADERS;
    }

    @Test
    @Category(PerformanceTests.class)
    public void run() {
        if (BENCHMARK_ENVIRONMENT == null) {
            BENCHMARK_ENVIRONMENT = BenchmarkEnvironment.fromRepo();
        }

        System.out.println("running benchmarks with BenchmarkEnvironment:");
        System.out.println(BENCHMARK_ENVIRONMENT);
        System.out.println();

        BenchmarkRunner benchmarkRunner =
                new BenchmarkRunner(getUploaders(), new StandardBenchmarks().get());
        benchmarkRunner.run();
    }

    /*
     * Shared logic to supply the complete list of benchmarks to both the standalone and JUnit codepaths
     */
    private static class StandardBenchmarks implements Supplier<List<Benchmark>> {

        @Override
        public List<Benchmark> get() {
            List<Benchmark> benchmarks = new ArrayList<>();
            benchmarks.addAll(new AntennaPodBenchmarks(BENCHMARK_ENVIRONMENT).get());
            benchmarks.addAll(new LargeGradleProjectBenchmarks(BENCHMARK_ENVIRONMENT).get());
            benchmarks.addAll(new MediumGradleProjectBenchmarks(BENCHMARK_ENVIRONMENT).get());
            return benchmarks;
        }
    }
}
