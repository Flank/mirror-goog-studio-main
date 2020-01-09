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

package com.android.tools.idea.wizard.template.impl.activities.androidThingsActivity


import androidThingsActivityRecipe
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val androidThingsActivityTemplate
  get() = template {
    revision = 1
    name = "Android Things Empty Activity"
    minApi = 24
    minBuildApi = 24
    description = "Creates a new empty activity for Android Things"

    category = Category.Activity
    formFactor = FormFactor.Things
    screens = listOf(WizardUiContext.MenuEntry)

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
    }

    val isThingsLauncher = booleanParameter {
      name = "Launch activity automatically on boot"
      default = false
      help = "This is the home activity, that is the first activity that is displayed when the device boots."
    }

    val generateLayout = booleanParameter {
      name = "Generate a Layout File"
      default = true
      help = "If true, a layout file will be generated. Android Things devices dont require a display, so UI is optional."
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_main"
      help = "The name of the UI layout to create for the activity"
      visible = { generateLayout.value }
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val packageName = stringParameter {
      name = "Package name"
      default = "com.mycompany.myapp"
      constraints = listOf(PACKAGE)
    }

    widgets(
      TextFieldWidget(activityClass),
      CheckBoxWidget(isThingsLauncher),
      CheckBoxWidget(generateLayout),
      TextFieldWidget(layoutName),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("android-things.png") }

    recipe = { data: TemplateData ->
      androidThingsActivityRecipe(data as ModuleTemplateData, activityClass.value, isThingsLauncher.value, generateLayout.value,
                                  layoutName.value, packageName.value)
    }
  }
