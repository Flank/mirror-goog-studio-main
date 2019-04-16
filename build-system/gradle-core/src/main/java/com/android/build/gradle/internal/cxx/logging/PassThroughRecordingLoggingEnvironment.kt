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

import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.google.gson.GsonBuilder
import java.io.File

/**
 * [ThreadLoggingEnvironment] that will record all of the messages received and then forward them
 * to the parent logger.
 */
open class PassThroughRecordingLoggingEnvironment : ThreadLoggingEnvironment() {
    private val messages = mutableListOf<LoggingRecord>()
    private val parent : LoggingEnvironment = parentLogger()

    override fun error(message: String) {
        parent.error(message)
        messages.add(errorRecordOf(message))
    }

    override fun warn(message: String) {
        parent.warn(message)
        messages.add(warnRecordOf(message))
    }

    override fun info(message: String) {
        parent.info(message)
        messages.add(infoRecordOf(message))
    }

    /**
     * Get a copy of the logging record.
     */
    val record get() = messages.map { it }
}

fun List<LoggingRecord>.toJsonString() = GsonBuilder()
    .registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
    .setPrettyPrinting()
    .create()
    .toJson(this)!!