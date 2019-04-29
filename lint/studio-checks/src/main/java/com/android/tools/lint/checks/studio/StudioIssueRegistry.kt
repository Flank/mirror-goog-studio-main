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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API

class StudioIssueRegistry : IssueRegistry() {

    override val api = CURRENT_API

    override val issues = listOf(
        FileComparisonDetector.ISSUE,
        ForkJoinPoolDetector.COMMON_FJ_POOL,
        ForkJoinPoolDetector.NEW_FJ_POOL,
        ImplicitExecutorDetector.ISSUE,
        RegexpPathDetector.ISSUE,
        SwingUtilitiesDetector.ISSUE,
        SwingWorkerDetector.ISSUE,
        WrongThreadDetector.ISSUE
    )

// TODO other checks:
// TODO: Creating file writer without UTF-8!
// TODO: Creating file reader without UTF-8!
}
