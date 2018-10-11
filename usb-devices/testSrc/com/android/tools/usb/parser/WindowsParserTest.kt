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

package com.android.tools.usb.parser

import com.android.testutils.TestUtils
import com.android.tools.usb.UsbDeviceCollector
import com.android.tools.usb.UsbDeviceCollectorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class WindowsParserTest {
    private lateinit var parser: OutputParser

    @Before
    fun setup() {
        parser = WindowsParser()
    }

    @Test
    fun parseOutput() {
        val file = TestUtils.getWorkspaceFile("tools/base/usb-devices/testData/windows.txt");
        val devices = parser.parse(file.inputStream())

        // There are 7 entries in the file, but 2 of them are actually duplicates
        // of the same physical device.
        assertEquals(5, devices.size)
        assertEquals("USB Root Hub (USB 3.0)", devices[0].name)

        assertEquals("USB Input Device", devices[2].name)
        assertEquals("0x24A0", devices[2].vendorId)
        assertEquals("0x04F3", devices[2].productId)
    }

    @Test
    fun parseOutputWithDuplicateNames() {
        val file = TestUtils.getWorkspaceFile("tools/base/usb-devices/testData/windowswithdupes.txt")
        val devices = parser.parse(file.inputStream())

        // There are 42 entries in the file, but only 28 distinct
        assertEquals(28, devices.size)

        assertEquals("USB Root Hub", devices[0].name)
        assertEquals("ROOT_HUB20\\4&25BC73E0&0", devices[0].deviceId)

        assertEquals("USB Composite Device", devices[1].name)
        assertEquals("VID_046D&PID_C31C\\7&315E181E&0&3", devices[1].deviceId)

        assertEquals("Dev Tools (Galaxy S5)", devices[27].name)
        assertEquals("VID_04E8&PID_6860&MI_00\\7&36E6890D&0&0000", devices[27].deviceId)

        // All devices should be distinct
        assertEquals(devices.distinct(), devices)
    }
}
