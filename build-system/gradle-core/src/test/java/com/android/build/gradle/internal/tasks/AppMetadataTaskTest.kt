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

import com.android.SdkConstants.ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY
import com.android.SdkConstants.ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.APP_METADATA_VERSION_PROPERTY
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import javax.inject.Inject

/**
 * Unit tests for [AppMetadataTask].
 */
class AppMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: AppMetadataTask
    private lateinit var outputFile: File

    abstract class AppMetadataForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        AppMetadataTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "appMetadataTask",
            AppMetadataForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        outputFile = temporaryFolder.newFile()
    }

    @Test
    fun testBasic() {
        task.outputFile.set(outputFile)
        task.appMetadataVersion.set(AppMetadataTask.APP_METADATA_VERSION)
        task.agpVersion.set("foo")
        task.taskAction()

        checkAppMetadataFile(outputFile, AppMetadataTask.APP_METADATA_VERSION, "foo")
    }

    @Test
    fun testWithAgdeVersion() {
        task.outputFile.set(outputFile)
        task.appMetadataVersion.set(AppMetadataTask.APP_METADATA_VERSION)
        task.agpVersion.set("0123 abcd")
        task.agdeVersion.set("abcd 0123")

        task.taskAction()

        checkAppMetadataFile(outputFile, AppMetadataTask.APP_METADATA_VERSION, "0123 abcd", "abcd 0123")
    }

    private fun checkAppMetadataFile(
        file: File,
        appMetadataVersion: String,
        agpVersion: String,
        agdeVersion: String? = null
    ) {
        assertThat(file).exists()
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        assertThat(properties.getProperty(APP_METADATA_VERSION_PROPERTY))
            .isEqualTo(appMetadataVersion)
        assertThat(properties.getProperty(ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY))
            .isEqualTo(agpVersion)
        assertThat(properties.getProperty(ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY))
            .isEqualTo(agdeVersion)
    }
}
