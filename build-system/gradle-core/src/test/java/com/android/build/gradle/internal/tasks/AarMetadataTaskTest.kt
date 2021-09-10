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

import com.android.SdkConstants.AAR_FORMAT_VERSION_PROPERTY
import com.android.SdkConstants.AAR_METADATA_VERSION_PROPERTY
import com.android.SdkConstants.MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.Version
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.ide.common.repository.GradleVersion
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.RuntimeException
import java.util.Properties
import javax.inject.Inject

/**
 * Unit tests for [AarMetadataTask].
 */
class AarMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: AarMetadataTask
    private lateinit var outputFile: File

    abstract class AarMetadataForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        AarMetadataTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "aarMetadataTask",
            AarMetadataForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        outputFile = temporaryFolder.newFile("AarMetadata.xml")
    }

    @Test
    fun testBasic() {
        task.output.set(outputFile)
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.minCompileSdk.set(28)
        task.minAgpVersion.set("7.0.0")
        task.taskAction()

        checkAarMetadataFile(
            outputFile,
            AarMetadataTask.AAR_FORMAT_VERSION,
            AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdk = "28",
            minAgpVersion = "7.0.0"
        )
    }

    @Test
    fun testUnstableMinAgpVersion() {
        task.output.set(outputFile)
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.minCompileSdk.set(28)
        task.minAgpVersion.set("7.0.0-beta01")
        try {
            task.taskAction()
            fail("expecting RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains(
                "The specified minAgpVersion (7.0.0-beta01) is not valid. The minAgpVersion " +
                        "must be a stable AGP version, formatted with major, minor, and micro " +
                        "values (for example \"4.0.0\")."
            )
        }
    }

    @Test
    fun testFutureMinAgpVersion() {
        task.output.set(outputFile)
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.minCompileSdk.set(28)
        task.minAgpVersion.set("10000.0.0")
        try {
            task.taskAction()
            fail("expecting RuntimeException")
        } catch (e: RuntimeException) {
            val currentAgpVersion =
                GradleVersion.parseAndroidGradlePluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
            assertThat(e.message).contains(
                "The specified minAgpVersion (10000.0.0) is not valid because it is a later " +
                        "version than the version of AGP used for this build ($currentAgpVersion)."
            )
        }
    }

    private fun checkAarMetadataFile(
        file: File,
        aarFormatVersion: String,
        aarMetadataVersion: String,
        minCompileSdk: String,
        minAgpVersion: String
    ) {
        assertThat(file).exists()
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        assertThat(properties.getProperty(AAR_FORMAT_VERSION_PROPERTY)).isEqualTo(aarFormatVersion)
        assertThat(properties.getProperty(AAR_METADATA_VERSION_PROPERTY))
            .isEqualTo(aarMetadataVersion)
        assertThat(properties.getProperty(MIN_COMPILE_SDK_PROPERTY)).isEqualTo(minCompileSdk)
        assertThat(properties.getProperty(MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY))
            .isEqualTo(minAgpVersion)
    }
}
