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
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.options.StringOption
import com.android.repository.Revision
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.SystemPropertyOverrides
import com.android.utils.Environment
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
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
        Environment.initialize()

        mockVersionedSdkLoader = mock(VersionedSdkLoader::class.java)
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(false)

        project = ProjectBuilder.builder().withProjectDir(temporaryFolderRule.newFolder()).build()
        `when`(sdkService.sdkLoader(any(), any())).thenReturn(mockVersionedSdkLoader)

        // Setup Build Services for configuration.
        val mockGeneralRegistration = mock(BuildServiceRegistration::class.java, RETURNS_DEEP_STUBS)
        `when`(globalConfig.services.buildServiceRegistry.registrations.getByName(any()))
            .thenReturn(mockGeneralRegistration)
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
        doReturn(realPropertyFor("someDeviceName")).`when`(task).managedDeviceName
        doReturn(realPropertyFor(true)).`when`(task).require64Bit


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

    private fun <T> mockEmptyProperty(): Property<T> {
        @Suppress("UNCHECKED_CAST")
        return mock(Property::class.java) as Property<T>
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

        val error = assertThrows(IllegalStateException::class.java) {
            task.taskAction()
        }
        assertThat(error.message).isEqualTo(
            """
                The system image for someDeviceName is not available and Gradle is in offline mode.
                Could not download the image or find other compatible images.
            """.trimIndent()
        )

        verify(mockVersionedSdkLoader)
            .sdkImageDirectoryProvider("system-images;android-29;default;x86_64")
        verify(mockVersionedSdkLoader)
            .offlineMode
        verifyNoMoreInteractions(mockVersionedSdkLoader)
        verifyNoInteractions(avdService)
    }

    @Test
    fun testTaskAction_errorOnWearSource() {
        val task = basicTaskSetup()
        doReturn(realPropertyFor("android-wear")).`when`(task).systemImageVendor

        val error = assertThrows(IllegalStateException::class.java) {
            task.taskAction()
        }
        assertThat(error.message).isEqualTo(
            """
                someDeviceName has a systemImageSource of android-wear.
                Wear, TV and Auto devices are presently not supported with Gradle Managed Devices.
            """.trimIndent()
        )
    }

    @Test
    fun testTaskAction_errorOnAutoProfile() {
        val task = basicTaskSetup()
        doReturn(realPropertyFor("Automotive (1024p landscape)"))
            .`when`(task).hardwareProfile

        val error = assertThrows(IllegalStateException::class.java) {
            task.taskAction()
        }
        assertThat(error.message).isEqualTo(
            """
                someDeviceName has a device profile of Automotive (1024p landscape).
                Wear, TV and Auto devices are presently not supported with Gradle Managed Devices.
            """.trimIndent()
        )
    }

    @Test
    fun creationAction_configureTask() {
        try {
            // Need to use a custom set up environment to ensure deterministic behavior.
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                // This will ensure the config believes we are running on an x86_64 Linux machine.
                // This will guarantee the x86 system-images are selected.
                systemPropertyOverrides.setProperty("os.name", "Linux")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")

                val config = ManagedDeviceSetupTask.CreationAction(
                    "setupTaskName",
                    ManagedVirtualDevice("testName").also {
                        it.device = "Pixel 3"
                        it.apiLevel = 27
                        it.systemImageSource = "aosp"
                    },
                    globalConfig
                )

                val task = mock(ManagedDeviceSetupTask::class.java, RETURNS_DEEP_STUBS)

                // default path for emulator mode ("auto-no-window")
                `when`(
                    globalConfig.services.projectOptions[
                            StringOption.GRADLE_MANAGED_DEVICE_EMULATOR_GPU_MODE])
                    .thenReturn(null)

                `when`(globalConfig.compileSdkHashString).thenReturn("some_version")
                `when`(globalConfig.buildToolsRevision).thenReturn(Revision.parseRevision("5.1"))

                // We need to create mock properties to verify/capture values in the task as
                // RETURNS_DEEP_STUBS does not work as expected with verify. Also, we can't use
                // FakeGradleProperties because they do not support disallowChanges().
                val sdkProperty = mockEmptyProperty<SdkComponentsBuildService>()
                val avdProperty = mockEmptyProperty<AvdComponentsBuildService>()
                val compileSdkVersion = mockEmptyProperty<String>()
                val buildToolsRevision = mockEmptyProperty<Revision>()
                val abiProperty = mockEmptyProperty<String>()
                val apiLevel = mockEmptyProperty<Int>()
                val systemImageVendor = mockEmptyProperty<String>()
                val hardwareProfile = mockEmptyProperty<String>()
                val emulatorGpuFlag = mockEmptyProperty<String>()
                val managedDeviceName = mockEmptyProperty<String>()
                val require64Bit = mockEmptyProperty<Boolean>()

                `when`(task.sdkService).thenReturn(sdkProperty)
                `when`(task.avdService).thenReturn(avdProperty)
                `when`(task.compileSdkVersion).thenReturn(compileSdkVersion)
                `when`(task.buildToolsRevision).thenReturn(buildToolsRevision)
                `when`(task.abi).thenReturn(abiProperty)
                `when`(task.apiLevel).thenReturn(apiLevel)
                `when`(task.systemImageVendor).thenReturn(systemImageVendor)
                `when`(task.hardwareProfile).thenReturn(hardwareProfile)
                `when`(task.emulatorGpuFlag).thenReturn(emulatorGpuFlag)
                `when`(task.managedDeviceName).thenReturn(managedDeviceName)
                `when`(task.require64Bit).thenReturn(require64Bit)

                config.configure(task)

                verify(sdkProperty).set(any<Provider<SdkComponentsBuildService>>())
                verify(sdkProperty).disallowChanges()
                verifyNoMoreInteractions(sdkProperty)

                verify(avdProperty).set(any<Provider<AvdComponentsBuildService>>())
                verify(avdProperty).disallowChanges()
                verifyNoMoreInteractions(avdProperty)

                verify(compileSdkVersion).set("some_version")
                verify(compileSdkVersion).disallowChanges()
                verifyNoMoreInteractions(compileSdkVersion)

                verify(buildToolsRevision).set(Revision.parseRevision("5.1"))
                verify(buildToolsRevision).disallowChanges()
                verifyNoMoreInteractions(buildToolsRevision)

                verify(abiProperty).set("x86")
                verify(abiProperty).disallowChanges()
                verifyNoMoreInteractions(abiProperty)

                verify(apiLevel).set(27)
                verify(apiLevel).disallowChanges()
                verifyNoMoreInteractions(apiLevel)

                verify(systemImageVendor).set("aosp")
                verify(systemImageVendor).disallowChanges()
                verifyNoMoreInteractions(systemImageVendor)

                verify(hardwareProfile).set("Pixel 3")
                verify(hardwareProfile).disallowChanges()
                verifyNoMoreInteractions(hardwareProfile)

                verify(emulatorGpuFlag).set("auto-no-window")
                verify(emulatorGpuFlag).disallowChanges()
                verifyNoMoreInteractions(emulatorGpuFlag)

                verify(managedDeviceName).set("testName")
                verify(managedDeviceName).disallowChanges()
                verifyNoMoreInteractions(managedDeviceName)

                verify(require64Bit).set(false)
                verify(require64Bit).disallowChanges()
                verifyNoMoreInteractions(require64Bit)
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }

    @Test
    fun generateSystemErrorMessage_offlineMode() {
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(true)

        val result = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "test_device_name",
            28,
            "aosp",
            true,
            mockVersionedSdkLoader
        )

        assertThat(result).isEqualTo(
            """
                The system image for test_device_name is not available and Gradle is in offline mode.
                Could not download the image or find other compatible images.
            """.trimIndent()
        )
    }

    @Test
    fun generateSystemErrorMessage_onlineMode() {
        `when`(mockVersionedSdkLoader.offlineMode).thenReturn(false)
        `when`(mockVersionedSdkLoader.allSystemImageHashes()).thenReturn(listOf())

        val result = ManagedDeviceSetupTask.generateSystemImageErrorMessage(
            "some_test_device",
            28,
            "aosp",
            true,
            mockVersionedSdkLoader
        )

        assertThat(result).isEqualTo(
            "System Image specified by some_test_device does not exist.\n\n" +
                    "Try one of the following fixes:"
        )
    }
}
