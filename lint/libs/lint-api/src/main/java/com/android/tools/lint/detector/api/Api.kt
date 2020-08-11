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

package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.IssueRegistry

/**
 * The current API version for Lint's API.
 * Custom checks should return this value from [IssueRegistry.api].
 * Note that this is a constant, so the compiler should inline the
 * value, not read the current value from the hosting lint environment
 * when the custom lint checks are loaded into lint.
 */
const val CURRENT_API = 8

/** Describes the given API level */
fun describeApi(api: Int): String {
    return when (api) {
        9 -> "4.2+" // 4.2.0-alpha08
        8 -> "4.1" // 4.1.0-alpha06
        7 -> "4.0" // 4.0.0-alpha08
        6 -> "3.6" // 3.6.0-alpha06
        5 -> "3.5" // 3.5.0-alpha07
        4 -> "3.4" // 3.4.0-alpha03
        3 -> "3.3" // 3.3.0-alpha12
        2 -> "3.2" // 3.2.0-alpha07
        1 -> "3.1" // Initial; 3.1.0-alpha4
        0 -> "3.0 and older"
        -1 -> "Not specified"
        else -> "Future: $api"
    }
}
