/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools

import java.util.regex.Matcher
import java.util.regex.Pattern

internal const val NOT_SET : String = "Unknown"

// Example of failure message:
// Failure [INSTALL_FAILED_TEST_ONLY: installPackageLI]
// Note that some failure output can be StackTraceExceptio
private val FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(([^:]*)(:.*)?)\\]")

class InstallException(output: String, exception: Exception? = null) : RuntimeException(exception) {
    val errorMessage : String
    val errorCode: String

    init {
        val m: Matcher = FAILURE_PATTERN.matcher(output)
        if (m.matches()) {
            errorMessage = m.group(1) // e.g.: INSTALL_FAILED_TEST_ONLY: installPackageLI
            errorCode = m.group(2) // e.g.: INSTALL_FAILED_TEST_ONLY
        } else {
            errorMessage = output
            errorCode = NOT_SET
        }
    }
}
