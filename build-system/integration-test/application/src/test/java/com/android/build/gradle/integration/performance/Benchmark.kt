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

package com.android.build.gradle.integration.performance

import com.android.build.gradle.integration.common.fixture.ModelBuilder
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.options.BooleanOption
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

typealias BenchmarkAction = (((() -> Unit) -> Unit, GradleTestProject, GradleTaskExecutor, ModelBuilder) -> Unit)

data class Benchmark(
        // These first three parameters should uniquely identify your benchmark
        val scenario: ProjectScenario,
        val benchmark: Logging.Benchmark,
        val benchmarkMode: Logging.BenchmarkMode,

        val projectFactory: (GradleTestProjectBuilder) -> GradleTestProject,
        val postApplyProject: (GradleTestProject) -> GradleTestProject = { p -> p },
        val action: BenchmarkAction) {
    fun run(): BenchmarkResult {
        var recordStart = 0L
        var recordEnd = 0L
        val totalStart = System.nanoTime()

        /*
         * Any common project configuration should happen here. Note that it isn't possible to take
         * a subproject until _after_ project.apply has been called, so if you do need to do that
         * you'll have to supply a postApplyProject function and do it in there.
         */
        var project = projectFactory(
                GradleTestProject.builder()
                        .enableProfileOutputInDirectory(BenchmarkTest.getProfileDirectory())
                        .withTestDir(projectDir().toFile())
                        .withRepoDirectories(BenchmarkTest.getMavenRepos())
                        .withAndroidHome(BenchmarkTest.getSdkDir().toFile())
                        .withGradleDistributionDirectory(BenchmarkTest.getGradleDir().toFile())
                        .withKotlinVersion(KotlinVersion.CURRENT.toString()))

        var profileLocation: Path? = null

        val statement =
                object : Statement() {
                    override fun evaluate() {
                        /*
                         * Anything that needs to be done to the project before the executor and
                         * model are taken from it should happen in the postApplyProject function.
                         *
                         * This is commonly used to do any initialisation, like changing the
                         * contents of build files and whatnot.
                         */
                        project = postApplyProject(project)

                        val model = project.model().ignoreSyncIssues().withoutOfflineFlag()
                        PerformanceTestProjects.assertNoSyncErrors(model.fetchAndroidProjects().onlyModelMap)

                        val executor = project.executor()
                                .withEnableInfoLogging(false)
                                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                                .with(BooleanOption.ENABLE_D8, scenario.useD8())
                                .withUseDexArchive(scenario.useDexArchive())

                        val recorder = BenchmarkTest.getBenchmarkRecorder()
                        val recordCalled = AtomicBoolean(false)
                        val record = { r: () -> Unit ->
                            recordStart = System.nanoTime()
                            try {
                                if (recordCalled.get()) {
                                    throw IllegalStateException("record lambda used twice, please make sure your benchmark is only measuring one action")
                                }
                                recordCalled.set(true)

                                val numProfiles = recorder.record(scenario, benchmark, benchmarkMode, r)
                                if (numProfiles != 1) {
                                    throw IllegalStateException("record lambda generated more than one profile, this is not allowed")
                                }

                                profileLocation = Iterables.getOnlyElement(BenchmarkTest.getProfileCapturer().lastPoll)
                            } finally {
                                recordEnd = System.nanoTime()
                            }
                        }

                        action(record, project, executor, model)
                        if (!recordCalled.get()) {
                            throw IllegalStateException("no recorded section in your benchmark, did you forget to use the record function?")
                        }

                        recorder.uploadAsync()
                    }
                }

        var exception: Exception? = null
        try {
            project.apply(statement, testDescription()).evaluate()
        } catch (e: Exception) {
            exception = e
        }

        return BenchmarkResult(
                benchmark = this,
                recordedDuration = Duration.ofNanos(recordEnd - recordStart),
                totalDuration = Duration.ofNanos(System.nanoTime() - totalStart),
                profileLocation = profileLocation,
                exception = exception
        )
    }

    private fun testDescription(): Description {
        val desc = "{scenario=${scenario.name}, " +
                "benchmark=${benchmark.name}, " +
                "benchmarkMode=${benchmarkMode.name}}"

        return Description.createTestDescription(this.javaClass, desc)
    }

    /**
     * Returns a command you can run locally to reproduce this individual test.
     */
    fun command(): String {
        val task = ":base:build-system:integration-test:application:performanceTest"
        val command = "./gradlew --info $task -D$task.single=${BenchmarkTest::class.simpleName}"

        val gradleNightly = if (GradleTestProject.USE_LATEST_NIGHTLY_GRADLE_VERSION) "true" else "false"

        return  "USE_GRADLE_NIGHTLY=$gradleNightly " +
                "PERF_SCENARIO=${scenario.name} " +
                "PERF_BENCHMARK=${benchmark.name} " +
                "PERF_BENCHMARK_MODE=${benchmarkMode.name} " +
                command
    }

    fun projectDir(): Path =
            BenchmarkTest.getBenchmarkTestRootDirectory()
                    .resolve(scenario.name)
                    .resolve(benchmark.name)
                    .resolve(benchmarkMode.name)
}
