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

package com.android.tools.idea.wizard.template.impl.activities.viewModelActivity

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
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import com.android.tools.idea.wizard.template.underscoreToCamelCase
import java.io.File

val viewModelActivityTemplate
  get() = template {
    name = "Fragment + ViewModel"
    minApi = MIN_API
    description = "Creates a new activity and a fragment with view model"

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val activityLayout = stringParameter {
      name = "Activity Layout Name"
      default = "main_activity"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "${classToResource(activityClass.value)}_activity" }
    }

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "MainFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "${underscoreToCamelCase(classToResource(activityClass.value))}Fragment" }
    }

    val fragmentLayout = stringParameter {
      name = "Fragment Layout Name"
      default = "main_fragment"
      help = "The name of the layout to create for the fragment"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "${classToResource(fragmentClass.value)}_fragment" }
    }

    val viewModelClass = stringParameter {
      name = "ViewModel Name"
      default = "MainViewModel"
      help = "The name of the view model class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "${underscoreToCamelCase(classToResource(fragmentClass.value))}ViewModel" }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val packageName = defaultPackageNameParameter

    val fragmentPackage = stringParameter {
      name = "Fragment package path"
      default = "ui.main"
      help = "The package path for the fragment and the view model"
      constraints = listOf(PACKAGE)
      suggest = { "ui.${classToResource(fragmentClass.value).replace("_", "")}" }
    }

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(activityLayout),
      TextFieldWidget(fragmentClass),
      TextFieldWidget(fragmentLayout),
      TextFieldWidget(viewModelClass),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      TextFieldWidget(fragmentPackage),
      LanguageWidget()
    )

    thumb { File("template_blank_activity.png") }

    recipe = { data: TemplateData ->
      viewModelActivityRecipe(data as ModuleTemplateData, activityClass.value, activityLayout.value, fragmentClass.value,
                              fragmentLayout.value, viewModelClass.value, isLauncher.value, packageName.value, fragmentPackage.value)
    }
  }
