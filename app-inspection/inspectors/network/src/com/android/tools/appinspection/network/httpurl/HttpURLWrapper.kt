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
package com.android.tools.appinspection.network.httpurl

import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.util.Arrays

/**
 * A set of helpers for the Network profiler bytecode instrumentation.
 *
 * The calls to the helper methods are injected in a post build bytecode transformation task
 */
private fun wrapURLConnectionHelper(wrapped: URLConnection): URLConnection {
    // Skip the helper frames (the helpers are implemented so every path has exactly two helper frames)
    val callstack =
        Throwable().stackTrace.let { stacktrace ->
            Arrays.copyOfRange(stacktrace, 2, stacktrace.size)
        }

    // Create the wrapper class based on the dynamic type of the wrapped object
    return when (wrapped) {
        is HttpsURLConnection -> HttpsURLConnection(wrapped, callstack)
        is HttpURLConnection -> HttpURLConnection(wrapped, callstack)
        else -> wrapped
    }
}

/**
 * Wraps URL.openConnect() and creates a wrapper class around the returned Http(s)URLConnection
 *
 * url.openConnection() ⇒ HttpURLWrapper.wrapURLConnection(url.openConnection())
 */
fun wrapURLConnection(wrapped: URLConnection): URLConnection {
    return wrapURLConnectionHelper(wrapped)
}

/**
 * Wraps URL.openStream()
 *
 * url.openStream() ⇒ HttpURLWrapper.wrapOpenStream(url)
 */
fun wrapOpenStream(url: URL): InputStream {
    return wrapURLConnectionHelper(url.openConnection()).getInputStream()
}

/**
 * Wraps URL.getContent()
 *
 * url.getContent() ⇒ HttpURLWrapper.wrapGetContent(url)
 */
fun wrapGetContent(url: URL): Any {
    return wrapURLConnectionHelper(url.openConnection()).content
}

/**
 * Wraps URL.getContent(Class[] types)
 *
 * url.getContent(types) ⇒ HttpURLWrapper.wrapGetContent(url, types)
 */
fun wrapGetContent(url: URL, types: Array<Class<*>>): Any {
    return wrapURLConnectionHelper(url.openConnection()).getContent(types)
}
