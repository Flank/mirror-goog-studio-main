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

import com.android.tools.usb.UsbDevice
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

private const val NEW_DEVICE_KEY = "Availability"
private const val NAME_KEY = "Name"
private const val DEVICEID_KEY = "DeviceID"

private fun extractValues(lines: List<String>): UsbDevice? {
    var name: String? = null
    var vendorId = ""
    var productId = ""
    var deviceId = ""
    lines.forEach { line ->
        if (line.startsWith(NAME_KEY)) {
            name = line.substring(line.indexOf("=")+1)
        } else if (line.startsWith(DEVICEID_KEY)) {
            val vidIndex = line.indexOf("VID_")
            if (vidIndex != -1) {
                vendorId = "0x" + line.substring(vidIndex + 4, vidIndex + 8)
            }
            val pidIndex = line.indexOf("PID_")
            if (pidIndex != -1) {
                productId = "0x" + line.substring(pidIndex + 4, pidIndex + 8)
            }
            deviceId = line.substring(line.indexOf("=") + 1)
              .replace("&amp;", "&")
              .replace("USB\\", "")
        }
    }
    if (name != null) {
        return UsbDevice(name!!, productId, vendorId, null, null, deviceId)
    }
    return null
}

class WindowsParser : OutputParser {
    object WindowsUSBCollector :
        Collector<String, MutableList<MutableList<String>>, MutableList<MutableList<String>>> {
        override fun accumulator() = BiConsumer<MutableList<MutableList<String>>, String> { stringGroups, line ->
            // looks for a specific line, creates a new MutableList<String> and append it to the List of Lists, otherwise add non-empty lines to the last List of strings
            if (line.isEmpty()) return@BiConsumer
            if (line.startsWith(NEW_DEVICE_KEY)) {
                stringGroups.add(ArrayList())
            }

            if (!stringGroups.isEmpty()) {
                stringGroups.last().add(line)
            }
        }

        override fun combiner() = BinaryOperator<MutableList<MutableList<String>>> { t, u ->
            t.apply {
                addAll(u)
            }
        }

        //the accumulator object is the same as the result object
        override fun characteristics() = setOf(Collector.Characteristics.IDENTITY_FINISH)

        override fun supplier() = Supplier<MutableList<MutableList<String>>> { ArrayList() }
        override fun finisher(): Function<MutableList<MutableList<String>>, MutableList<MutableList<String>>> =
            Function.identity<MutableList<MutableList<String>>>()
    }

    override fun parse(output: InputStream): List<UsbDevice> {
        return BufferedReader(InputStreamReader(output, Charsets.UTF_8))
            .lines()
            .collect(WindowsUSBCollector).mapNotNull { usbLines -> extractValues(usbLines) }.distinct()
    }
}
