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

package com.android.tools.appinspection.common

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val ERROR_HEADER = ("Network Inspector encountered an unexpected error. "
        + "Consider reporting a bug, including logcat output below.\n"
        + "See also: https://developer.android.com/studio/report-bugs.html#studio-bugs\n\n")

private const val LOG_TAG = "Network Inspector"
private val LOG_BUFFER_NS = TimeUnit.SECONDS.toNanos(10)

private val MESSAGE_TIMESTAMPS = ConcurrentHashMap<String, Long>()

private fun shouldLog(message: String): Boolean {
    val currentTime = System.nanoTime()
    val lastMessageTimestamp = MESSAGE_TIMESTAMPS[message]
    if (lastMessageTimestamp == null || currentTime > lastMessageTimestamp + LOG_BUFFER_NS) {
        MESSAGE_TIMESTAMPS[message] = currentTime
        return true
    }
    return false
}

/**
 * Logs an error to the commandline.
 *
 * To avoid spamming user's logcat, we cap the number of times a message
 * can be sent to once every [LOG_BUFFER_NS].
 */
fun logError(message: String, throwable: Throwable) {
    if (shouldLog(message)) {
        Log.e(LOG_TAG, "$ERROR_HEADER$message", throwable)
    }
}
