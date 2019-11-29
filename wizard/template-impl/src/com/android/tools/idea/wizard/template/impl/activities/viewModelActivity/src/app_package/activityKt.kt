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

package com.android.tools.idea.wizard.template.impl.activities.viewModelActivity.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun activityKt(
  activityClass: String,
  activityLayout: String,
  fragmentClass: String,
  fragmentPackage: String,
  packageName: String,
  superClassFqcn: String) =

  """package ${escapeKotlinIdentifier(packageName)}

import ${superClassFqcn}
import android.os.Bundle
import ${escapeKotlinIdentifier(packageName)}.${escapeKotlinIdentifier(fragmentPackage)}.${fragmentClass}

class ${activityClass} : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${activityLayout})
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ${fragmentClass}.newInstance())
                .commitNow()
        }
    }
}
"""
