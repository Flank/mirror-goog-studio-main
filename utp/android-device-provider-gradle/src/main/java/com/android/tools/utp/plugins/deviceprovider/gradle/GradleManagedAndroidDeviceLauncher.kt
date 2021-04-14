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

import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig
import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.api.config.AndroidSdk
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.lib.process.logger.DefaultSubprocessLogger
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.Setup
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.core.device.DeviceProviderErrorSummary
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.lib.process.inject.DaggerSubprocessComponent
import com.google.testing.platform.lib.process.logger.SubprocessLogger
import com.google.testing.platform.proto.api.config.AdbConfigProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.runtime.android.AndroidDeviceProvider
import com.google.testing.platform.runtime.android.device.AndroidDevice
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import kotlin.math.pow

private const val MAX_ADB_ATTEMPTS = 4
private const val MS_PER_SECOND = 1000
private const val ADB_RETRY_DELAY_SECONDS = 2.0
const val MANAGED_DEVICE_NAME_KEY = "gradleManagedDeviceDslName"

/**
 * Creates an emulator using the [GradleManagedAndroidDeviceProvider] configuration proto and the
 * [com.google.testing.platform.proto.api.config.RunnerConfigProto].
 * Booting and killing the emulator is done directly from UTP for use by the Gradle Plugin for
 * Android.
 *
 * Ports for the emulator is decided by the emulator directly, where the device is detected by a
 * unique id created by the device launcher.
 *
 * @param subprocessComponent Dagger component factory for [Subprocess]
 */
class GradleManagedAndroidDeviceLauncher @VisibleForTesting constructor(
        private val adbManager: GradleAdbManager,
        private val emulatorHandle: EmulatorHandle,
        private val deviceControllerFactory: DeviceControllerFactory,
        private val skipRetryDelay: Boolean
) : AndroidDeviceProvider {
    private lateinit var environment: Environment
    private lateinit var testSetup: Setup
    private lateinit var androidSdk: AndroidSdk
    private lateinit var customConfig: GradleManagedAndroidDeviceProviderConfig
    private lateinit var avdFolder: String
    private lateinit var avdName: String
    private lateinit var dslName: String
    private lateinit var avdId: String
    private var enableDisplay: Boolean = false /*lateinit*/
    private var adbServerPort: Int = 0 /*lateinit*/
    private lateinit var device: AndroidDevice

    companion object {
        fun create(config: Config): GradleManagedAndroidDeviceLauncher {
            val subprocessLoggerFactory = object: SubprocessLogger.Factory {
                override fun create() = DefaultSubprocessLogger(
                        config.environment.outputDirectory,
                        flushEagerly = true)
            }
            val subprocessComponent = DaggerSubprocessComponent.builder()
                    .subprocessLoggerFactory(subprocessLoggerFactory)
                    .build()
            return GradleManagedAndroidDeviceLauncher(
                    GradleAdbManagerImpl(subprocessComponent),
                    EmulatorHandleImpl(subprocessComponent),
                    DeviceControllerFactoryImpl(),
                    skipRetryDelay = false
            )
        }
    }

    class DataBoundArgs(
            val gradleManagedDeviceProviderConfig: GradleManagedAndroidDeviceProviderConfig,
            val delegateConfigBase: ConfigBase
    ) : ConfigBase by delegateConfigBase

    override fun configure(config: Config) {
        config as DataBoundArgs
        environment = config.delegateConfigBase.environment
        testSetup = config.delegateConfigBase.setup
        androidSdk = config.delegateConfigBase.androidSdk
        customConfig = config.gradleManagedDeviceProviderConfig
        avdFolder = PathProto.Path.parseFrom(customConfig.managedDevice.avdFolder.value).path
        avdName = customConfig.managedDevice.avdName
        dslName = customConfig.managedDevice.gradleDslDeviceName
        avdId = customConfig.managedDevice.avdId
        enableDisplay = customConfig.managedDevice.enableDisplay
        adbServerPort = customConfig.adbServerPort
        adbManager.configure(androidSdk.adbPath)
        emulatorHandle.configure(PathProto.Path.parseFrom(customConfig.managedDevice.emulatorPath.value).path)
    }

    private fun makeDevice(): AndroidDevice {
        emulatorHandle.launchInstance(
                avdName,
                avdFolder,
                avdId,
                enableDisplay
        )

        val targetSerial = findSerial()

        if (targetSerial == null) {
            // Need to close the emulator if we can't connect.
            releaseDevice()
            throw DeviceProviderException(
                    "Emulator failed to attach to ADB server. Check logs for details."
            )
        }

        val emulatorPort = targetSerial.substring("emulator-".length).toInt()
        device = AndroidDevice(
                host = "localhost",
                serial = targetSerial,
                type = Device.DeviceType.VIRTUAL,
                port = emulatorPort + 1,
                emulatorPort = emulatorPort,
                serverPort = adbServerPort,
                properties = AndroidDeviceProperties()
        )
        return device
    }

    /**
     * Searches for the serial of the avd device that has the unique
     * avd id.
     *
     * After the emulator is started with the unique avd id This method is
     * called to use adb to detect which emulator has that id. This is done
     * in two steps:
     *
     * A call to "adb devices"
     * to get a list of all serials of the devices currently attached to adb.
     *
     * Then loop through each device, calling "adb -s <serial> emu avd id" to
     * retrieve each id, checking against the emulators unique id to find the
     * correct serial.
     */
    private fun findSerial(numberOfRetries: Int = MAX_ADB_ATTEMPTS): String? {
        // We may need to retry as the emulator may not have attached to the
        // adb server even though the emulator has booted.
        var retries = 0
        while (retries < numberOfRetries) {
            val serials = adbManager.getAllSerials()
            for (serial in serials) {
                // ignore non-emulator devices.
                if (!serial.startsWith("emulator-")) {
                    continue
                }
                if (avdId == adbManager.getId(serial)) {
                    return serial
                }
            }
            if (!skipRetryDelay) {
                Thread.sleep(ADB_RETRY_DELAY_SECONDS.pow(retries).toLong() * MS_PER_SECOND)
            }
            ++retries
        }

        return null
    }

    override fun provideDevice(): DeviceController {
        val deviceController: DeviceController
        try {
            deviceController = deviceControllerFactory.getController(
                    this,
                    environment,
                    testSetup,
                    androidSdk,
                    AdbConfigProto.AdbConfig.parseFrom(customConfig.adbConfig.value),
            )
        } catch (throwable: Throwable) {
            throw DeviceProviderException(
                    "Loading and configuring DeviceController failed, make sure the device controller is" +
                            " present as a part of the same jar the DeviceProvider is part of.",
                    DeviceProviderErrorSummary.UNDETERMINED,
                    throwable
            )
        }

        // As a temporary work around. We need to add the dslName to the
        // properties here. b/183651101
        // This will be overwritten if setDevice() is called again.
        val device = makeDevice()
        deviceController.setDevice(device)
        device.properties = device.properties.copy(
            map = device.properties.map +
                mapOf(MANAGED_DEVICE_NAME_KEY to dslName)
        )
        return deviceController
    }

    override fun releaseDevice() {
        emulatorHandle.closeInstance()
        // On Windows, this may not kill the instance. Search for the serial
        // on adb and run a kill command from the adb. We don't want to
        // retry to find the serial because the device either is or is not
        // connected at this point and, ideally, would be disconnected.
        findSerial(1)?.run {
            adbManager.closeDevice(this)
        }
    }

    override fun cancel(): Boolean = false
}
