/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * Implementation of [IssueReporter] that simply records the errors and warnings.
 */
class FakeIssueReporter(
    private val throwOnError : Boolean = false
) : IssueReporter() {

    val messages = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    private val issueTypes = mutableSetOf<Type>()

    override fun reportIssue(type: Type,
            severity: Severity,
            exception: EvalIssueException) {
        issueTypes.add(type)
        messages.add(exception.message)
        when(severity) {
            Severity.ERROR -> errors.add(exception.message)
            Severity.WARNING -> warnings.add(exception.message)
        }
        if (severity == Severity.ERROR && throwOnError) {
            throw exception
        }
    }

    override fun hasIssue(type: Type): Boolean = issueTypes.contains(type)
}