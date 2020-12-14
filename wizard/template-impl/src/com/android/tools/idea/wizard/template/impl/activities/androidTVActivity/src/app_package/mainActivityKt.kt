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

package com.android.tools.idea.wizard.template.impl.activities.androidTVActivity.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun mainActivityKt(
  activityClass: String,
  layoutName: String,
  mainFragment: String,
  packageName: String
) = """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Loads [${mainFragment}].
 */
class ${activityClass} : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${layoutName})
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, ${mainFragment}())
                .commitNow()
        }
    }
}
"""
