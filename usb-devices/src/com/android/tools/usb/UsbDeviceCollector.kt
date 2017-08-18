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

package com.android.tools.usb

import java.io.InputStream
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Interface to hide the implementation class.
 * Also allows consumers of this library to easily test since [UsbDeviceCollectorImpl]
 * is final.
 */
interface UsbDeviceCollector {
    /**
     * returns list of [UsbDevice]
     */
    fun listUsbDevices() : CompletableFuture<List<UsbDevice>>

    /**
     * returns if the given platform is supported.
     */
    fun isSupported(platform: String) : Boolean

    /**
     * Returns an enum representing the platform OS
     */
    fun getPlatform() : Platform
}

/**
 * Returns [UsbDevice] by parsing usb devices command output.
 */
class UsbDeviceCollectorImpl : UsbDeviceCollector {
    override fun listUsbDevices() : CompletableFuture<List<UsbDevice>> {
        val currentOS = Platform.currentOS()
        if (!currentOS.supported) return CompletableFuture.completedFuture(Collections.emptyList())

        val parser = currentOS.parser()
        return parser.parse(execute(currentOS.command!!))
    }

    override fun isSupported(platform: String) = Platform.currentOS(platform).supported

    override fun getPlatform() = Platform.currentOS()

    private fun execute(command: String): InputStream {
        val builder = ProcessBuilder(command.split(" "))
        builder.redirectErrorStream(true)
        val p = builder.start()
        return p.inputStream
    }
}
