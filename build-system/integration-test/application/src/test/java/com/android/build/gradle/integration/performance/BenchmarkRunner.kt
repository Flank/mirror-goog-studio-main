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

package com.android.build.gradle.integration.performance

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import org.junit.AssumptionViolatedException
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier
import java.util.stream.Collectors

class BenchmarkRunner(
    val uploaders: List<ProfileUploader> = listOf(),
    benchmarks: List<Benchmark>) {

    private val benchmarks: MutableList<Benchmark> = benchmarks.toMutableList()
    private val shuffleSeed: Long

    init {
        val shuffleSeedEnv = System.getenv("PERF_SHUFFLE_SEED")
        shuffleSeed = if (!shuffleSeedEnv.isNullOrEmpty()) shuffleSeedEnv.toLong() else ThreadLocalRandom.current().nextLong()
    }

    fun run() {
        /*
         * Sometimes the version of Gradle checked in to our repo is the same as their current
         * nightly version. In this scenario, we want to avoid running the same set of tests twice
         * and wasting lots of time, so we just bail out at this point.
         */
        if (GradleTestProject.USE_LATEST_NIGHTLY_GRADLE_VERSION && GradleTestProject.GRADLE_TEST_VERSION == BasePlugin.GRADLE_MIN_VERSION.toString())
        {
            val msg = "Running tests with gradle nightly skipped as the minimum plugin version is equal to the latest nightly version."
            System.err.println(msg)
            throw AssumptionViolatedException(msg)
        }

        val start = System.nanoTime()

        /*
         * We sort the benchmarks to make sure they're in a predictable, stable order. This is
         * important because when we shard the running of these tests across machines, all of the
         * machines need this list to be in the same order so that they can run a non-overlapping
         * subset of them.
         */
        benchmarks.sortWith(
            compareBy<Benchmark>({it.scenario}).thenBy({it.benchmark}).thenBy({it.benchmarkMode}));
        /*
         * Figure out which shard we are, or default to being the only shard, and then get the
         * correct sub-list of benchmarks to run based on that information.
         */
        val shardNum = Integer.parseInt(env("PERF_SHARD_NUM", "0"))
        val shardTotal = Integer.parseInt(env("PERF_SHARD_TOTAL", "1"))
        var shard = PerformanceTestUtil.shard(benchmarks, shardNum, shardTotal)

        println(
            "got shard of size "
                    + shard.size
                    + " with parameters PERF_SHARD_NUM="
                    + shardNum
                    + " and PERF_SHARD_TOTAL="
                    + shardTotal
        )

        /*
         * Allow the user to filter by scenario, benchmark and benchmarkMode. This makes it
         * easier to run specific benchmarks locally.
         */
        val scenario = System.getenv("PERF_SCENARIO")
        if (!Strings.isNullOrEmpty(scenario))
        {
            val before = shard.size
            shard = shard.filter { it.scenario == ProjectScenario.valueOf(scenario) }
            val after = shard.size
            println(
                ("Filtered by ProjectScenario="
                        + scenario
                        + ", excluded "
                        + (before - after)
                        + " benchmarks"))
        }

        val benchmark = System.getenv("PERF_BENCHMARK")
        if (!Strings.isNullOrEmpty(benchmark))
        {
            val before = shard.size
            shard = shard.filter {it.benchmark == Logging.Benchmark.valueOf(benchmark)}
            val after = shard.size
            println(
                ("Filtered by Benchmark="
                        + benchmark
                        + ", excluded "
                        + (before - after)
                        + " benchmarks"))
        }

        val benchmarkMode = System.getenv("PERF_BENCHMARK_MODE")
        if (!Strings.isNullOrEmpty(benchmarkMode))
        {
            val before = shard.size
            shard = shard.filter { it.benchmarkMode == Logging.BenchmarkMode.valueOf(benchmarkMode) }
            val after = shard.size
            println(
                ("Filtered by BenchmarkMode="
                        + benchmarkMode
                        + ", excluded "
                        + (before - after)
                        + " benchmarks"))
        }

        if (shard.isEmpty())
        {
            println("Found no benchmarks to run, exiting")
            return
        }

        /*
         * After all of the sharding and filtering, we want to randomise the order that the
         * benchmarks run in, in order to make sure there are no temporal dependencies between them.
         *
         * We generate a seed beforehand and print it to the logs to make temporal dependencies
         * easier to debug when they happen, as the user will then be able to reproduce the order
         * the benchmarks were run in by setting the PERF_SHUFFLE_SEED environment variable.
         */

        println(
            ("Shuffling benchmarks with seed: ${shuffleSeed}, specify PERF_SHUFFLE_SEED=${shuffleSeed}"
                    + " when re-running locally if you need to ensure that the benchmarks run in the same order"))
        Collections.shuffle(shard, Random(shuffleSeed))

        /*
         * Run the benchmarks.
         */
        var i = 0
        val runsPerBenchmark = Integer.parseInt(env("RUNS_PER_BENCHMARK", "1"))
        Preconditions.checkArgument(
            runsPerBenchmark > 0, "you must set RUNS_PER_BENCHMARK to a number greater than 0")

        println("Running ${shard.size} benchmarks, ${runsPerBenchmark} times each...")
        println()

        var benchmarkDuration = Duration.ofNanos(0)
        var recordedDuration = Duration.ofNanos(0)

        val failed = ArrayList<BenchmarkResult>()
        val succeeded = ArrayList<BenchmarkResult>()
        for (b in shard)
        {
            i++

            for (j in 0 until runsPerBenchmark)
            {
                val result = b.run()

                benchmarkDuration = benchmarkDuration.plus(result.totalDuration)
                recordedDuration = recordedDuration.plus(result.recordedDuration)

                println("Benchmark ${i}/${shard.size} (run ${(j + 1)}/${runsPerBenchmark})")
                println(result)
                println()

                if (result.exception != null) {
                    failed.add(result)
                } else {
                    succeeded.add(result)
                }
            }
        }

        /*
         * Each BenchmarkResult that was successful will have a GradleBuildProfile proto attached to
         * it, representing the profile data gathered during that benchmark. There's a lot of
         * metadata we need to attach to the result to identify what machine the benchmark ran on,
         * what commit it pertains to, what set of flags it used and so on. The ProfileWrapper does
         * all of that and gives us a GradleBenchmarkResult proto in return.
         */
        val wrapper = ProfileWrapper()
        val protos = succeeded.map() {wrapper.wrap(it.profile!!, it.benchmark.benchmark, it.benchmark.benchmarkMode, it.benchmark.scenario)}

        /*
         * The last meaningful step in this benchmarking process is to upload the results somewhere.
         * For this we grab the list of ProfileUploader objects and pass all of the results in to
         * each for them to upload to wherever they want to.
         */

        if (!uploaders.isEmpty() && protos.isNotEmpty())
        {
            println("captured ${protos.size} profiles to be uploaded")

            for (uploader in uploaders)
            {
                val uploaderName = uploader.javaClass.getSimpleName()
                println("uploading ${protos.size} profiles with uploader ${uploaderName}...")

                try {
                    uploader.uploadData(protos)
                    println("done")
                } catch (e: IOException) {
                    println("failed to upload with uploader ${uploaderName}: ${e.message}")
                }
            }
        }

        val totalDuration = Duration.ofNanos(System.nanoTime() - start)
        println("Total recorded duration: ${recordedDuration}")
        println("Total benchmark duration: ${benchmarkDuration}")
        println("Overall duration: ${totalDuration}")

        if (!failed.isEmpty())
        {
            println()
            println("Some benchmarks did not complete successfully: ")
            println()

            for (result in failed)
            {
                println(result)
                println()
                println("Command to re-run locally: ${result.benchmark.command()}")
                println()
                println("full stack trace:")
                result.exception!!.printStackTrace()
                println()
            }

            throw AssertionError("${failed.size} benchmarks failed")
        }
    }

    private fun env(name: String, def: String): String {
        var value = System.getenv(name)
        if (Strings.isNullOrEmpty(value)) {
            value = def
        }
        return value
    }


}
