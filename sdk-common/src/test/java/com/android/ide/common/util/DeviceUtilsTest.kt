/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.util

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.time.Duration

class DeviceUtilsTest {

    @Mock
    private lateinit var device: IDevice

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(device.version).thenReturn(AndroidVersion(27))
    }

    @Test
    fun test() {
        setUpDeviceOutput(
            "config: mcc310-mnc410-en-rGB,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27",
            "abi: arm64-v8a,armeabi-v7a,armeabi",
            "recentConfigs:",
            "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27",
            "  config: mcc310-mnc410-it-rIT,fr-rBE-ldltr-sw411dp-w774dp-h383dp-normal-long-notround-lowdr-nowidecg-land-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27",
            // fixed order for wide gamut and HDR
            "  config: en-rUS,de-rDE-ldltr-sw411dp-w411dp-h746dp-normal-long-notround-nowidecg-lowdr-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"
        )

        Truth.assertThat(device.getLanguages(Duration.ZERO))
            .containsExactly("en", "fr", "es", "it", "de")
    }
    @Test
    fun testMdnsConnectionIsFalseForPhysicalDeviceSerialNumber() {
        // Prepare
        setUpDeviceSerialNumber("435DT06WH")

        // Act
        val isClear = device.isMdnsAutoConnectUnencrypted
        val isTls = device.isMdnsAutoConnectTls

        // Assert
        Truth.assertThat(isClear).isFalse()
        Truth.assertThat(isTls).isFalse()
    }

    @Test
    fun testMdnsConnectionIsTrueForTlsSerialNumber() {
        // Prepare
        setUpDeviceSerialNumber("adb-435DT06WH-vWgJpq._adb-tls-connect._tcp.")

        // Act
        val isClear = device.isMdnsAutoConnectUnencrypted
        val isTls = device.isMdnsAutoConnectTls

        // Assert
        Truth.assertThat(isClear).isFalse()
        Truth.assertThat(isTls).isTrue()
    }

    @Test
    fun testMdnsConnectionIsTrueForClearSerialNumber() {
        // Prepare
        setUpDeviceSerialNumber("adb-435DT06WH-vWgJpq._adb._tcp.")

        // Act
        val isClear = device.isMdnsAutoConnectUnencrypted
        val isTls = device.isMdnsAutoConnectTls

        // Assert
        Truth.assertThat(isClear).isTrue()
        Truth.assertThat(isTls).isFalse()
    }

    @Test
    fun testIsMdnsAutoConnectTlsTrueForSerialNumberThatDoesntEndWithDot() {
        // Prepare
        setUpDeviceSerialNumber("adb-86UX00F4R-cYuns7._adb-tls-connect._tcp")

        // Act
        val isTls = device.isMdnsAutoConnectTls

        // Assert
        Truth.assertThat(isTls).isTrue()
    }

    @Test
    fun testIsSameAsDeviceWithSerialNumberEqualsKey() {
        // Prepare
        setUpDeviceSerialNumber("serialNumber1")

        // Act
        val sameAsDeviceWith = device.isSameAsDeviceWith("serialNumber1")

        // Assert
        Truth.assertThat(sameAsDeviceWith).isTrue()
    }

    @Test
    fun testIsSameAsDeviceWithSerialNumberIsMdnsAutoConnectTls() {
        // Prepare
        setUpDeviceSerialNumber("adb-serialNumber1-cYuns7._adb-tls-connect._tcp")

        // Act
        val sameAsDeviceWith =
            device.isSameAsDeviceWith("adb-serialNumber2-cYuns7._adb-tls-connect._tcp")

        // Assert
        Truth.assertThat(sameAsDeviceWith).isFalse()
    }

    @Test
    fun testIsSameAsDeviceWithKeyIsMdnsAutoConnectTls() {
        // Prepare
        setUpDeviceSerialNumber("serialNumber1")

        // Act
        val sameAsDeviceWith =
            device.isSameAsDeviceWith("adb-serialNumber1-cYuns7._adb-tls-connect._tcp")

        // Assert
        Truth.assertThat(sameAsDeviceWith).isTrue()
    }

    @Test
    fun testIsSameAsDeviceWithSerialNumberIsntMdnsAutoConnectTls() {
        // Prepare
        setUpDeviceSerialNumber("serialNumber1")

        // Act
        val sameAsDeviceWith = device.isSameAsDeviceWith("serialNumber2")

        // Assert
        Truth.assertThat(sameAsDeviceWith).isFalse()
    }

    @Test
    fun testIsSameAsDeviceWith() {
        // Prepare
        setUpDeviceSerialNumber("adb-serialNumber1-cYuns7._adb-tls-connect._tcp")

        // Act
        val sameAsDeviceWith = device.isSameAsDeviceWith("serialNumber1")

        // Assert
        Truth.assertThat(sameAsDeviceWith).isTrue()
    }

    private fun setUpDeviceSerialNumber(serialNumber: String) {
        Mockito.`when`(
            device.serialNumber
        ).thenAnswer {
            serialNumber
        }
    }

    private fun setUpDeviceOutput(vararg configs: String) {
        Mockito.`when`(
            device.executeShellCommand(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.anyLong(),
                Mockito.any()
            )
        ).thenAnswer {
            // get the 2nd arg (the receiver to feed it the lines).
            val receiver = it.arguments[1] as IShellOutputReceiver
            configs.forEach {
                val byteArray = "$it\n".toByteArray(Charsets.UTF_8)
                receiver.addOutput(byteArray, 0, byteArray.size)
            }
        }
    }
}
