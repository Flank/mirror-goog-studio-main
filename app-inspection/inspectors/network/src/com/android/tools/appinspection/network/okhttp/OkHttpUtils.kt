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
package com.android.tools.appinspection.network.okhttp

import java.io.OutputStream
import java.util.Arrays

/**
 * Scans the current stack trace and return the section that starts with OkHttp package.
 */
fun getCallstack(okHttpPackage: String): Array<StackTraceElement> {
    val callstack = Throwable().stackTrace
    var okHttpCallstackStart = -1
    var okHttpCallstackEnd = 0
    for (i in callstack.indices) {
        val className = callstack[i].className
        if (okHttpCallstackStart < 0 && className.startsWith(okHttpPackage)) {
            okHttpCallstackStart = i
        } else if (okHttpCallstackStart >= 0 && !className.startsWith(okHttpPackage)) {
            okHttpCallstackEnd = i
            break
        }
    }
    return if (okHttpCallstackStart >= 0) {
        Arrays.copyOfRange(callstack, okHttpCallstackEnd, callstack.size)
    } else {
        emptyArray()
    }
}

/**
 * Returns an [OutputStream] that simply discards written bytes.
 *
 *
 * Our profiling instrumentation code assumes that a request's body is an [OutputStream] that is
 * written into before the request is sent. However, OkHttp uses [okio.Sink]s instead of
 * [OutputStream]s for their request bodies, so we provide a temporary stub output stream to use
 * that will be wrapped with a [okio.Sink] and can still be tracked.
 *
 *
 * TODO: We may want to clean up this assumption in HttpTracker so these OkHttp gymnastics
 * are not required.
 */
fun createNullOutputStream(): OutputStream {
    return object : OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}
