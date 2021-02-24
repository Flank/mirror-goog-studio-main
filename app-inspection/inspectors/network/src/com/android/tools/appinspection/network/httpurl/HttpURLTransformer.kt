/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.appinspection.network.httpurl

import com.android.tools.appinspection.network.HttpTrackerFactory
import java.net.HttpURLConnection
import java.net.URLConnection
import java.util.Arrays
import javax.net.ssl.HttpsURLConnection

/**
 * Wraps URL.openConnect() and creates a wrapper class around the returned Http(s)URLConnection
 *
 * url.openConnection() ⇒ HttpURLWrapper.wrapURLConnection(url.openConnection())
 */
fun wrapURLConnection(
    wrapped: URLConnection,
    trackerFactory: HttpTrackerFactory
): URLConnection {
    // Skip the irrelevant stack trace elements (including app inspection stack frames)
    var callstack = Throwable().stackTrace
    callstack = Arrays.copyOfRange(callstack, 5, callstack.size)

    // Create the wrapper class based on the dynamic type of the wrapped object
    return when (wrapped) {
        is HttpsURLConnection -> {
            HttpsURLConnectionWrapper(wrapped, callstack, trackerFactory)
        }
        is HttpURLConnection -> {
            HttpURLConnectionWrapper(wrapped, callstack, trackerFactory)
        }
        else -> {
            wrapped
        }
    }
}
