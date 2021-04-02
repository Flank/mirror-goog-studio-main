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

package com.android.tools.idea.wizard.template.impl.activities.blankWearActivity

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
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val blankWearActivityTemplate
  get() = template {
    name = "Blank Activity"
    minApi = 25
    description = "Creates a blank activity for Wear OS"

    constraints = listOf(TemplateConstraint.AndroidX)
    category = Category.Wear
    formFactor = FormFactor.Wear
    screens = listOf(WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_main"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = true
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it the default launchable activity"
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("templates-WatchViewStub-Wear.png") }

    recipe = { data: TemplateData ->
      blankWearActivityRecipe(data as ModuleTemplateData, activityClass.value, layoutName.value, isLauncher.value, packageName.value)
    }
  }
