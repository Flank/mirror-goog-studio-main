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

import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.Configurable
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.parseConfig
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.provider.DeviceProviderConfigImpl
import com.google.testing.platform.runtime.android.AndroidDeviceProvider

/**
 * Spins up an emulator using the [GradleManagedAndroidDeviceLauncher] class. The annotated
 * values are injected by the dataBinder. [GradleManagedAndroidDeviceLauncher] is injected by a
 * separate Dagger component from this level.
 */
class GradleManagedAndroidDeviceProvider : AndroidDeviceProvider, Configurable {

    /** Environment config to get log directory for the Virtual Launcher. */
    private lateinit var environment: Environment

    /** The device provider implementation to run the managed Gradle Device. */
    private lateinit var managedGradleDeviceLauncher: GradleManagedAndroidDeviceLauncher

    override fun configure(config: Config) {
        config as DeviceProviderConfigImpl
        environment = config.environment

        managedGradleDeviceLauncher = GradleManagedAndroidDeviceLauncher.create(config)

        managedGradleDeviceLauncher.configure(
                GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                        gradleManagedDeviceProviderConfig = config.parseConfig()!!,
                        delegateConfigBase = config
                )
        )
    }

    override fun provideDevice(): DeviceController {
        return managedGradleDeviceLauncher.provideDevice()
    }

    override fun releaseDevice() {
        managedGradleDeviceLauncher.releaseDevice()
    }

    override fun cancel(): Boolean = false
}
