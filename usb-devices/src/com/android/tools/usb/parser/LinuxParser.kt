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

package com.android.tools.usb.parser

import com.android.tools.usb.UsbDevice
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

// see: testData/linux.txt for example: Bus 003 Device 037: ID 18d1:4ee7 Google Inc.
private val PATTERN_STRING: String = "Bus \\d{3} Device \\d{3}: ID (\\w{4}):(\\w{4}) (.*)"
private val BUS_REGEX: Regex = Regex(PATTERN_STRING)
private val SERIAL_REGEX = Regex("\\h*iSerial\\h+(\\S+)\\h*(\\S*)$")

fun createUsbDevice(line: String): UsbDevice? {
    val matcher = BUS_REGEX.matchEntire(line) ?: return null
    assert(matcher.groupValues.size == 4)
    val (_, vendorId, productId, productName) = matcher.groupValues
    return UsbDevice(productName.trim(), "0x" + vendorId, "0x" + productId)
}

class LinuxParser : OutputParser {
    override fun parse(output: InputStream): List<UsbDevice> {
        val lines = BufferedReader(InputStreamReader(output, Charsets.UTF_8)).lines()

        val result = ArrayList<UsbDevice>()
        var curDevice: UsbDevice? = null
        for (next in lines) {
            val nextDevice = createUsbDevice(next)
            if (nextDevice != null) {
                if (curDevice != null) {
                    result.add(curDevice)
                }
                curDevice = nextDevice
                continue
            }
            if (curDevice != null) {
                val matchSerial = SERIAL_REGEX.matchEntire(next)
                if (matchSerial != null) {
                    val (_, _, iSerial) = matchSerial.groupValues
                    if (!iSerial.isEmpty()) {
                        curDevice = curDevice.copy(iSerial = iSerial)
                    }
                }
            }
        }
        if (curDevice != null) {
            result.add(curDevice)
        }
        return result
    }
}
