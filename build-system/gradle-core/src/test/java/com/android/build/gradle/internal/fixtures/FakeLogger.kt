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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker

open class FakeLogger: Logger {

    val debugs = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val lifeCycles = mutableListOf<String>()
    val quiets = mutableListOf<String>()
    val infos = mutableListOf<String>()
    val traces = mutableListOf<String>()

    override fun debug(format: String, arg: Any) {
        debugs.add(String.format(format, arg))
    }

    override fun warn(format: String, arg: Any) {
        warnings.add(String.format(format, arg))
    }

    override fun warn(format: String, vararg args: Any) {
        warnings.add(String.format(format, *args))
    }

    override fun warn(format: String, arg1: Any, arg2: Any) {
        warnings.add(String.format(format, arg1, arg2))
    }

    override fun warn(message: String, t: Throwable?) {
        warnings.add(message)
    }

    override fun warn(marker: Marker, msg: String) {
        warnings.add(msg)
    }

    override fun warn(marker: Marker, format: String, arg: Any) {
        warnings.add(String.format(format, arg))
    }

    override fun warn(marker: Marker, format: String, arg1: Any, arg2: Any) {
        warnings.add(String.format(format, arg1, arg2))
    }

    override fun warn(marker: Marker, format: String, vararg args: Any) {
        warnings.add(String.format(format, *args))
    }

    override fun warn(marker: Marker, message: String, t: Throwable) {
        warnings.add(message)
    }

    override fun isQuietEnabled(): Boolean = true

    override fun getName(): String = "fakelogger"

    override fun info(format: String, vararg args: Any) {
        infos.add(String.format(format, *args))
    }

    override fun info(message: String) {
        infos.add(message)
    }

    override fun info(format: String, arg: Any) {
        infos.add(String.format(format, arg))
    }

    override fun info(format: String, arg1: Any, arg2: Any) {
        infos.add(String.format(format, arg1, arg2))
    }

    override fun info(message: String, t: Throwable) {
        infos.add(message)
    }

    override fun info(marker: Marker, message: String) {
        infos.add(message)
    }

    override fun info(marker: Marker, format: String, arg: Any) {
        infos.add(String.format(format, arg))
    }

    override fun info(marker: Marker, format: String, arg1: Any, arg2: Any) {
        infos.add(String.format(format, arg1, arg2))
    }

    override fun info(marker: Marker, format: String, vararg args: Any) {
        infos.add(String.format(format, *args))
    }

    override fun info(marker: Marker, message: String, t: Throwable) {
        infos.add(message)
    }

    override fun isErrorEnabled(): Boolean = true

    override fun isErrorEnabled(marker: Marker): Boolean = true

    override fun error(message: String) {
        errors.add(message)
    }

    override fun error(format: String, arg: Any) {
        errors.add(String.format(format, arg))
    }

    override fun error(format: String, arg1: Any, arg2: Any) {
        errors.add(String.format(format, arg1, arg2))
    }

    override fun error(format: String, vararg args: Any) {
        errors.add(String.format(format, *args))
    }

    override fun error(message: String, t: Throwable?) {
        errors.add(message)
    }

    override fun error(marker: Marker, message: String) {
        errors.add(message)
    }

    override fun error(marker: Marker, format: String, arg: Any) {
        errors.add(String.format(format, arg))
    }

    override fun error(marker: Marker, format: String, arg1: Any, arg2: Any) {
        errors.add(String.format(format, arg1, arg2))
    }

    override fun error(marker: Marker, format: String, vararg args: Any) {
        errors.add(String.format(format, *args))
    }

    override fun error(marker: Marker, message: String, t: Throwable) {
        errors.add(message)
    }

    override fun isDebugEnabled(): Boolean = true

    override fun isDebugEnabled(marker: Marker): Boolean = true

    override fun log(p0: LogLevel?, p1: String?) {
        TODO("not implemented")
    }

    override fun log(p0: LogLevel?, format: String, vararg args: Any) {
        TODO("not implemented")
    }

    override fun log(p0: LogLevel?, message: String, t: Throwable) {
        TODO("not implemented")
    }

    override fun debug(format: String, vararg args: Any) {
        debugs.add(String.format(format, *args))
    }

    override fun debug(message: String) {
        debugs.add(message)
    }

    override fun debug(format: String, arg1: Any, arg2: Any) {
        debugs.add(String.format(format, arg1, arg2))
    }

    override fun debug(message: String, t: Throwable) {
        debugs.add(message)
    }

    override fun debug(marker: Marker, message: String) {
        debugs.add(message)
    }

    override fun debug(marker: Marker, format: String, arg: Any) {
        debugs.add(String.format(format, arg))
    }

    override fun debug(marker: Marker, format: String, arg1: Any, arg2: Any) {
        debugs.add(String.format(format, arg1, arg2))
    }

    override fun debug(marker: Marker, format: String, vararg args: Any) {
        debugs.add(String.format(format, *args))
    }

    override fun debug(marker: Marker, message: String, t: Throwable) {
        debugs.add(message)
    }

    override fun isEnabled(level: LogLevel?): Boolean = true

    override fun lifecycle(message: String) {
        lifeCycles.add(message)
    }

    override fun lifecycle(format: String, vararg args: Any) {
        lifeCycles.add(String.format(format, *args))
    }

    override fun lifecycle(message: String, t: Throwable) {
        lifeCycles.add(message)
    }

    override fun quiet(message: String) {
        quiets.add(message)
    }

    override fun quiet(format: String, vararg args: Any) {
        quiets.add(String.format(format, *args))
    }

    override fun quiet(message: String, t: Throwable) {
        quiets.add(message)
    }

    override fun isLifecycleEnabled(): Boolean = true

    override fun isInfoEnabled(): Boolean = true

    override fun isInfoEnabled(marker: Marker): Boolean = true

    override fun trace(message: String) {
        traces.add(message)
    }

    override fun trace(format: String, arg: Any) {
        traces.add(String.format(format, arg))
    }

    override fun trace(format: String, arg1: Any, arg2: Any) {
        traces.add(String.format(format, arg1, arg2))
    }

    override fun trace(format: String, vararg args: Any) {
        traces.add(String.format(format, *args))
    }

    override fun trace(message: String, t: Throwable) {
        traces.add(message)
    }

    override fun trace(marker: Marker, message: String) {
        traces.add(message)
    }

    override fun trace(marker: Marker, format: String, arg: Any) {
        traces.add(String.format(format, arg))
    }

    override fun trace(marker: Marker, format: String, arg1: Any, arg2: Any) {
        traces.add(String.format(format, arg1, arg2))
    }

    override fun trace(marker: Marker, format: String, vararg args: Any) {
        traces.add(String.format(format, *args))
    }

    override fun trace(marker: Marker, message: String, t: Throwable) {
        traces.add(message)
    }

    override fun isWarnEnabled(): Boolean = true

    override fun isWarnEnabled(marker: Marker): Boolean = true

    override fun isTraceEnabled(): Boolean = true

    override fun isTraceEnabled(marker: Marker): Boolean = true

    override fun warn(message: String) {
        traces.add(message)
    }
}
