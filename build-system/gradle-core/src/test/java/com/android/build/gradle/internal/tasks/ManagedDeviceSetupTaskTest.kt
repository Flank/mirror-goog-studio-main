/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.repository.Revision
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

class ManagedDeviceSetupTaskTest {
    private lateinit var mockVersionedSdkLoader: VersionedSdkLoader

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var globalConfig: GlobalTaskCreationConfigImpl

    @Mock
    private lateinit var avdService: AvdComponentsBuildService

    @Mock
    private lateinit var sdkService: SdkComponentsBuildService

    private lateinit var project: Project

    @Before
    fun setup() {
        mockVersionedSdkLoader = mock(VersionedSdkLoader::class.java)
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(false)

        project = ProjectBuilder.builder().withProjectDir(temporaryFolderRule.newFolder()).build()
        `when`(sdkService.sdkLoader(any(), any())).thenReturn(mockVersionedSdkLoader)
    }

    private fun basicTaskSetup(): ManagedDeviceSetupTask {
        val task = mock(
            ManagedDeviceSetupTask::class.java,
            CALLS_REAL_METHODS)

        // Need to use a real property for all variables passed into the ManagedDeviceSetupRunnable
        // Because internal to Gradle's implementation of ProfileAwareWorkAction
        // a forced cast occurs to cast Provider to ProviderInternal which we
        // do not have access to directly.
        doReturn(realPropertyFor(mock(AnalyticsService::class.java)))
            .`when`(task).analyticsService
        doReturn(realPropertyFor("project_path")).`when`(task).projectPath

        doReturn("path").`when`(task).path
        doReturn(mock(TaskOutputsInternal::class.java, RETURNS_DEEP_STUBS))
            .`when`(task).outputs
        doReturn(MockitoKt.mock<Logger>()).`when`(task).logger

        doReturn(realPropertyFor(sdkService)).`when`(task).sdkService
        doReturn(realPropertyFor(avdService)).`when`(task).avdService
        doReturn(realPropertyFor("sdkVersion")).`when`(task).compileSdkVersion
        doReturn(realPropertyFor(mock(Revision::class.java)))
            .`when`(task).buildToolsRevision
        doReturn(realPropertyFor("x86_64")).`when`(task).abi
        doReturn(realPropertyFor(29)).`when`(task).apiLevel
        doReturn(realPropertyFor("aosp")).`when`(task).systemImageVendor
        doReturn(realPropertyFor("Pixel 2")).`when`(task).hardwareProfile
        doReturn(realPropertyFor("auto-no-window")).`when`(task).emulatorGpuFlag


        doReturn(FakeGradleWorkExecutor(project.objects, temporaryFolderRule.newFolder()))
            .`when`(task).workerExecutor
        return task
    }

    private inline fun <reified ValueClass> realPropertyFor(
        providedValue: ValueClass): Property<ValueClass> {

        val property = project.objects.property(ValueClass::class.java)
        property.set(providedValue)
        return property
    }

    @Test
    fun taskAction_basicSetupPath() {
        val task = basicTaskSetup()

        val imageDirectory = mock(Directory::class.java)
        `when`(mockVersionedSdkLoader.sdkImageDirectoryProvider(any()))
            .thenReturn(FakeGradleProperty(imageDirectory))
        `when`(avdService.avdProvider(any(), any(), any(), any()))
            .thenReturn(FakeGradleProperty(mock(Directory::class.java)))

        task.taskAction()

        verify(mockVersionedSdkLoader)
            .sdkImageDirectoryProvider("system-images;android-29;default;x86_64")
        verifyNoMoreInteractions(mockVersionedSdkLoader)

        verify(avdService)
            .avdProvider(
                argThat {
                    it is FakeGradleProperty && it.get() == imageDirectory
                },
                eq("system-images;android-29;default;x86_64"),
                eq("dev29_aosp_x86_64_Pixel_2"),
                eq("Pixel 2")
            )
        verify(avdService)
            .ensureLoadableSnapshot(
                "dev29_aosp_x86_64_Pixel_2",
                "auto-no-window"
            )
        verifyNoMoreInteractions(avdService)
    }

    @Test
    fun testTaskAction_missingImage() {
        val task = basicTaskSetup()

        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(true)
        `when`(mockVersionedSdkLoader.sdkImageDirectoryProvider(any()))
            .thenReturn(FakeGradleProperty(null))

        try {
            task.taskAction()

            error("Should not reach")
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo(
                """
                    system-images;android-29;default;x86_64 is not available, and could not be downloaded while in offline mode.
                """.trimIndent()
            )
        }

        verify(mockVersionedSdkLoader)
            .sdkImageDirectoryProvider("system-images;android-29;default;x86_64")
        verify(mockVersionedSdkLoader)
            .offlineMode
        verifyNoMoreInteractions(mockVersionedSdkLoader)
        verifyNoInteractions(avdService)
    }

    @Test
    fun generateSystemImageErrorMessage_Offline() {
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(true)
        val errorMessage = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "system-images;android-29;default;x86",
            mockVersionedSdkLoader
        )
        assertThat(errorMessage).isEqualTo(
            """
                system-images;android-29;default;x86 is not available, and could not be downloaded while in offline mode.
            """.trimIndent()
        )
    }

    @Test
    fun generateSystemImageErrorMessage_licensingError() {
        `when`(mockVersionedSdkLoader.allSystemImageHashes())
            .thenReturn(listOf("system-images;android-29;default;x86"))

        val errorMessage = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "system-images;android-29;default;x86",
            mockVersionedSdkLoader
        )
        assertThat(errorMessage).isEqualTo(
            """
                System image hash: system-images;android-29;default;x86 exists, but could not be downloaded. This is
                likely due to a licensing exception. See above errors for clarification.
            """.trimIndent()
        )
    }

    @Test
    fun generateSystemImageErrorMessage_similarImages() {
        `when`(mockVersionedSdkLoader.allSystemImageHashes())
            .thenReturn(
                listOf("system-images;android-29;default;x86_64"))

        val errorMessage = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "system-images;android-29;default;x86",
            mockVersionedSdkLoader
        )
        assertThat(errorMessage).isEqualTo(
            """
                System image hash: system-images;android-29;default;x86 does not exist. However, here is a list of similar
                images:
                [system-images;android-29;default;x86_64]
            """.trimIndent()
        )
    }

    @Test
    fun generateSystemImageErrorMessage_invalidHash() {
        `when`(mockVersionedSdkLoader.allSystemImageHashes()).thenReturn(listOf())

        val errorMessage = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "system-images;android-29;default;x86",
            mockVersionedSdkLoader
        )
        assertThat(errorMessage).isEqualTo(
            "Generated invalid hash string \"system-images;android-29;default;x86\" from the " +
                    "DSL. This should not occur."
        )
    }
}
