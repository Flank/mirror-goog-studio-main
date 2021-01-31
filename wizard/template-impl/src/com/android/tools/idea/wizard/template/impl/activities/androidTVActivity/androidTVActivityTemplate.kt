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

package com.android.tools.idea.wizard.template.impl.activities.androidTVActivity


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

val androidTVActivityTemplate
  get() = template {
    name = "Android TV Blank Activity"
    minApi = 21
    description = "Creates a new Android TV activity using Leanback Support library"
    constraints = listOf(TemplateConstraint.AndroidX)

    category = Category.Activity
    formFactor = FormFactor.Tv
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
      name = "Main Layout Name"
      default = "main"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val mainFragment = stringParameter {
      name = "Main Fragment"
      default = "MainFragment"
      help = "The name of the main fragment"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "MainFragment" }
    }

    val detailsActivity = stringParameter {
      name = "Details Activity"
      default = "DetailsActivity"
      help = "The name of the details activity"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "DetailsActivity" }
    }

    val detailsLayoutName = stringParameter {
      name = "Details Layout Name"
      default = "details"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(detailsActivity.value) }
    }

    val detailsFragment = stringParameter {
      name = "Details Fragment"
      default = "VideoDetailsFragment"
      help = "The name of the details fragment"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "VideoDetailsFragment" }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      TextFieldWidget(mainFragment),
      TextFieldWidget(detailsActivity),
      TextFieldWidget(detailsLayoutName),
      TextFieldWidget(detailsFragment),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template-leanback-TV.png") }

    recipe = { data: TemplateData ->
      androidTVActivityRecipe(
        data as ModuleTemplateData, activityClass.value, layoutName.value, mainFragment.value, detailsActivity.value,
        detailsLayoutName.value, detailsFragment.value, packageName.value)
    }
  }
