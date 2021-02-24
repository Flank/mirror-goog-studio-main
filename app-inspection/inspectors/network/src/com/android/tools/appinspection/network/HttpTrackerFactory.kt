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
package com.android.tools.appinspection.network

import com.android.tools.appinspection.network.reporters.ConnectionReporter
import com.android.tools.appinspection.network.trackers.ConnectionTracker
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker

/**
 * This is the factory for the [HttpConnectionTracker] instances.
 */
class HttpTrackerFactory(private val inspectorConnection: androidx.inspection.Connection) {

    /**
     * Starts tracking an HTTP request based on the provided url.
     *
     * The stacktrace is used to report the call stacks of the calling code
     *
     * Returns an [HttpConnectionTracker] which can be used to track request and response details.
     */
    fun trackConnection(url: String, callstack: Array<StackTraceElement>): HttpConnectionTracker {
        return ConnectionTracker(
            url,
            callstack.fold("") { acc, stackTraceElement -> "$acc$stackTraceElement\n" },
            ConnectionReporter.createConnectionTracker(inspectorConnection)
        )
    }
}
