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
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.provider.DeviceProviderConfigImpl
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.EnvironmentProto
import com.google.testing.platform.proto.api.config.LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider
import com.google.testing.platform.proto.api.config.SetupProto
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit tests for [DdmlibAndroidDeviceProvider].
 */
class DdmlibAndroidDeviceProviderTest {

    @Mock
    private lateinit var mockDeviceFinder: DdmlibAndroidDeviceFinder

    private lateinit var environmentProto: EnvironmentProto.Environment
    private lateinit var testSetupProto: SetupProto.TestSetup
    private lateinit var androidSdkProto: AndroidSdkProto.AndroidSdk

    @Before
    fun setup() {
        initMocks(this)

        environmentProto = EnvironmentProto.Environment.getDefaultInstance()
        testSetupProto = SetupProto.TestSetup.getDefaultInstance()
        androidSdkProto = AndroidSdkProto.AndroidSdk.getDefaultInstance()
    }

    private fun createProvider(config: LocalAndroidDeviceProvider): DdmlibAndroidDeviceProvider {
        val provider = DdmlibAndroidDeviceProvider(mockDeviceFinder)
        provider.configure(DeviceProviderConfigImpl(
                environmentProto, testSetupProto, androidSdkProto, Any.pack(config)))
        return provider
    }

    private fun createProvider(serial: String): DdmlibAndroidDeviceProvider {
        return createProvider(LocalAndroidDeviceProvider.newBuilder().apply {
            this.serial = serial
        }.build())
    }

    @Test
    fun noDevicesFoundThrowsDeviceProviderException() {
        val provider = createProvider("serial-1234")

        assertThrows(DeviceProviderException::class.java) {
            provider.provideDevice()
        }
    }

    @Test
    fun deviceFound() {
        val mockDevice = mock<IDevice>()
        `when`(mockDevice.serialNumber).thenReturn("serial-1234")
        `when`(mockDeviceFinder.findDevice(
                eq("serial-1234"),
                anyInt(), anyLong(), anyLong(), anyLong())).thenReturn(mockDevice)
        val provider = createProvider("serial-1234")

        val controller = provider.provideDevice()

        assertThat(controller).isNotNull()
    }
}
