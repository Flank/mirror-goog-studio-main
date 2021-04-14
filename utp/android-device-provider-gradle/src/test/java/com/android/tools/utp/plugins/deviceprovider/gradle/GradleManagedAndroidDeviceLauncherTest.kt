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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.android.testutils.MockitoKt.any
import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.provider.DeviceProviderConfigImpl
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.EnvironmentProto
import com.google.testing.platform.proto.api.config.SetupProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.runtime.android.device.AndroidDevice
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

/**
 * Tests for [GradleManagedDeviceLauncher]
 */
@RunWith(JUnit4::class)
class GradleManagedAndroidDeviceLauncherTest {
    private companion object {
        const val ADB_SERVER_PORT = 5037
        const val deviceName = "device1"
        const val deviceId = "myapp_myDeviceAndroidDebugTest"
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    lateinit var logDir: String

    @Mock
    lateinit var emulatorHandle: EmulatorHandle

    @Mock
    lateinit var adbManager: GradleAdbManager

    @Mock
    lateinit var deviceControllerFactory: DeviceControllerFactory

    @Mock
    lateinit var deviceController: DeviceController

    private lateinit var managedDeviceLauncher: GradleManagedAndroidDeviceLauncher

    private var androidDevice: AndroidDevice? = null

    private val deviceProviderConfig = with(GradleManagedAndroidDeviceProviderConfig.newBuilder()) {
        managedDeviceBuilder.apply {
            avdFolder = Any.pack(PathProto.Path.newBuilder().apply {
                path = "path/to/gradle/avd"
            }.build())
            avdName = deviceName
            avdId = deviceId
            enableDisplay = false
            emulatorPath = Any.pack(PathProto.Path.newBuilder().apply {
                path = "path/to/emulator"
            }.build())
            gradleDslDeviceName = "device1"
        }
        adbServerPort = ADB_SERVER_PORT
        build()
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        logDir = tempFolder.newFolder().absolutePath
        androidDevice = null
        `when`(
                deviceControllerFactory.getController(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                )
        ).thenReturn(deviceController)
        `when`(deviceController.setDevice(any())).thenAnswer {
            androidDevice = it.getArgument(0) as AndroidDevice
            androidDevice
        }
        `when`(deviceController.getDevice()).thenAnswer {
            androidDevice!!
        }

        managedDeviceLauncher = GradleManagedAndroidDeviceLauncher(
                adbManager,
                emulatorHandle,
                deviceControllerFactory,
                skipRetryDelay = true
        )
    }

    @Test
    fun provideDevice_ensureDeviceProvided() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn(deviceId)

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        deviceProviderConfig,
                        makeConfigFromDeviceProviderConfig(deviceProviderConfig)
                )
        )

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5554")
        assertThat(device.port).isEqualTo(5555)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5554)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator()
    }

    @Test
    fun provideDevice_ensureEnableDisplayWorks() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn(deviceId)

        val enableDisplayConfig =
                with(GradleManagedAndroidDeviceProviderConfig.newBuilder(deviceProviderConfig)) {
                    managedDeviceBuilder.apply {
                        enableDisplay = true
                    }
                    build()
                }

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        enableDisplayConfig,
                        makeConfigFromDeviceProviderConfig(enableDisplayConfig)
                )
        )

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5554")
        assertThat(device.port).isEqualTo(5555)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5554)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator(enableDisplay = true)
    }

    @Test
    fun provideDevice_ensureCorrectDeviceProvidedWithMultipleDevices() {
        `when`(adbManager.getAllSerials()).thenReturn(
                listOf("emulator-5554", "emulator-5556")
        )
        `when`(adbManager.getId("emulator-5554")).thenReturn("myapp_myDeviceAndroidVariantTest")
        `when`(adbManager.getId("emulator-5556")).thenReturn(deviceId)

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        deviceProviderConfig,
                        makeConfigFromDeviceProviderConfig(deviceProviderConfig)
                )
        )

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5556")
        assertThat(device.port).isEqualTo(5557)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5556)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")
        verifyCallToEmulator()
    }

    @Test
    fun provideDevice_ensureDeviceProviderSkipsUnnecessaryAdbInvocations() {
        `when`(adbManager.getAllSerials()).thenReturn(
                listOf("a_device", "emulator-5556", "emulator-5558")
        )
        // a-device skipped: non-emulator devices skipped
        `when`(adbManager.getId("emulator-5556")).thenReturn(deviceId)
        // emulator-5558 skipped: correct device already found.

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        deviceProviderConfig,
                        makeConfigFromDeviceProviderConfig(deviceProviderConfig)
                )
        )

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5556")
        assertThat(device.port).isEqualTo(5557)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5556)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")
        verifyCallToEmulator()
        verify(adbManager).configure(any())
        verify(adbManager).getAllSerials()
        verify(adbManager).getId("emulator-5556")
        verifyNoMoreInteractions(adbManager)
    }

    @Test
    fun provideDevice_emulatorFailingThrowsProviderException() {
        // if the emulator handle fails to launch, a device provider exception is thrown.
        `when`(emulatorHandle.launchInstance(any(), any(), any(), any())).thenAnswer {
            throw DeviceProviderException("")
        }

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        deviceProviderConfig,
                        makeConfigFromDeviceProviderConfig(deviceProviderConfig)
                )
        )

        assertThrows(DeviceProviderException::class.java) {
            managedDeviceLauncher.provideDevice()
        }
    }

    @Test
    fun provideDevice_failToFindIdThrowsProviderException() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn("some-other-id")

        managedDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        deviceProviderConfig,
                        makeConfigFromDeviceProviderConfig(deviceProviderConfig)
                )
        )

        assertThrows(DeviceProviderException::class.java) {
            managedDeviceLauncher.provideDevice()
        }
        verify(emulatorHandle).closeInstance()
    }

    @Test
    fun releaseDevice_callsAdbManagerCloseDevice() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5558"))
        `when`(adbManager.getId("emulator-5558")).thenReturn(deviceId)

        managedDeviceLauncher.configure(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5558")
        assertThat(device.port).isEqualTo(5559)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5558)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator()

        managedDeviceLauncher.releaseDevice()

        verify(emulatorHandle).closeInstance()
        verify(adbManager).closeDevice("emulator-5558")
    }

    private fun makeConfigFromDeviceProviderConfig(
            deviceProviderConfig: GradleManagedAndroidDeviceProviderConfig
    ): DeviceProviderConfigImpl {
        val androidSdkProto = AndroidSdkProto.AndroidSdk.getDefaultInstance()
        val environmentProto = EnvironmentProto.Environment.newBuilder().apply {
            outputDirBuilder.path = logDir
            tmpDirBuilder.path = logDir
            androidEnvironment = EnvironmentProto.AndroidEnvironment.newBuilder().apply {
                this.androidSdk = androidSdkProto
                this.testLogDirBuilder.path = "broker_logs"
                this.testRunLogBuilder.path = "test-results.log"
            }.build()
        }.build()
        val testSetupProto = SetupProto.TestSetup.getDefaultInstance()
        return DeviceProviderConfigImpl(
                environmentProto = environmentProto,
                androidSdkProto = androidSdkProto,
                testSetupProto = testSetupProto,
                configProto = Any.pack(deviceProviderConfig)
        )
    }

    private fun verifyCallToEmulator(enableDisplay: Boolean = false) {
        verify(emulatorHandle).launchInstance(
                deviceName,
                "path/to/gradle/avd",
                deviceId,
                enableDisplay
        )
    }
}
