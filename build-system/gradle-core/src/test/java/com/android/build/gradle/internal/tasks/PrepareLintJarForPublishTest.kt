/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

class PrepareLintJarForPublishTest {
    @Rule
    @JvmField
    val tmpDir = TemporaryFolder()

    private lateinit var project: Project

    abstract class PrepareLintJarForPublishForTest @Inject constructor(fakeExecutor: FakeGradleWorkExecutor) :
            PrepareLintJarForPublish() {

        override val workerExecutor = fakeExecutor
    }

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(tmpDir.newFolder()).build()
    }

    /** Regression test for b/163039193. */
    @Test
    fun checkRunnableCanRunTwice() {
        val inputJar = tmpDir.newFile("input.jar")
        val outputLocation = tmpDir.root.resolve("outputDir/output.jar")
        val task =
                project.tasks.register("publish",
                        PrepareLintJarForPublishForTest::class.java,
                        FakeGradleWorkExecutor(project.objects, tmpDir.newFolder()))
        task.configure {
            it.lintChecks.from(inputJar)
            it.outputLintJar.set(outputLocation)
            it.analyticsService.set(FakeNoOpAnalyticsService())
        }

        task.get().prepare()
        assertThat(outputLocation).exists()

        // Make sure we can run second time.
        task.get().prepare()
        assertThat(outputLocation).exists()
    }
}
