/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.logging

import com.android.build.gradle.internal.cxx.configure.ThreadLoggingEnvironment

/**
 * Logging environment that gathers all messages seen into a batch.
 *
 * At the end of the scope, all of the messages joined together into a single
 * multi-line message that is sent to the prior logger at the highest logging
 * level seen.
 *
 * This is useful for the case where all of the info and warn messages in the
 * batch are known to elucidate the errors in that batch. For example,
 * NdkLocator issues info messages about the places that were searched and
 * why they weren't suitable. In the case that a suitable NDK is found those
 * info messages aren't very interesting and so they should be logged to at
 * info level. However, if no suitable NDK is found then those info messages
 * are very important because they tell the user which NDKs are available in
 * case they want to use one of those.
 */
class BatchLoggingEnvironment : ThreadLoggingEnvironment() {
    private val messages =  mutableListOf<LoggingRecord>()

    override fun error(message: String) {
        messages.add(errorRecordOf(message))
    }

    override fun warn(message: String) {
        messages.add(warnRecordOf(message))
    }

    override fun info(message: String) {
        messages.add(infoRecordOf(message))
    }

    override fun close() {
        // Close will cause the next logger to popped to the top of the stack.
        super.close()

        if (messages.isEmpty()) return

        // Find the highest (most severe) logging level
        val highestLevel = messages
            .map { it.level }
            .maxBy { it }!!

        // Join all of the messages. Sort so that errors and warnings appear
        // first.
        val batchMessage = messages
            .sortedByDescending { it.level }
            .joinToString("\n") { it.message }

        // Forward the batch to the prior logger at the highest logging
        // level seen.
        highestLevel.log(batchMessage)
    }
}