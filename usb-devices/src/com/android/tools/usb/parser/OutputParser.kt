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
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CompletableFuture

/**
 * Converts command output to List of UsbDevices
 */
interface OutputParser {
    fun parse(output: InputStream) : List<UsbDevice>
}

/**
 * Placeholder parser that returns an empty list.
 */
class EmptyParser : OutputParser {
    override fun parse(output: InputStream): List<UsbDevice> {
        return Collections.emptyList()
    }
}
