/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registry which merges many issue registries into one, and presents a unified list
 * of issues.
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
open class CompositeIssueRegistry(
    private val registries: List<IssueRegistry>
) : IssueRegistry() {
    private var mergedIssues: List<Issue>? = null

    override val issues: List<Issue>
        get() {
            val issues = this.mergedIssues
            if (issues != null) {
                return issues
            }

            var capacity = 0
            for (registry in registries) {
                capacity += registry.issues.size
            }
            val list = ArrayList<Issue>(capacity)
            for (registry in registries) {
                list.addAll(registry.issues)
            }
            this.mergedIssues = list
            return list
        }

    override val api: Int = CURRENT_API

    override val isUpToDate: Boolean
        get() {
            for (registry in registries) {
                if (!registry.isUpToDate) {
                    return false
                }
            }

            return true
        }
}
