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
package com.android.tools.idea.wizard.template.impl.activities.navigationDrawerActivity

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val navigationDrawerActivityTemplate = template {
  name = "Navigation Drawer Activity"
  minApi = MIN_API
  description = "Creates a new Activity with a Navigation Drawer"

  category = Category.Activity
  formFactor = FormFactor.Mobile
  screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

  lateinit var layoutName: StringParameter

  val activityClass = stringParameter {
    name = "Activity Name"
    default = "MainActivity"
    help = "The name of the activity class to create"
    constraints = listOf(Constraint.CLASS, Constraint.UNIQUE, Constraint.NONEMPTY)
    suggest = { layoutToActivity(layoutName.value) }
  }

  layoutName = stringParameter {
    name = "Layout Name"
    default = "activity_main"
    help = "The name of the layout to create for the activity"
    constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE, Constraint.NONEMPTY)
    suggest = { activityToLayout(activityClass.value) }
  }

  val isLauncher = booleanParameter {
    name = "Launcher Activity"
    default = false
    help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
  }

  val packageName = defaultPackageNameParameter

  val appBarLayoutName = stringParameter {
    name = "App Bar Layout Name"
    default = "app_bar_main"
    help = "The name of the App Bar layout to create for the activity"
    visible = { false }
    constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE)
    suggest = { activityToLayout(activityClass.value, "app_bar") }
  }

  val navHeaderLayoutName = stringParameter {
    name = "Navigation Header Layout Name"
    default = "nav_header_main"
    help = "The name of the Navigation header layout to create for the activity"
    visible = { false }
    constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE)
    suggest = { activityToLayout(activityClass.value, "nav_header") }
  }

  val drawerMenu = stringParameter {
    name = "Drawer Menu Name"
    default = "activity_main_drawer"
    help = "The name of the Drawer menu to create for the activity"
    visible = { false }
    constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE)
    suggest = { "${layoutName.value}_drawer" }
  }

  val contentLayoutName = stringParameter {
    name = "Content Layout Name"
    default = "content_main"
    help = "The name of the content layout to create for the activity"
    visible = { false }
    constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE)
    suggest = { activityToLayout(activityClass.value, "content") }
  }

  val navGraphName = stringParameter {
    name = "Navigation graph name"
    default = "mobile_navigation"
    help = "The name of the navigation graph"
    visible = { false }
    constraints = listOf(Constraint.NAVIGATION, Constraint.UNIQUE)
    suggest = { "mobile_navigation" }
  }

  widgets(
    TextFieldWidget(activityClass),
    TextFieldWidget(layoutName),
    CheckBoxWidget(isLauncher),
    TextFieldWidget(packageName),
    // Below are invisible widgets. Defining as widgets to impose constraints
    TextFieldWidget(appBarLayoutName),
    TextFieldWidget(drawerMenu),
    TextFieldWidget(navHeaderLayoutName),
    TextFieldWidget(contentLayoutName),
    TextFieldWidget(navGraphName),
    LanguageWidget()
  )

  thumb { File("template_blank_activity_drawer.png") }

  recipe = { data: TemplateData ->
    generateNavigationDrawer(
      data = data as ModuleTemplateData,
      activityClass = activityClass.value,
      layoutName = layoutName.value,
      isLauncher = isLauncher.value,
      packageName = packageName.value,
      appBarLayoutName = appBarLayoutName.value,
      navHeaderLayoutName = navHeaderLayoutName.value,
      drawerMenu = drawerMenu.value,
      contentLayoutName = contentLayoutName.value,
      navGraphName = navGraphName.value
    )
  }
}
