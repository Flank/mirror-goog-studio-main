/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.tests

import com.android.adblib.DeviceSelector
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.availableFeatures
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class TestAdbHostServicesExt: TestInstallBase() {

    // Test that an older host, supporting a subset of the device features, actually only exposes
    // the common set of features.
    @Test
    fun testFeaturesWithLimitedHost() {
        val deviceID = "1234"
        val theOneFeatureSupported = "push_sync"
        val features = setOf(theOneFeatureSupported)
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildWithFeatures(features).start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val hostServices = createHostServices(fakeAdb)

        // Act
        val availableFeaturesList = runBlocking {
            hostServices.availableFeatures(DeviceSelector.fromSerialNumber(deviceID))
        }

        val hostFeaturesList = runBlocking {
            hostServices.hostFeatures()
        }

        // Assert
        Assert.assertTrue(hostFeaturesList.size == 1)
        Assert.assertTrue(availableFeaturesList.size == 1)
        Assert.assertTrue(availableFeaturesList.contains(theOneFeatureSupported))
    }
}
