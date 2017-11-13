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
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import java.io.File
import java.util.concurrent.ThreadLocalRandom
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
                            benchmarkMode = Logging.BenchmarkMode.EVALUATION,
                            recordedAction = { executor, _ -> executor.run("tasks") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.SYNC,
                            recordedAction = { _, model -> model.multi.modelMap }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD__FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run("assembleVanillaDebug") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run("assembleVanillaDebug")
                                changeActivity(project.file(ACTIVITY))
                            },
                            recordedAction = { executor, _ -> executor.run("assembleVanillaDebug") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__MAIN_PROJECT__JAVA__API_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run("assembleVanillaDebug")
                                addMethodToActivity(project.file(ACTIVITY))
                            },
                            recordedAction = { executor, _ -> executor.run("assembleVanillaDebug") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_ANDROID_TESTS_FROM_CLEAN,
                            recordedAction = { executor, _ -> executor.run("assembleVanillaDebugAndroidTest") }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.GENERATE_SOURCES,
                            recordedAction = { executor, model ->
                                model.dontRecord {
                                    val tasks =
                                        ModelHelper.getGenerateSourcesCommands(
                                            model.multi.modelMap,
                                            { project ->
                                                if (project == ":WordPress") {
                                                    "vanillaDebug"
                                                } else {
                                                    "debug"
                                                }
                                            })

                                    executor
                                            .with(BooleanOption.IDE_GENERATE_SOURCES_ONLY, true)
                                            .run(tasks)
                                }
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.NO_OP,
                            setup = { _, executor, _ -> executor.run("assembleVanillaDebug") },
                            recordedAction = { executor, _ -> executor.run("assembleVanillaDebug") }
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
                benchmark = Logging.Benchmark.PERF_ANDROID_MEDIUM,
                benchmarkMode = benchmarkMode,
                postApplyProject = { project ->
                    PerformanceTestProjects.initializeWordpress(project)

                    if (scenario.flags.multiDex == Logging.GradleBenchmarkResult.Flags.MultiDexMode.NATIVE) {
                        TestFileUtils.searchAndReplace(
                                project.file("WordPress/build.gradle"),
                                "minSdkVersion( )* \\d+",
                                "minSdkVersion 21")
                    }

                    project
                },
                projectFactory = { projectBuilder ->
                    projectBuilder
                            .fromExternalProject("gradle-perf-android-medium")
                            .withHeap("1536M")
                            .create()
                },
                setup = { project, executor, model ->
                    executor.run("clean")
                    FileUtils.cleanOutputDir(executor.buildCacheDir)
                    setup(project, executor, model)
                },
                recordedAction = recordedAction
        )
    }

    private fun addMethodToActivity(file: File) {
        val newMethodName = "newMethod" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE)
        TestFileUtils.searchAndReplace(
                file,
                "void onCreate\\((.*?)\\) \\{",
                """
                    void onCreate($1) {
                       $newMethodName();
                """.trimIndent())

        TestFileUtils.addMethod(
                file,
                """
                    public void $newMethodName () {
                        android.util.Log.d("perftests", "$newMethodName called");
                    }
                """.trimIndent())
    }

    private fun changeActivity(file: File) {
        val rand = "rand" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE)
        TestFileUtils.searchAndReplace(
                file,
                "void onCreate\\((.*?)\\) \\{",
                """
                    void onCreate($1) {
                       android.util.Log.d("perftests", "onCreate called $rand");
                """.trimIndent())
    }
}