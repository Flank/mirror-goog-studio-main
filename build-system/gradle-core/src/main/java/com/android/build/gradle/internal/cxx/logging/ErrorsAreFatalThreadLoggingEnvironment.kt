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

import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

/**
 * Logging environment that converts error messages into GradleExceptions. Throwing the exception
 * is delayed until close() so that all errors, warnings, and infos in the scope can also be logged.
 * Only the first seen exception is thrown. The rest are logged.
 */
class ErrorsAreFatalThreadLoggingEnvironment : ThreadLoggingEnvironment() {
    private val errors = mutableListOf<LoggingRecord>()
    private val logger = Logging.getLogger(ErrorsAreFatalThreadLoggingEnvironment::class.java)

    override fun error(message: String) {
        logger.error(message)
        errors.add(errorRecordOf(message))
    }

    override fun warn(message: String) {
        logger.warn(message)
    }

    override fun info(message: String) {
        logger.info(message)
    }

    override fun close() {
        // Close will cause the next logger to popped to the top of the stack.
        super.close()

        errors.onEach { (level, message) ->
            if (level == LoggingLevel.ERROR) {
                throw GradleException(message)
            }
        }
    }
}