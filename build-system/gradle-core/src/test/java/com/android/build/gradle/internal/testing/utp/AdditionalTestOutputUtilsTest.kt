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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ddmlib.MultiLineReceiver
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.junit.Before
import org.mockito.Mockito.anyLong

/**
 * Unit tests for functions in AdditionalTestOutputUtils.kt.
 */
class AdditionalTestOutputUtilsTest {
    @get:Rule
    var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    companion object {
        private val QUERY: String = """
            content query --uri content://media/external/file --projection _data --where "_data LIKE '%/Android'"
        """.trimIndent()
    }

    @Mock private lateinit var device: DeviceConnector
    @Mock private lateinit var managedDevice: UtpManagedDevice
    @Mock private lateinit var testData: StaticTestData

    @Before
    fun setupMocks() {
        `when`(testData.instrumentationTargetPackageId).thenReturn("testedApplicationId")
        `when`(device.executeShellCommand(eq(QUERY), any(), anyLong(), any())).then {
            val receiver: MultiLineReceiver = it.getArgument(1)
            receiver.processNewLines(arrayOf("Row: 0 _data=/storage/emulated/0/Android"))
        }
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi15() {
        `when`(device.apiLevel).thenReturn(15)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWhenQueryReturnsNull() {
        `when`(device.executeShellCommand(eq(QUERY), any(), anyLong(), any())).then {
            val receiver: MultiLineReceiver = it.getArgument(1)
            receiver.processNewLines(arrayOf(""))
        }

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi16() {
        `when`(device.apiLevel).thenReturn(16)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isEqualTo(
            "/storage/emulated/0/Android/data/testedApplicationId/files/test_data")
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnDeviceWithApi29() {
        `when`(device.apiLevel).thenReturn(29)

        val dir = findAdditionalTestOutputDirectoryOnDevice(device, testData)

        assertThat(dir).isEqualTo(
            "/sdcard/Android/media/testedApplicationId/additional_test_output")
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi15() {
        `when`(managedDevice.api).thenReturn(15)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi16() {
        `when`(managedDevice.api).thenReturn(16)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isNull()
    }

    @Test
    fun findAdditionalTestOutputDirectoryOnManagedDeviceWithApi29() {
        `when`(managedDevice.api).thenReturn(29)

        val dir = findAdditionalTestOutputDirectoryOnManagedDevice(managedDevice, testData)

        assertThat(dir).isEqualTo(
            "/sdcard/Android/media/testedApplicationId/additional_test_output")
    }
}
