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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.*
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.template
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import java.io.File

val tabbedActivityTemplate
  get() = template {
    revision = 1
    name = "Tabbed Activity"
    minApi = MIN_API
    minBuildApi = MIN_API
    description = "Creates a new blank activity with tabs."

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject)

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

    val fragmentLayoutName = stringParameter {
      name = "Fragment Layout Name"
      default = "fragment_main"
      help = "The name of the layout to create for the activitys content fragment"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "fragment_${classToResource(activityClass.value)}" }
    }

    val activityTitle = stringParameter {
      name = "Title"
      default = "MainActivity"
      help = "The name of the activity. For launcher activities, the application title."
      visible = { false }
      constraints = listOf(NONEMPTY)
      suggest = { activityClass.value }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val packageName = stringParameter {
      name = "Package name"
      default = "com.mycompany.myapp"
      constraints = listOf(PACKAGE)
      suggest = { packageName }
    }

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      TextFieldWidget(fragmentLayoutName),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_blank_activity_tabs.png") }

    recipe = { data: TemplateData ->
      tabbedActivityRecipe(data as ModuleTemplateData, activityClass.value, layoutName.value, fragmentLayoutName.value,
                           isLauncher.value, packageName.value)
    }
  }
