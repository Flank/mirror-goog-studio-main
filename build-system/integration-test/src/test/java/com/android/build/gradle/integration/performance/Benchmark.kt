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
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.RunGradleTasks
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.options.BooleanOption
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import org.junit.runner.Description
import org.junit.runners.model.Statement

data class Benchmark(
        val scenario: ProjectScenario,
        val benchmark: Logging.Benchmark,
        val benchmarkMode: Logging.BenchmarkMode,
        val projectFactory: (GradleTestProjectBuilder) -> GradleTestProject,
        val postApplyProject: (GradleTestProject) -> GradleTestProject = { p -> p },
        val setup: (GradleTestProject, RunGradleTasks, BuildModel) -> Unit,
        val recordedAction: (RunGradleTasks, BuildModel) -> Unit) {
    fun run() {
        var project = projectFactory(
                GradleTestProject.builder()
                        .forBenchmarkRecording(BenchmarkRecorder(benchmark, scenario)))

        val statement =
                object : Statement() {
                    override fun evaluate() {
                        /*
                         * This was added for the sole reason of supporting the weird structure of
                         * the AntennaPod project. Please don't use it unless you've thought very
                         * hard about alternatives.
                         *
                         * The problem arises because calling getSubproject involved a call to
                         * getTestDir, and getTestDir will fail unless createTestDirectory has been
                         * called, which is only ever called by apply. So we need to have called
                         * apply before we call getSubproject, and this hack was the least bad way
                         * we could think of doing it.
                         */
                        project = postApplyProject(project)

                        val model = project.model().ignoreSyncIssues().withoutOfflineFlag()
                        PerformanceTestProjects.assertNoSyncErrors(model.multi.modelMap)

                        val executor = project.executor()
                                .withEnableInfoLogging(false)
                                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                                .with(BooleanOption.ENABLE_D8, scenario.useD8())
                                .withUseDexArchive(scenario.useDexArchive())

                        setup(project, executor, model)

                        /*
                         * Note that both the executor and model are put into recording mode. This
                         * means that if you do something with the model, and do something with the
                         * executor, you will end up with two GradleBenchmarkResults at the end, and
                         * it's highly likely that the distinctness check on these results will fail
                         * because they will be too similar.
                         *
                         * As such, in a recordedAction, you want to either do only something with
                         * the executor _or_ the model, or you want to disable recording on one of
                         * them like so:
                         *
                         *   executor.recordBenchmark(null)
                         */
                        recordedAction(executor.recordBenchmark(benchmarkMode),
                                model.recordBenchmark(benchmarkMode))
                    }
                }

        project.apply(statement, testDescription()).evaluate()
    }

    private fun testDescription(): Description {
        val desc = "{scenario=${scenario.name}, " +
                "benchmark=${benchmark.name}, " +
                "benchmarkMode=${benchmarkMode.name}}"

        return Description.createTestDescription(this.javaClass, desc)
    }
}
