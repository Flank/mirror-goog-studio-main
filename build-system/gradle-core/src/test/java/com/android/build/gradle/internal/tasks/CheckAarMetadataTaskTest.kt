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
import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeResolvedArtifactResult
import com.android.Version
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

/**
 * Unit tests for [CheckAarMetadataTask].
 */
class CheckAarMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: CheckAarMetadataTask

    abstract class CheckAarMetadataTaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        CheckAarMetadataTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "checkAarMetadataTask",
            CheckAarMetadataTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testPassing() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        task.taskAction()
    }

    @Test
    fun testFailsOnAarFormatVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = "2.0",
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set("1.0")
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        try {
            task.taskAction()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("The $AAR_FORMAT_VERSION_PROPERTY (2.0) specified")
        }
    }

    @Test
    fun testFailsOnAarMetadataVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = "2.0",
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set("1.0")
        task.compileSdkVersion.set("android-28")
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        try {
            task.taskAction()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("The $AAR_METADATA_VERSION_PROPERTY (2.0) specified")
        }
    }

    @Test
    fun testFailsOnMinCompileSdkVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-27")
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        try {
            task.taskAction()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains(
                "Dependency 'displayName' requires 'compileSdkVersion' to be set to 28 or higher."
            )
        }
    }

    @Test
    fun testFailsOnMinAgpVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.agpVersion.set("3.0.0-beta01")
        task.projectPath.set(":app")
        try {
            task.taskAction()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains(
                """
                    Dependency 'displayName' requires an Android Gradle Plugin version of 3.0.0 or higher.
                    The Android Gradle Plugin version used for this build is 3.0.0-beta01.
                    """.trimIndent()
            )
        }
    }

    @Test
    fun tesMultipleFailures() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = "2.0",
                                aarMetadataVersion = "2.0",
                                minCompileSdk = 28,
                                minAgpVersion = "3.0.0"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-27")
        task.agpVersion.set("3.0.0-beta01")
        task.projectPath.set(":app")
        try {
            task.taskAction()
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("The $AAR_FORMAT_VERSION_PROPERTY (2.0) specified")
            assertThat(e.message).contains("The $AAR_METADATA_VERSION_PROPERTY (2.0) specified")
            assertThat(e.message).contains(
                "Dependency 'displayName' requires 'compileSdkVersion' to be set to 28 or higher."
            )
            assertThat(e.message).contains(
                """
                    Dependency 'displayName' requires an Android Gradle Plugin version of 3.0.0 or higher.
                    The Android Gradle Plugin version used for this build is 3.0.0-beta01.
                    """.trimIndent()
            )
        }
    }
}
