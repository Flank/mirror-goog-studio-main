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

package com.android.tools.idea.wizard.template.impl.activities.scrollActivity

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
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val scrollActivityTemplate
  get() = template {
    name = "Scrolling Activity"
    minApi = MIN_API
    description = "Creates a new vertical scrolling activity"

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "ScrollingActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_scrolling"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
    }

    val menuName = stringParameter {
      name = "Menu Resource Name"
      default = "menu_scrolling"
      help = "The name of the resource file to create for the menu items"
      visible = { isNewModule }
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "menu_${classToResource(activityClass.value)}" }
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val contentLayoutName = stringParameter {
      name = "Content Layout Name"
      default = "content_scrolling"
      help = "The name of the content layout to create for the activity"
      visible = { false }
      constraints = listOf(LAYOUT, UNIQUE)
      suggest = { activityToLayout(activityClass.value, "content") }
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      TextFieldWidget(menuName),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_scroll_activity.png") }

    recipe = { data: TemplateData ->
      scrollActivityRecipe(data as ModuleTemplateData, activityClass.value, layoutName.value, contentLayoutName.value,
                           menuName.value, isLauncher.value, packageName.value)
    }

  }
