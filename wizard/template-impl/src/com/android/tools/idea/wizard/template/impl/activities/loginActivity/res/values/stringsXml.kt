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

package com.android.tools.idea.wizard.template.impl.activities.loginActivity.res.values

import com.android.tools.idea.wizard.template.renderIf


fun stringsXml(
  simpleName: String,
  activityTitle: String,
  isNewModule: Boolean): String {

  val title = renderIf(!isNewModule) {"""
    <string name="title_${simpleName}">${activityTitle}</string>
    """.trimIndent()}

  return """<resources>
    <!-- Strings related to login -->
    ${title} 
    <string name="prompt_email">Email</string>
    <string name="prompt_password">Password</string>
    <string name="action_sign_in">Sign in or register</string>
    <string name="action_sign_in_short">Sign in</string>
    <string name="welcome">"Welcome !"</string>
    <string name="invalid_username">Not a valid username</string>
    <string name="invalid_password">Password must be >5 characters</string>
    <string name="login_failed">"Login failed"</string>
</resources>
"""
}
