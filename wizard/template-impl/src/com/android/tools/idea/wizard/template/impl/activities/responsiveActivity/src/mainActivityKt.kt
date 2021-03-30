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
package com.android.tools.idea.wizard.template.impl.activities.responsiveActivity.src

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.underscoreToLowerCamelCase

fun mainActivityKt(
  packageName: String,
  activityClass: String,
  appBarLayoutName: String,
  contentMainLayoutName: String,
  layoutName: String,
  navHostFragmentId: String,
  isViewBindingSupported: Boolean
): String {

  val appBarMainBinding = underscoreToLowerCamelCase(appBarLayoutName)
  val contentMainBinding = underscoreToLowerCamelCase(contentMainLayoutName)

  return """
package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
${importViewBindingClass(isViewBindingSupported, packageName, layoutName, Language.Kotlin)}

class $activityClass : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.${appBarMainBinding}.toolbar)

        binding.${appBarMainBinding}.fab?.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        val navController = findNavController(R.id.$navHostFragmentId)
        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(
                R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow, R.id.nav_settings),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }

        binding.${appBarMainBinding}.${contentMainBinding}.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(setOf(
                R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow))
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        // Using findViewById because NavigationView exists in different layout files
        // between w600dp and w1240dp
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            // The navigation drawer already has the items including the items in the overflow menu
            // We only inflate the overflow menu if the navigation drawer isn't visible
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.$navHostFragmentId)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.$navHostFragmentId)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
"""
}
