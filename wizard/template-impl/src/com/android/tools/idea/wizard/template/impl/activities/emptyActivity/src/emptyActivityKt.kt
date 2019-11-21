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
package com.android.tools.idea.wizard.template.impl.activities.emptyActivity.src

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun emptyActivityKt(
  packageName: String, activityClass: String, layoutName: String, generateLayout: Boolean, useAndroidX: Boolean
) = """
package ${escapeKotlinIdentifier(packageName)}

import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
import android.os.Bundle

class $activityClass : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ${renderIf(generateLayout) { """setContentView(R.layout.$layoutName)""" }}
    }
}
"""
