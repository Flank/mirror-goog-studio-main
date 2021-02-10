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
package com.android.tools.idea.wizard.template.impl.activities.basicActivity

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NAVIGATION
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.Separator
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val basicActivityTemplate get() = template {
  name = "Basic Activity"
  minApi = MIN_API
  description = "Creates a new basic activity with the Navigation component"

  category = Category.Activity
  formFactor = FormFactor.Mobile
  screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

  lateinit var activityClass: StringParameter
  val layoutName: StringParameter = stringParameter {
    name = "Layout Name"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    suggest = { activityToLayout(activityClass.value) }
    default = "activity_main"
    help = "The name of the layout to create for the activity"
  }

  activityClass = stringParameter {
    name = "Activity Name"
    constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    suggest = { layoutToActivity(layoutName.value) }
    default = "MainActivity"
    help = "The name of the activity class to create"
  }

  val menuName: StringParameter = stringParameter {
    name = "Menu Resource File"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    suggest = { "menu_" + classToResource(activityClass.value) }
    visible = { isNewModule }
    default = "menu_main"
    help = "The name of the resource file to create for the menu items"
  }
  val isLauncher: BooleanParameter = booleanParameter {
    name = "Launcher Activity"
    visible = { !isNewModule }
    default = false
    help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
  }

  val contentLayoutName: StringParameter = stringParameter {
    name = "Content Layout Name"
    constraints = listOf(LAYOUT, UNIQUE)
    suggest = { activityToLayout(activityClass.value, "content") }
    default = "content_main"
    visible = { false }
    help = "The name of the App Bar layout to create for the activity"
  }

  val firstFragmentLayoutName: StringParameter = stringParameter {
    name = "First fragment Layout Name"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    default = "fragment_first"
    visible = { false }
    help = "The name of the layout of the Fragment as the initial destination in Navigation"
  }

  val secondFragmentLayoutName: StringParameter = stringParameter {
    name = "First fragment Layout Name"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    default = "fragment_second"
    visible = { false }
    help = "The name of the layout of the Fragment as the second destination in Navigation"
  }

  val navGraphName = stringParameter {
    name = "Navigation graph name"
    default = "nav_graph"
    help = "The name of the navigation graph"
    visible = { false }
    constraints = listOf(NAVIGATION, UNIQUE)
    suggest = { "nav_graph" }
  }

  val packageName = defaultPackageNameParameter

  widgets(
    TextFieldWidget(activityClass),
    TextFieldWidget(layoutName),
    TextFieldWidget(menuName),
    CheckBoxWidget(isLauncher),
    Separator, // for example
    PackageNameWidget(packageName),
    LanguageWidget(),

    // Invisible widgets. Defining these to impose constraints
    TextFieldWidget(contentLayoutName),
    TextFieldWidget(firstFragmentLayoutName),
    TextFieldWidget(secondFragmentLayoutName),
    TextFieldWidget(navGraphName)
  )

  thumb {
    File("template_basic_activity.png")
  }

  recipe = { data: TemplateData ->
    generateBasicActivity(
      moduleData = data as ModuleTemplateData,
      activityClass = activityClass.value,
      layoutName = layoutName.value,
      contentLayoutName = contentLayoutName.value,
      packageName = packageName.value,
      menuName = menuName.value,
      isLauncher = isLauncher.value,
      firstFragmentLayoutName = firstFragmentLayoutName.value,
      secondFragmentLayoutName = secondFragmentLayoutName.value,
      navGraphName = navGraphName.value
    )
  }
}
