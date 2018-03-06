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
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

private val NAME_KEY = "name"
private val VENDOR_ID_KEY = "Vendor ID"
private val PRODUCT_ID_KEY = "Product ID"
private val NAME_REGEX: Regex = Regex("(.*):$")
private val PROPERTY_REGEX: Regex = Regex("(.*):(.*)$")
private val REQUIRED_KEYS: List<String> = arrayListOf(NAME_KEY, VENDOR_ID_KEY, PRODUCT_ID_KEY)

private fun extractValues(lines: List<String>): Map<String, String> {
  val extractedValues = HashMap<String, String>()
  lines.forEach { line ->
    if (line.matches(NAME_REGEX)) {
      val name = NAME_REGEX.matchEntire(line)?.groups?.get(1)?.value
      name?.let { extractedValues.put(NAME_KEY, it.trim()) }
    } else if (line.matches(PROPERTY_REGEX)) {
      val groups = PROPERTY_REGEX.matchEntire(line)?.groups
      val key = groups?.get(1)?.value
      val value = groups?.get(2)?.value
      if (key != null && value != null) {
        extractedValues.put(key.trim(), value.trim())
      }
    }
  }
  return extractedValues
}

private fun createUsbDevice(map: Map<String, String>): UsbDevice {
  return UsbDevice(
    map[NAME_KEY]!!,
    map[VENDOR_ID_KEY]!!.split(" ")[0], // output could include vendorId aa text. i.e. 0x18d1 (Google Inc.)
    map[PRODUCT_ID_KEY]!!
  )
}

private fun hasRequiredValues(values: Map<String, String>): Boolean = REQUIRED_KEYS.all(
  values::containsKey
)

class MacParser : OutputParser {

  object USBDeclarationCollector : Collector<String, MutableList<MutableList<String>>, MutableList<MutableList<String>>> {
    override fun accumulator() = BiConsumer<MutableList<MutableList<String>>, String> { stringGroups, line ->
      // looks for a specific line, creates a new MutableList<String> and append it to the List of Lists, otherwise add non-empty lines to the last List of strings
      if (line.isEmpty()) return@BiConsumer
      if (line.matches(NAME_REGEX)) {
        stringGroups.add(ArrayList())
      }

      stringGroups.last().add(line)
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
    return BufferedReader(InputStreamReader(output))
      .lines().skip(1) // skip the first line USB: output
      .collect(USBDeclarationCollector)
      .map { usbLines -> extractValues(usbLines) }
      .filter { values -> hasRequiredValues(values) }
      .map { values -> createUsbDevice(values) }
  }
}
