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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.truth.TruthHelper.assertWithMessage
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for the content of the profile collected from library projects.
 */
class LibraryProfileContentTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.library"))
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
        // TODO b/158092419
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=23")
        .enableProfileOutput()
        .create()

    @Test
    fun testProfileProtoContentMakesSense() {
        val capturer = ProfileCapturer(project)

        val cleanBuild = Iterables.getOnlyElement(
            capturer.capture { project.executor().withArguments(listOf("--parallel", "--max-workers=1")).run("assembleDebug") })

        // Check that the generate library R file task records its worker spans.
        val generateLibraryTask = cleanBuild.spanList.first() {
            it.hasTask() && it.task.type == GradleTaskExecutionType.GENERATE_LIBRARY_R_FILE.number }
        val generateLibraryTaskChildren =
            cleanBuild.spanList.filter { it.parentId == generateLibraryTask.id }
        assertThat(generateLibraryTaskChildren).hasSize(3)
        val workerSpan = generateLibraryTaskChildren.first() { it.type == GradleBuildProfileSpan.ExecutionType.WORKER_EXECUTION }
        assertWithMessage("Worker span is positive").that(workerSpan.durationInMs).isGreaterThan(0)
        assertThat(cleanBuild.parallelTaskExecution).isTrue()
    }
}
