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

// Parse install-write and install-commit output. A typical output from "install-write" is
// "Success: streamed 13740091 bytes"
// A typical success output from "install-commit" is:
// "Success"
internal val SUCCESS_OUTPUT = "Success"

// Parse install-commit output
// Example of failure message:
// Failure [INSTALL_FAILED_TEST_ONLY: installPackageLI]
internal val FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(([^:]*)(:.*)?)\\]")

internal class InstallResult(result: String) {

    val errorMessage : String
    val errorCode : String
    val successMessage : String
    val success : Boolean

    init {
        var tmpErrorMessage = ""
        var tmpErrorCode = ""
        var tmpSuccessMessage = ""
        var tmpSuccess = true
        // TODO: We should have a flow returning lines instead of breaking the output down
            val lines = result.split("\n")

            lines.forEach {
                if (it.isEmpty()) {
                    return@forEach
                }

                if (it.startsWith(SUCCESS_OUTPUT)) {
                    tmpSuccess = true
                    tmpErrorMessage = ""
                    tmpSuccessMessage = it
                    return@forEach
                }

                tmpSuccess = false
                val m: Matcher = FAILURE_PATTERN.matcher(it)
                if (m.matches()) {
                    tmpErrorMessage = m.group(1) // e.g.: INSTALL_FAILED_TEST_ONLY: installPackageLI
                    tmpErrorCode = m.group(2) // e.g.: INSTALL_FAILED_TEST_ONLY
                } else {
                    if (tmpErrorMessage.isEmpty()) {
                        tmpErrorMessage = "Unknown failure: '$it'"
                        tmpErrorCode = "UNKNOWN"
                    } else {
                        tmpErrorMessage += "\n $it"
                   }
               }
            }
        errorMessage = tmpErrorMessage
        errorCode = tmpErrorCode
        successMessage = tmpSuccessMessage
        success = tmpSuccess
    }
}
