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
import com.android.build.gradle.integration.common.utils.getDebugGenerateSourcesCommands
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__ADD
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__EDIT
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.BUILD__FROM_CLEAN
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.EVALUATION
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.GENERATE_SOURCES
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.NO_OP
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.BenchmarkMode.SYNC
import java.io.File
import java.util.function.Supplier

class LargeGradleProjectBenchmarks(private val benchmarkEnvironment: BenchmarkEnvironment) : Supplier<List<Benchmark>> {
    companion object {
        private val SCENARIOS = listOf(ProjectScenario.D8_NATIVE_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX)

        private const val ACTIVITY_PATH =
            "outissue/carnally/src/main/java/com/studio/carnally/LoginActivity.java"
        private const val RES_PATH =
            "outissue/carnally/src/main/res/values/strings.xml"
    }

    override fun get(): List<Benchmark> {
        var benchmarks: List<Benchmark> = mutableListOf()

        for (scenario in SCENARIOS) {
            benchmarks += mutableListOf(
                    benchmark(
                            scenario = scenario,
                            benchmarkMode = EVALUATION,
                            action = { record, _, executor, _ ->
                                record { executor.run("tasks") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = SYNC,
                            action = { record, _, _, model ->
                                record { model.fetchAndroidProjects() }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BUILD__FROM_CLEAN,
                            action = { record, _, executor, _ ->
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = NO_OP,
                            action = { record, _, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                PerformanceTestUtil.changeActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE,
                            action = { record, project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                PerformanceTestUtil.addMethodToActivity(project.file(ACTIVITY_PATH))
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BUILD_INC__SUB_PROJECT__RES__EDIT,
                            action = { record, project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                PerformanceTestUtil.changeStringResource(project.file(RES_PATH))
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = BUILD_INC__SUB_PROJECT__RES__ADD,
                            action = { record, project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                PerformanceTestUtil.addStringResource(project.file(RES_PATH))
                                record { executor.run(":phthalic:assembleDebug") }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = GENERATE_SOURCES,
                            action = { record, _, executor, model ->
                                val tasks = model.fetchAndroidProjects().getDebugGenerateSourcesCommands()
                                record { executor.run(tasks) }
                            }
                    )
            )
        }

        return benchmarks
    }

    private fun benchmark(scenario: ProjectScenario, benchmarkMode: BenchmarkMode, action: BenchmarkAction): Benchmark {
        return Benchmark(
                benchmarkEnvironment = benchmarkEnvironment,
                scenario = scenario,
                benchmark = Logging.Benchmark.PERF_ANDROID_LARGE,
                benchmarkMode = benchmarkMode,
                projectFactory = {
                    it
                        .fromDir(File(benchmarkEnvironment.projectDir.toFile(), "android-studio-gradle-test"))
                        .withHeap("20G")
                        .create()
                },
                postApplyProject = { project ->
                    PerformanceTestProjects.initializeUberSkeleton(project)
                    project
                },
                action = { record, project, executor, model ->
                    executor.run("addSources")
                    executor.run("clean")
                    FileUtils.cleanOutputDir(executor.buildCacheDir)
                    action(record, project, executor, model)
                }
        )
    }
}