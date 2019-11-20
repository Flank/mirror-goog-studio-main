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

package com.android.tools.idea.wizard.template.impl.settingsActivity


import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import settingsActivityRecipe
import java.io.File

val settingsActivityTemplate
  get() = template {
    revision = 1
    name = "Settings Activity"
    description = "Creates a new activity that allows a user to configure application settings"
    minApi = 14
    minBuildApi = 14
    requireAndroidX = true

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry)

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "SettingsActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val activityTitle = stringParameter {
      name = "Title"
      default = "Settings"
      help = "The title of the activity."
      visible = { false }
      constraints = listOf(NONEMPTY)
      suggest = { activityClass.value }
    }

    val multipleScreens = booleanParameter {
      name = "Split settings hierarchy into separate sub-screens"
      default = false
      help = "If true, this activity will have a main settings screen that links to separate settings screens. "
    }

    val packageName = stringParameter {
      name = "Package name"
      default = "com.mycompany.myapp"
      constraints = listOf(PACKAGE)
    }

    widgets(
      TextFieldWidget(activityClass),
      CheckBoxWidget(multipleScreens),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_settings_activity.png") }

    recipe = { data: TemplateData ->
      settingsActivityRecipe(data as ModuleTemplateData, activityClass.value, activityTitle.value, multipleScreens.value, packageName.value)
    }

  }
