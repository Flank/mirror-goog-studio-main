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
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier

object LargeGradleProjectBenchmarks : Supplier<List<Benchmark>> {
    private val SCENARIOS = listOf(ProjectScenario.D8_NATIVE_MULTIDEX,
            ProjectScenario.DEX_ARCHIVE_NATIVE_MULTIDEX)

    private val ACTIVITY_PATH = "outissue/carnally/src/main/java/com/studio/carnally/LoginActivity.java"
    private val RES_PATH = "outissue/carnally/src/main/res/values/strings.xml"

    override fun get(): List<Benchmark> {
        var benchmarks: List<Benchmark> = emptyList()

        for (scenario in SCENARIOS) {
            benchmarks += listOf(
                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.EVALUATION,
                            recordedAction = { executor, _ ->
                                executor.run("tasks")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.SYNC,
                            recordedAction = { _, model ->
                                model.multi
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD__FROM_CLEAN,
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.NO_OP,
                            setup = { _, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__IMPLEMENTATION_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                changeActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__JAVA__API_CHANGE,
                            setup = { project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                addMethodToActivity(project.file(ACTIVITY_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__EDIT,
                            setup = { project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                changeStringResource(project.file(RES_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.BUILD_INC__SUB_PROJECT__RES__ADD,
                            setup = { project, executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                                addStringResource(project.file(RES_PATH))
                            },
                            recordedAction = { executor, _ ->
                                executor.run(":phthalic:assembleDebug")
                            }
                    ),

                    benchmark(
                            scenario = scenario,
                            benchmarkMode = Logging.BenchmarkMode.GENERATE_SOURCES,
                            recordedAction = { executor, model ->
                                // We don't care about the model benchmark here.
                                model.dontRecord {
                                    executor.run(ModelHelper.getDebugGenerateSourcesCommands(model.multi.modelMap))
                                }
                            }
                    )
            )
        }

        return benchmarks
    }

    fun benchmark(scenario: ProjectScenario,
            benchmarkMode: Logging.BenchmarkMode,
            setup: (GradleTestProject, RunGradleTasks, BuildModel) -> Unit = { _, _, _ -> },
            recordedAction: (RunGradleTasks, BuildModel) -> Unit): Benchmark {
        return Benchmark(
                scenario = scenario,
                benchmark = Logging.Benchmark.PERF_ANDROID_LARGE,
                benchmarkMode = benchmarkMode,
                projectFactory = {
                    it
                        .fromExternalProject("android-studio-gradle-test")
                        .withHeap("20G")
                        .create()
                },
                postApplyProject = { project, executor ->
                    PerformanceTestProjects.initializeUberSkeleton(project)
                    executor.run("addSources")
                    project
                },
                setup = { project, executor, model ->
                    executor.run("clean")
                    executor.run(":phthalic:assembleDebug")
                    executor.run("clean")
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

    private fun changeStringResource(file: File) {
        TestFileUtils.searchAndReplace(file, "</string>", " added by test</string>");
    }

    private fun addStringResource(file: File) {
        TestFileUtils.searchAndReplace(
                file,
                "</resources>",
                "<string name=\"generated_by_test_for_perf\">my string</string></resources>");
    }
}
