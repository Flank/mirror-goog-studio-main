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
package com.android.tools.idea.wizard.template.impl.basicActivity

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.*
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.Separator
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.template
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import java.io.File

val basicActivityTemplate get() = template {
  revision = 1
  name = "Basic Activity"
  minApi = 14
  minBuildApi = 16
  description = "Creates a new basic activity with an app bar."

  category = Category.Activity
  formFactor = FormFactor.Mobile
  screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry)

  lateinit var activityClass: StringParameter
  val layoutName: StringParameter = stringParameter {
    name = "Layout Name"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    suggest = { activityToLayout(activityClass.value) }
    default = "activity_main"
    help = "The name of the layout to create for the activity"
    formFactor = FormFactor.Mobile
  }

  activityClass = stringParameter {
    name = "Activity Name"
    constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    suggest = { layoutToActivity(layoutName.value) }
    default = "MainActivity"
    help = "The name of the activity class to create"
  }

  val activityTitle: StringParameter = stringParameter {
    name = "Title"
    constraints = listOf(NONEMPTY)
    suggest = { activityClass.value }
    default = "MainActivity"
    help = "The name of the activity. For launcher activities, the application title."
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

  val packageName: StringParameter = stringParameter {
    name = "Package name"
    visible = { !isNewModule }
    constraints = listOf(PACKAGE)
    default = "com.mycompany.myapp"
  }

  widgets(
    TextFieldWidget(activityClass),
    TextFieldWidget(layoutName),
    TextFieldWidget(activityTitle),
    TextFieldWidget(menuName),
    CheckBoxWidget(isLauncher),
    Separator, // for example
    TextFieldWidget(contentLayoutName),
    TextFieldWidget(firstFragmentLayoutName),
    TextFieldWidget(secondFragmentLayoutName),
    PackageNameWidget(packageName),
    LanguageWidget()
    )

  thumb {
    File("template_basic_activity.png")
  }

  recipe = { data: TemplateData ->
    generateBasicActivity(
      data as ModuleTemplateData, activityClass.value, layoutName.value, contentLayoutName.value, packageName.value,
      menuName.value, activityTitle.value, isLauncher.value, firstFragmentLayoutName.value, secondFragmentLayoutName.value)
  }
}