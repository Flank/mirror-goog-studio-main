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

import com.android.build.gradle.integration.common.fixture.BuildModel
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.RunGradleTasks
import com.android.build.gradle.integration.common.utils.ModelHelper
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.instant.InstantRunTestUtils
import com.android.build.gradle.options.BooleanOption
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier

object AntennaPodBenchmarks : Supplier<List<Benchmark>> {
    private val SCENARIOS = listOf(
            ProjectScenario.NORMAL_J8,
            ProjectScenario.DEX_ARCHIVE_MONODEX_J8,
            ProjectScenario.D8_MONODEX_J8)

    private const val ACTIVITY_PATH = "app/src/main/java/de/danoeh/antennapod/activity/MainActivity.java"

    private val INSTANT_RUN_TARGET_DEVICE_VERSION = AndroidVersion(24, null)

    override fun get(): List<Benchmark> {
        var benchmarks: List<Benchmark> = mutableListOf()

        for (scenario in SCENARIOS) {
            benchmarks += listOf(
                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.EVALUATION,
                            recordedAction = { executor, _ -> executor.run("tasks") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.SYNC,
                            recordedAction = { _, model -> model.multi }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD__FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebug") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                addMethodToActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebug") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                changeActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebug") }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebug") }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__API_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                addMethodToActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":app:assembleDebug")
                            }
                    ),

                    instantRunBenchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.INSTANT_RUN_BUILD__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":app:assembleDebug")
                                changeActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":app:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebugAndroidTest") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_UNIT_TESTS_FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run(":app:assembleDebugUnitTest") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.GENERATE_SOURCES,
                            recordedAction = { executor, model ->
                                // We don't care about the model benchmark here.
                                model.dontRecord {
                                    val tasks = ModelHelper.getDebugGenerateSourcesCommands(model.multi.modelMap)
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

    fun benchmark(
            scenario: ProjectScenario,
            benchmarkMode: Logging.BenchmarkMode,
            setup: (GradleTestProject, RunGradleTasks, BuildModel) -> Unit = { _, _, _ -> },
            recordedAction: (RunGradleTasks, BuildModel) -> Unit): Benchmark {
        return Benchmark(
                scenario = scenario,
                benchmark = Logging.Benchmark.ANTENNA_POD,
                benchmarkMode = benchmarkMode,
                postApplyProject = { project ->
                    PerformanceTestProjects.initializeAntennaPod(project)
                    project.getSubproject("AntennaPod")
                },
                projectFactory = { projectBuilder ->
                    projectBuilder
                        .fromExternalProject("AntennaPod")
                        .withRelativeProfileDirectory(
                                Paths.get("AntennaPod", "build", "android-profile"))
                        .withHeap("1536M")
                        .create()
                },
                setup = { project, executor, model ->
                    executor.run("assembleDebug", "assembleDebugAndroidTest")
                    executor.run("clean")
                    FileUtils.cleanOutputDir(executor.buildCacheDir)
                    setup(project, executor, model)
                },
                recordedAction = recordedAction
        )
    }

    private fun instantRunBenchmark(
            scenario: ProjectScenario,
            benchmarkMode: Logging.BenchmarkMode,
            setup: (GradleTestProject, RunGradleTasks, BuildModel) -> Unit = { _, _, _ -> },
            recordedAction: (RunGradleTasks, BuildModel) -> Unit): Benchmark {
        return benchmark(
                scenario = scenario,
                benchmarkMode = benchmarkMode,
                setup = { project, executor, model ->
                    setup(project,
                            executor.withInstantRun(INSTANT_RUN_TARGET_DEVICE_VERSION),
                            model)
                },
                recordedAction = { executor, model ->
                    recordedAction(executor.withInstantRun(INSTANT_RUN_TARGET_DEVICE_VERSION),
                            model)
                    assertInstantRunInvoked(model)
                })
    }

    private fun addMethodToActivity(file: File) {
        val newMethodName = "newMethod" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE)
        TestFileUtils.searchAndReplace(
                file,
                "public void onStart\\(\\) \\{",
                """
                    public void onStart() {
                       $newMethodName();
                """.trimIndent())

        TestFileUtils.addMethod(
                file,
                """
                    private void $newMethodName () {
                        Log.d(TAG, "$newMethodName called");
                    }
                """.trimIndent())
    }

    private fun changeActivity(file: File) {
        val rand = "rand" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE)
        TestFileUtils.searchAndReplace(
                file,
                "public void onStart\\(\\) \\{",
                """
                    public void onStart() {
                       Log.d(TAG, "onStart called $rand");
                """.trimIndent())
    }

    private fun assertInstantRunInvoked(model: BuildModel) {
        model.dontRecord {
            /*
             * The following lines of code verify that an instant run happened
             * by asserting that we can parse an InstantRunBuildInfo. If we
             * can't, an exception is thrown.
             */
            InstantRunTestUtils.loadContext(
                    InstantRunTestUtils.getInstantRunModel(
                            model.multi.modelMap[":app"]!!))
        }
    }
}

