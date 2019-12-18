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

package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.values_w900dp


fun refsXml(
  itemListLayout: String
) = """
<resources>
    <!--
    Layout alias to replace the single-pane version of the layout with a
    two-pane version on screens with a width of at least 900 density-independent pixels (dips).

    For more on layout aliases, see:
    http://developer.android.com/training/multiscreen/screensizes.html#TaskUseAliasFilters
    -->
    <item type="layout" name="${itemListLayout}">@layout/${itemListLayout}_twopane</item>
</resources>
"""
