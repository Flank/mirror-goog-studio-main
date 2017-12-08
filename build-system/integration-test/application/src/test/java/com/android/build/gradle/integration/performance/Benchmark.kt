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
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.options.BooleanOption
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import org.junit.runner.Description
import org.junit.runners.model.Statement

data class Benchmark(
        // These first three parameters should uniquely identify your benchmark
        val scenario: ProjectScenario,
        val benchmark: Logging.Benchmark,
        val benchmarkMode: Logging.BenchmarkMode,

        val projectFactory: (GradleTestProjectBuilder) -> GradleTestProject,
        val postApplyProject: (GradleTestProject) -> GradleTestProject = { p -> p },
        val action: ((() -> Unit) -> Unit, GradleTestProject, GradleTaskExecutor, ModelBuilder) -> Unit) {
    fun run() {
        /*
         * Any common project configuration should happen here. Note that it isn't possible to take
         * a subproject until _after_ project.apply has been called, so if you do need to do that
         * you'll have to supply a postApplyProject function and do it in there.
         */
        var project = projectFactory(GradleTestProject.builder())

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
                        PerformanceTestProjects.assertNoSyncErrors(model.multi.modelMap)

                        val executor = project.executor()
                                .withEnableInfoLogging(false)
                                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                                .with(BooleanOption.ENABLE_D8, scenario.useD8())
                                .withUseDexArchive(scenario.useDexArchive())


                        val recorder = BenchmarkRecorder(ProfileCapturer(project.profileDirectory))
                        val record = { r: () -> Unit -> recorder.record(scenario, benchmark, benchmarkMode, r) }

                        action(record, project, executor, model)
                        recorder.uploadAsync()
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
