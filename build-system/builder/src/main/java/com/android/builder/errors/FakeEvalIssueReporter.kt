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

package com.android.builder.errors

/**
 * Fake implementation of {@link EvalIssueReporter} to be used in tests to capture reported warnings
 * and errors.
 */
class FakeEvalIssueReporter(
    private val throwOnError : Boolean = false) : EvalIssueReporter() {

    val messages = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun reportIssue(type: Type,
            severity: Severity,
            exception: EvalIssueException) {
        messages.add(exception.message)
        when(severity) {
            Severity.ERROR -> errors.add(exception.message)
            Severity.WARNING -> warnings.add(exception.message)
        }
        if (severity == Severity.ERROR && throwOnError) {
            throw exception
        }
    }
}