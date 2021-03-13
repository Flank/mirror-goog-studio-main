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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.android.ddmlib.AndroidDebugBridge
import com.google.testing.platform.api.config.AndroidSdk
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.Setup
import com.google.testing.platform.api.config.androidSdk
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.config.parseConfig
import com.google.testing.platform.api.config.setup
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.config.v1.extension.hostOrDefault
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.proto.api.config.LocalAndroidDeviceProviderProto
import com.google.testing.platform.runtime.android.AndroidDeviceProvider
import com.google.testing.platform.runtime.android.device.AndroidDevice
import java.util.concurrent.TimeUnit

/**
 * Provisions an [AndroidDevice] and returns a [DeviceController] using the DDMLIB.
 */
class DdmlibAndroidDeviceProvider() : AndroidDeviceProvider {
    companion object {
        private const val DEFAULT_ADB_COMMAND_TIMEOUT_SECONDS = 120L
    }

    private lateinit var deviceFinder: DdmlibAndroidDeviceFinder
    private lateinit var environment: Environment
    private lateinit var testSetup: Setup
    private lateinit var androidSdk: AndroidSdk
    private lateinit var deviceProviderConfig: LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider

    constructor(deviceFinder: DdmlibAndroidDeviceFinder) : this() {
        this.deviceFinder = deviceFinder
    }

    /**
     * Called to 'inject' config into this class.
     *
     * @param config The config class which has all the required data classes.
     */
    override fun configure(config: Config) {
        environment = config.environment
        testSetup = config.setup
        androidSdk = config.androidSdk
        deviceProviderConfig =
                validateLocalDeviceProviderConfig(requireNotNull(config.parseConfig()))

        if (!this::deviceFinder.isInitialized) {
            AndroidDebugBridge.init(true)
            val adb = requireNotNull(AndroidDebugBridge.createBridge(
                    config.androidSdk.adbPath,
                    /*forceNewBridge=*/false,
                    DEFAULT_ADB_COMMAND_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                "Failed to initialize AndroidDebugBridge."
            }
            deviceFinder = DdmlibAndroidDeviceFinder(adb)
        }
    }

    /**
     * Makes sure there is configuration info to contact adb server. Does not use config.isValid()
     * since that validates the config fully identifies a device.
     */
    private fun validateLocalDeviceProviderConfig(
            config: LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider
    ): LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider {
        require(config.hostOrDefault.isNotBlank()) {
            "DeviceProviderConfig must contain adbServerPort and either host, was given:\n$config"
        }
        return config
    }

    override fun provideDevice(): DeviceController {
        val deviceController = DdmlibAndroidDeviceController()
        val device = deviceFinder.findDevice(deviceProviderConfig.serial)
                ?: throw DeviceProviderException(
                        "Android device (${deviceProviderConfig.serial}) is not found.")

        deviceController.setDevice(DdmlibAndroidDevice(device))
        return deviceController
    }

    override fun releaseDevice() {
        // No need to release the local device after usage since we didn't start it.
    }

    override fun cancel(): Boolean = false
}
