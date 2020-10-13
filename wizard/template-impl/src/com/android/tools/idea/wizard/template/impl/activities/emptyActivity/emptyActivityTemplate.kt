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
package com.android.tools.idea.wizard.template.impl.activities.emptyActivity

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.StringParameter
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

val emptyActivityTemplate get() = template {
  name = "Empty Activity"
  minApi = MIN_API
  description = "Creates a new empty activity"

  category = Category.Activity
  formFactor = FormFactor.Mobile
  screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

  val generateLayout: BooleanParameter = booleanParameter {
    name = "Generate a Layout File"
    default = true
    help = "If true, a layout file will be generated"
  }
  lateinit var layoutName: StringParameter
  val activityClass: StringParameter = stringParameter {
    name = "Activity Name"
    constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    suggest = {
      layoutToActivity(layoutName.value)
    }
    default = "MainActivity"
    help = "The name of the activity class to create"
  }
   layoutName = stringParameter {
    name = "Layout Name"
    constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
    suggest = {
      activityToLayout(activityClass.value)
    }
    default = "activity_main"
    visible = { generateLayout.value }
    help = "The name of the UI layout to create for the activity"
  }
  val isLauncher: BooleanParameter = booleanParameter {
    name = "Launcher Activity"
    visible = { !isNewModule }
    default = false
    help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
  }
  val packageName = defaultPackageNameParameter

  widgets(
    TextFieldWidget(activityClass),
    CheckBoxWidget(generateLayout),
    TextFieldWidget(layoutName),
    CheckBoxWidget(isLauncher),
    PackageNameWidget(packageName),
    LanguageWidget()
  )

  thumb {
    File("template_empty_activity.png")
  }

  recipe = { data ->
    generateEmptyActivity(
      data as ModuleTemplateData, activityClass.value, generateLayout.value, layoutName.value, isLauncher.value, packageName.value
    )
  }
}
