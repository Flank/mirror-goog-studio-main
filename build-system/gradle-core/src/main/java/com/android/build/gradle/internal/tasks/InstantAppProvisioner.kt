/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.builder.testing.ConnectedDevice
import com.android.builder.testing.api.DeviceProvider
import com.android.instantapp.provision.ProvisionException
import com.android.instantapp.provision.ProvisionListener
import com.android.instantapp.provision.ProvisionRunner
import com.google.common.annotations.VisibleForTesting
import java.io.File
import org.gradle.api.logging.Logger



/**
 * Code consumed by [@link InstantAppProvisionTask]. In a separate class for testing without
 * exposing methods.
 */
class InstantAppProvisioner (
    private val instantAppSdk: File,
    private val deviceProvider: DeviceProvider,
    private val logger: Logger
) {

    private var fakeProvisionRunner: ProvisionRunner? = null

    fun provisionDevices() {
        val listener = object : ProvisionListener {
            override fun printMessage(message: String) {
                logger.info(message)
            }

            override fun logMessage(
                message: String, e: ProvisionException?
            ) {
                if (e == null) {
                    logger.debug(message)
                } else {
                    logger.debug(message, e)
                    logger.error(message, e)
                }
            }

            override fun setProgress(fraction: Double) {}

            override fun isCancelled(): Boolean {
                return false
            }
        }

        val provisionRunner: ProvisionRunner =
            fakeProvisionRunner ?: ProvisionRunner(instantAppSdk, listener)


        deviceProvider.use {
            val devices = deviceProvider.devices
            for (device in devices) {
                if (device is ConnectedDevice) {
                    provisionRunner.runProvision(device.iDevice)
                }
            }

            null
        }
    }

    @VisibleForTesting
    fun setFakeProvisionRunner(fakeProvisionRunner: ProvisionRunner) {
        this.fakeProvisionRunner = fakeProvisionRunner
    }
}