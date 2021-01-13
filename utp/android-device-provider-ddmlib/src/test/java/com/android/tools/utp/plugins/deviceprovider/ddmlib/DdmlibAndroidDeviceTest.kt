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

import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.api.device.Device
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit tests for [DdmlibAndroidDevice].
 */
class DdmlibAndroidDeviceTest {

    @Mock
    private lateinit var mockIDevice: IDevice

    @Before
    fun setup() {
        initMocks(this)
    }

    @Test
    fun physicalDevice() {
        `when`(mockIDevice.isEmulator).thenReturn(false)

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.type).isEqualTo(Device.DeviceType.PHYSICAL)
    }

    @Test
    fun virtualDevice() {
        val emulatorName = "A good name"
        `when`(mockIDevice.isEmulator).thenReturn(true)
        `when`(mockIDevice.avdName).thenReturn(emulatorName)

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.avdName).isEqualTo(emulatorName)
        assertThat(device.properties.avdName).isEqualTo(emulatorName)
    }

    @Test
    fun serial() {
        `when`(mockIDevice.serialNumber).thenReturn("serial-1234")

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.serial).isEqualTo("serial-1234")
    }

    @Test
    fun properties() {
        fun MultiLineReceiver.addOutput(message: String) {
            val bytes = message.toByteArray()
            addOutput(bytes, 0, bytes.size)
            flush()
        }
        `when`(mockIDevice.executeShellCommand(eq("printenv"), any())).then {
            it.getArgument<MultiLineReceiver>(1).addOutput("""
                _=/system/bin/printenv
                ANDROID_DATA=/data
                DOWNLOAD_CACHE=/data/cache
            """.trimIndent())
        }
        `when`(mockIDevice.executeShellCommand(eq("getprop"), any())).then {
            it.getArgument<MultiLineReceiver>(1).addOutput("""
                [dalvik.vm.appimageformat]: [lz4]
                [dalvik.vm.dex2oat-Xms]: [64m]
                [dalvik.vm.dex2oat-Xmx]: [512m]
            """.trimIndent())
        }

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.properties.map).containsExactly(
                "_", "/system/bin/printenv",
                "ANDROID_DATA", "/data",
                "DOWNLOAD_CACHE", "/data/cache",
                "dalvik.vm.appimageformat", "lz4",
                "dalvik.vm.dex2oat-Xms", "64m",
                "dalvik.vm.dex2oat-Xmx", "512m",
        )
    }
}
