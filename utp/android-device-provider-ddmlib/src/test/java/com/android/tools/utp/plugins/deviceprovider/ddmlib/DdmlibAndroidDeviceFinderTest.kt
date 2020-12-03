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
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit test for [DdmlibAndroidDeviceFinder].
 */
class DdmlibAndroidDeviceFinderTest {

    @Mock
    private lateinit var mockAdb: AndroidDebugBridge

    private lateinit var finder: DdmlibAndroidDeviceFinder

    @Before
    fun setUp() {
        initMocks(this)
        `when`(mockAdb.devices).thenReturn(emptyArray())

        finder = DdmlibAndroidDeviceFinder(mockAdb)
    }

    private fun createMockDevice(serialNumber: String): IDevice {
        val device = mock<IDevice>()
        `when`(device.serialNumber).thenReturn(serialNumber)
        return device
    }

    @Test
    fun deviceFound() {
        val finder = DdmlibAndroidDeviceFinder(mockAdb)

        val device = createMockDevice("12345")
        `when`(mockAdb.devices).thenReturn(arrayOf(device))

        assertThat(finder.findDevice("12345", maxRetry = 1, maxBackoffSeconds = 0)).isNotNull()
    }

    @Test
    fun deviceNotFound() {
        val finder = DdmlibAndroidDeviceFinder(mockAdb)

        assertThat(finder.findDevice("12345", maxRetry = 3, maxBackoffSeconds = 0)).isNull()

        verify(mockAdb, times(3)).devices
    }
}
