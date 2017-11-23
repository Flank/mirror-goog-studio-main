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

package com.android.tools.lint.gradle

import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.InvalidPackageDetector
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.google.common.collect.Sets
import java.util.ArrayList

class NonAndroidIssueRegistry : BuiltinIssueRegistry() {

    override val issues: List<Issue>
        get() {
            if (ourFilteredIssues == null) {
                val sIssues = super.issues
                val result = ArrayList<Issue>(sIssues.size)
                for (issue in sIssues) {
                    if (!isAndroidSpecific(issue)) {
                        result.add(issue)
                    }
                }


                ourFilteredIssues = result
            }
            return ourFilteredIssues!!
        }

    override val api: Int = CURRENT_API

    companion object {
        private var ourFilteredIssues: List<Issue>? = null

        private val ANDROID_SPECIFIC_CHECKS = Sets.newHashSet(
                // Issues that don't include manifest or resource checking in their
                // scope but nevertheless are Android specific
                ApiDetector.INLINED,
                ApiDetector.OVERRIDE,
                ApiDetector.OBSOLETE_SDK,
                InvalidPackageDetector.ISSUE
        )

        private fun isAndroidSpecific(issue: Issue): Boolean {
            val scope = issue.implementation.scope
            return scope.contains(Scope.MANIFEST) ||
                    scope.contains(Scope.RESOURCE_FILE) ||
                    scope.contains(Scope.ALL_RESOURCE_FILES) ||
                    ANDROID_SPECIFIC_CHECKS.contains(issue)
        }
    }
}
