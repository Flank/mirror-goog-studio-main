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

import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.integration.common.utils.getGenerateSourcesCommands
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode
import java.util.function.Supplier

object MediumGradleProjectBenchmarks : Supplier<List<Benchmark>> {
    private val SCENARIOS = listOf(
            ProjectScenario.LEGACY_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_LEGACY_MULTIDEX,
            ProjectScenario.NATIVE_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX,
            ProjectScenario.D8_NATIVE_MULTIDEX,
            ProjectScenario.D8_LEGACY_MULTIDEX)

    private const val ACTIVITY = "WordPress/src/main/java/org/wordpress/android/ui/WebViewActivity.java"

    override fun get(): List<Benchmark> {
        var benchmarks: List<Benchmark> = mutableListOf()

        for (scenario in SCENARIOS) {
            benchmarks += listOf(
                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.EVALUATION,
                            action = { record, _, executor, _ ->
                                record { executor.run("tasks") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.SYNC,
                            action = { record, _, _, model ->
                                record { model.fetchAndroidProjects().onlyModelMap }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.BUILD__FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run("assembleVanillaDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run("assembleVanillaDebug")
                                PerformanceTestUtil.changeActivity(project.file(ACTIVITY))
                                record { executor.run("assembleVanillaDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run("assembleVanillaDebug")
                                PerformanceTestUtil.addMethodToActivity(project.file(ACTIVITY))
                                record { executor.run("assembleVanillaDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run("assembleVanillaDebugAndroidTest") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.GENERATE_SOURCES,
                            action = { record, _, executor, model ->
                                val tasks = model.fetchAndroidProjects().getGenerateSourcesCommands(
                                        { p -> if (p == ":WordPress") "vanillaDebug" else "debug" })

                                record {
                                    executor
                                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                                            .run(tasks)
                                }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BenchmarkMode.NO_OP,
                            action = { record, _, executor, _ ->
                                executor.run("assembleVanillaDebug")
                                record { executor.run("assembleVanillaDebug") }
                            }
                    )
            )
        }

        return benchmarks
    }

    fun benchmark(
            scenario: ProjectScenario,
            benchmarkMode: BenchmarkMode,
            action: BenchmarkAction): Benchmark {
        return Benchmark(
                scenario = scenario,
                benchmark = Logging.Benchmark.PERF_ANDROID_MEDIUM,
                benchmarkMode = benchmarkMode,
                postApplyProject = { project ->
                    PerformanceTestProjects.initializeWordpress(project)
                },
                projectFactory = { projectBuilder ->
                    projectBuilder
                            .fromExternalProject("gradle-perf-android-medium")
                            .withHeap("1536M")
                            .create()
                },
                action = { record, project, executor, model ->
                    executor.run("clean")
                    FileUtils.cleanOutputDir(executor.buildCacheDir)
                    action(record, project, executor, model)
                }
        )
    }
}