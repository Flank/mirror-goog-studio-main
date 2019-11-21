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

package com.android.tools.idea.wizard.template.impl.activities.settingsActivity.res.values

fun arraysXml() =
  """<resources>
    <!-- Reply Preference -->
    <string-array name="reply_entries">
        <item>Reply</item>
        <item>Reply to all</item>
    </string-array>

    <string-array name="reply_values">
        <item>reply</item>
        <item>reply_all</item>
    </string-array>
</resources>"""
