/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.tasks.ExtractDeepLinksTask
import com.android.testutils.truth.PathSubject
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Inject

/**
 * Unit tests for [ExtractDeepLinksTask].
 */
class ExtractDeepLinksTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: ExtractDeepLinksTask
    private lateinit var outputFile: File
    private lateinit var navigationDir: File

    abstract class TaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        ExtractDeepLinksTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "extractDeepLinksTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        outputFile = temporaryFolder.newFile()
        navigationDir = temporaryFolder.newFolder()
    }

    @Test
    fun testBasic() {
        val hostPlaceholder = "\${host}"
        val schemePlaceholder = "\${scheme}"
        val appIdPlaceholder = "\${applicationId}"
        File(navigationDir, "navigation.xml").writeText(
            """
                <navigation xmlns:app="http://schemas.android.com/apk/res-auto">
                    <deepLink
                        app:uri="$schemePlaceholder://$hostPlaceholder/$appIdPlaceholder"/>
                </navigation>
            """.trimIndent()
        )
        task.navFilesFolders.add(FakeGradleDirectory(navigationDir))
        task.manifestPlaceholders.putAll(
            mapOf(
                "host" to "my.host.example.com",
                "scheme" to "myScheme"
            )
        )
        task.forAar.set(false)
        task.navigationJson.set(outputFile)
        task.taskAction()

        PathSubject.assertThat(outputFile).exists()
        PathSubject.assertThat(outputFile).contains("my.host.example.com")
        PathSubject.assertThat(outputFile).contains("myScheme")
        PathSubject.assertThat(outputFile).contains(appIdPlaceholder)
    }

    @Test
    fun testNoOutputWhenForAarAndNoInputNavigationXmls() {
        task.navFilesFolders.add(FakeGradleDirectory(navigationDir))
        task.forAar.set(true)
        task.navigationJson.set(outputFile)
        task.taskAction()

        PathSubject.assertThat(outputFile).doesNotExist()
    }
}
