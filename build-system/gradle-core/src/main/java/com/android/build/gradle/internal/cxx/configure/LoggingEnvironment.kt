/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Type.*
import com.android.utils.ILogger

/**
 * This file exposes functions for logging where the logger is held in a stack on thread-local
 * storage.
 *
 * Example usage,
 *
 *      GradleSyncLoggingEnvironment(...).use {
 *          warn("falling rocks")
 *       }
 *
 * The purpose is to separate the concerns of other classes and functions from the need to log
 * and warn.
 *
 * You can make your own logger by inheriting from ThreadLoggingEnvironment. This can be useful
 * for testing.
 */

/**
 * Stack of logger environments.
 */
private val loggerStack = ThreadLocal.withInitial { mutableListOf<ThreadLoggingEnvironment>() }

/**
 * The logger environment to use if there is no other environment. It logs to the console.
 */
private val BOTTOM_LOGGING_ENVIRONMENT = object : ThreadLoggingEnvironment() {
    override fun error(message: String) = println("error: $message")
    override fun warn(message: String) = println("warn: $message")
    override fun info(message: String) = println("info: $message")
}

/**
 * The current logger.
 */
private val logger : ThreadLoggingEnvironment
    get() = loggerStack.get().firstOrNull() ?: BOTTOM_LOGGING_ENVIRONMENT

fun error(message : String) = logger.error(message)
fun warn(message : String) = logger.warn(message)
fun info(message : String) = logger.info(message)

/**
 * Push a new logging environment onto the stack of environments.
 */
private fun push(logger : ThreadLoggingEnvironment) = loggerStack.get().add(0, logger)

/**
 * Pop the top logging environment.
 */
private fun pop() = loggerStack.get().removeAt(0)

/**
 * Logger base class. When used from Java try-with-resources or Kotlin use() function it will
 * automatically register and deregister with the thread-local stack of loggers.
 */
abstract class ThreadLoggingEnvironment : AutoCloseable {
    init {
        // Okay to suppress because push doesn't have knowledge of derived classes.
        @Suppress("LeakingThis")
        push(this)
    }
    abstract fun error(message : String)
    abstract fun warn(message : String)
    abstract fun info(message : String)
    override fun close() {
        pop()
    }
}

/**
 * A logger suitable for the gradle sync environment. Warnings and errors are reported so that they
 * can be seen in Android Studio.
 */
class GradleSyncLoggingEnvironment(
    private val variantName: String,
    private val issueReporter: EvalIssueReporter,
    private val logger: ILogger) : ThreadLoggingEnvironment() {

    override fun error(message: String) {
        issueReporter.reportError(
            EXTERNAL_NATIVE_BUILD_CONFIGURATION,
            EvalIssueException(message, variantName))
    }

    override fun warn(message: String) {
        issueReporter.reportWarning(
            EXTERNAL_NATIVE_BUILD_CONFIGURATION,
            message,
            variantName)
    }

    override fun info(message: String) {
        logger.info(message)
    }
}

