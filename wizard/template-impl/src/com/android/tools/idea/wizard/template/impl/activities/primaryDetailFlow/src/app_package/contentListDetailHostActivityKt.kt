/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass

fun contentListDetailHostActivityKt(
  packageName: String,
  collection: String,
  activityLayout: String,
  navHostFragmentId: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {
  val layoutName = "activity_${activityLayout}"
  val contentViewBlock = if (isViewBindingSupported) """
     val binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"

  return """
package ${escapeKotlinIdentifier(packageName)}


import android.os.Bundle
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

class ${collection}DetailHostActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        $contentViewBlock

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.${navHostFragmentId}) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.${navHostFragmentId})
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
  """
}
