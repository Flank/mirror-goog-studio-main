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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelBuilder
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.build.gradle.integration.instant.InstantRunTestUtils
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import java.io.File
import java.util.function.Supplier

class AntennaPodBenchmarks(private val benchmarkEnvironment: BenchmarkEnvironment) : Supplier<List<Benchmark>> {
    companion object {
        private val SCENARIOS = listOf(
            ProjectScenario.DEX_ARCHIVE_MONODEX_J8,
            ProjectScenario.D8_MONODEX_J8)

        private const val ACTIVITY_PATH = "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"
    }

    override fun get(): List<Benchmark> {
        var benchmarks: List<Benchmark> = mutableListOf()

        for (scenario in SCENARIOS) {
            benchmarks += listOf(
                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.EVALUATION,
                            action = { record, _, executor, _ ->
                                record { executor.run("tasks") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.SYNC,
                            action = { record, _, _, model ->
                                record { model.fetchAndroidProjects() }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD__FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                PerformanceTestUtil.addMethodToActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                PerformanceTestUtil.changeActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                PerformanceTestUtil.addMethodToActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                PerformanceTestUtil.changeActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":app:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run(":app:assembleDebugAndroidTest") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_UNIT_TESTS_FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run(":app:assembleDebugUnitTest") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.GENERATE_SOURCES,
                            action = { record, _, executor, model ->
                                // We don't care about the model benchmark here.
                                val tasks = model.fetchAndroidProjects().getDebugGenerateSourcesCommands()
                                record {
                                    executor
                                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                                            .run(tasks)
                                }
                            }
                    )
            )
        }

        return benchmarks
    }

    private fun benchmark(
            scenario: ProjectScenario,
            benchmarkMode: Logging.BenchmarkMode,
            action: ((() -> Unit) -> Unit, GradleTestProject, GradleTaskExecutor, ModelBuilder) -> Unit): Benchmark {
        return Benchmark(
                benchmarkEnvironment = benchmarkEnvironment,
                scenario = scenario,
                benchmark = Logging.Benchmark.ANTENNA_POD,
                benchmarkMode = benchmarkMode,
                projectFactory = { projectBuilder ->
                    projectBuilder
                        .fromDir(File(benchmarkEnvironment.projectDir.toFile(), "AntennaPod"))
                        .withHeap("1536M")
                        .create()
                },
                postApplyProject = { project ->
                    PerformanceTestProjects.initializeAntennaPod(project)
                    project.getSubproject("AntennaPod")
                },
                action = { record, project, executor, model ->
                    executor.run("clean")
                    FileUtils.cleanOutputDir(executor.buildCacheDir)
                    action(record, project, executor, model)
                }
        )
    }

    private fun instantRunBenchmark(
            scenario: ProjectScenario,
            benchmarkMode: Logging.BenchmarkMode,
            action: BenchmarkAction,
            instantRunTargetDeviceVersion: AndroidVersion = AndroidVersion(24, null)): Benchmark {
        return benchmark(
                scenario = scenario,
                benchmarkMode = benchmarkMode,
                action = { record, project, executor, model ->
                    action(record, project, executor.withInstantRun(instantRunTargetDeviceVersion), model)
                    assertInstantRunInvoked(model)
                })
    }

    private fun assertInstantRunInvoked(model: ModelBuilder) {
        /*
         * The following lines of code verify that an instant run happened
         * by asserting that we can parse an InstantRunBuildInfo. If we
         * can't, an exception is thrown.
         */
        InstantRunTestUtils.loadContext(
                InstantRunTestUtils.getInstantRunModel(
                        model.fetchAndroidProjects().onlyModelMap[":app"]!!))
    }
}

