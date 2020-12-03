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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.min

/**
 * A device finder using a given [AndroidDebugBridge].
 */
class DdmlibAndroidDeviceFinder(private val adb: AndroidDebugBridge) {
    companion object {
        private const val DEFAULT_FIND_DEVICE_MAX_RETRY = 5
        private const val DEFAULT_FIND_DEVICE_INITIAL_BACKOFF_SECONDS = 1L
        private const val DEFAULT_FIND_DEVICE_MAX_BACKOFF_SECONDS = 30L
        private const val DEFAULT_FIND_DEVICE_EXP_BACKOFF_BASE = 2L
    }

    /**
     * Finds a device with a given [serial].
     */
    fun findDevice(
            serial: String,
            maxRetry: Int = DEFAULT_FIND_DEVICE_MAX_RETRY,
            initialBackoffSeconds: Long = DEFAULT_FIND_DEVICE_INITIAL_BACKOFF_SECONDS,
            maxBackoffSeconds: Long = DEFAULT_FIND_DEVICE_MAX_BACKOFF_SECONDS,
            expBackoffBase: Long = DEFAULT_FIND_DEVICE_EXP_BACKOFF_BASE
    ): IDevice? {
        var backoffSeconds = min(initialBackoffSeconds, maxBackoffSeconds)
        repeat(maxRetry) {
            adb.devices.firstOrNull {
                it.serialNumber == serial
            }?.let {
                return it
            }
            runBlocking {
                delay(backoffSeconds * 1000)
            }
            backoffSeconds = min(backoffSeconds * expBackoffBase, maxBackoffSeconds)
        }
        return null
    }
}
