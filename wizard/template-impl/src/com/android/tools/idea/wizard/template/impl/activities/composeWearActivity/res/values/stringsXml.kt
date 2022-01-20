/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.renderIf


fun stringsXml(
  activityClass: String,
  isNewModule: Boolean
): String {
  val nameBlock = renderIf(!isNewModule) {"<string name=\"title_${activityToLayout(activityClass)}\">${activityClass}</string>"}
  return """
<resources>
    $nameBlock
    <!--
    This string is used for square devices and overridden by hello_world in
    values-round/strings.xml for round devices.
    -->
    <string name="hello_world">From the Square world,\nHello, %1${'$'}s!</string>
</resources>
"""
}
