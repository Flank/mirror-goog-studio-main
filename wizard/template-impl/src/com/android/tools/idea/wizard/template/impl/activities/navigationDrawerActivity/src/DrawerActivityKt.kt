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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity.src

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf
import com.android.tools.idea.wizard.template.underscoreToLowerCamelCase

fun drawerActivityKt(
  packageName: String,
  activityClass: String,
  appBarLayoutName: String,
  layoutName: String,
  menuName: String,
  navHostFragmentId: String,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"
  val appBarMainBinding = underscoreToLowerCamelCase(appBarLayoutName)
  return """
package ${escapeKotlinIdentifier(packageName)}
import android.os.Bundle
import android.view.Menu
${renderIf(!isViewBindingSupported) {"""
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
"""}}
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useAndroidX)}
import ${getMaterialComponentName("android.support.design.widget.NavigationView", useAndroidX)}
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import ${getMaterialComponentName("android.support.v4.widget.DrawerLayout", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

class ${activityClass} : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
${renderIf(isViewBindingSupported) {"""
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}
"""}}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ${contentViewBlock}
        setSupportActionBar(${findViewById(
          Language.Kotlin,
          isViewBindingSupported,
          id = "toolbar",
          bindingName = "binding.${appBarMainBinding}")})

        ${findViewById(
          Language.Kotlin,
          isViewBindingSupported,
          id = "fab",
          className = "FloatingActionButton",
          bindingName = "binding.${appBarMainBinding}")}.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        val drawerLayout: DrawerLayout = ${findViewById(Language.Kotlin, isViewBindingSupported, id = "drawer_layout")}
        val navView: NavigationView = ${findViewById(Language.Kotlin, isViewBindingSupported, id = "nav_view")}
        val navController = findNavController(R.id.${navHostFragmentId})
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.${menuName}, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.${navHostFragmentId})
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
"""
}
