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

package com.android.tools.idea.wizard.template.impl.activities.bottomNavigationActivity.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun mainActivityKt(
  activityClass: String,
  layoutName: String,
  navHostFragmentId: String,
  packageName: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.design.widget.BottomNavigationView", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

class ${activityClass} : AppCompatActivity() {

${renderIf(isViewBindingSupported) {"""
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}
"""}}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ${contentViewBlock}
        val navView: BottomNavigationView = ${findViewById(
          language = Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "nav_view")}

        val navController = findNavController(R.id.${navHostFragmentId})
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}
"""
}
