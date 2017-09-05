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

package com.android.builder.errors

import com.android.builder.model.SyncIssue

/**
 * An configurable error handler for project evaluation and execution.
 *
 *
 * The behavior of the handler varies depending on the evaluation mode ([ ]), indicating whether the IDE is querying the project or
 * not.
 */
abstract class ConfigurableErrorHandler protected constructor(val mode: EvaluationMode) :
        EvalIssueReporter, DeprecationReporter {

    enum class EvaluationMode {
        /** Standard mode, errors should be breaking  */
        STANDARD,
        /** IDE mode. Errors should not be breaking and should generate a SyncIssue instead.  */
        IDE,
        /** Legacy IDE mode (Studio 1.0), where SyncIssue are not understood by the IDE.  */
        IDE_LEGACY
    }

    abstract fun hasSyncIssue(type: Int): Boolean

    override fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        reportIssue(
                SyncIssue.TYPE_DEPRECATED_DSL,
                SyncIssue.SEVERITY_WARNING,
                "DSL element '$oldDslElement' is obsolete and has been replaced with '$newDslElement'.\n" +
                        "It will be removed in version ${deprecationTarget.version}",
                "$oldDslElement::$newDslElement::${deprecationTarget.version}")
    }

    override fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        reportIssue(
                SyncIssue.TYPE_DEPRECATED_DSL,
                SyncIssue.SEVERITY_WARNING,
                "DSL element '$oldDslElement' is obsolete and has been replaced with '$newDslElement'.\n" +
                        "It will be removed in version ${deprecationTarget.version}\n" +
                        "For more information, see $url",
                "$oldDslElement::$newDslElement::${deprecationTarget.version}")
    }

    override fun reportObsoleteUsage(oldDslElement: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        reportIssue(
                SyncIssue.TYPE_DEPRECATED_DSL,
                SyncIssue.SEVERITY_WARNING,
                "DSL element '$oldDslElement' is obsolete and will be removed in version ${deprecationTarget.version}",
                "$oldDslElement::::${deprecationTarget.version}")
    }

    override fun reportObsoleteUsage(
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        reportIssue(
                SyncIssue.TYPE_DEPRECATED_DSL,
                SyncIssue.SEVERITY_WARNING,
                "DSL element '$oldDslElement' is obsolete and will be removed in version ${deprecationTarget.version}\n" +
                        "For more information, see $url",
                "$oldDslElement::::${deprecationTarget.version}")
    }

}
