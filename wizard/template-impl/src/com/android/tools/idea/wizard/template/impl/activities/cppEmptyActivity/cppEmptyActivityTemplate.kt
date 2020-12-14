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
package com.android.tools.idea.wizard.template.impl.activities.cppEmptyActivity

import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LabelWidget
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.UrlLinkWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.enumParameter
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

const val DOCUMENTATION_URL = "https://developer.android.com/ndk/guides/cpp-support.html"

val cppEmptyActivityTemplate
  get() = template {
    name = "Native C++"
    minApi = MIN_API
    description = "Creates a new project with an Empty Activity configured to use JNI"
    documentationUrl = DOCUMENTATION_URL

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)

    lateinit var layoutName: StringParameter
    val activityClass: StringParameter = stringParameter {
      name = "Activity Name"
      visible = { !isNewModule }
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = {
        layoutToActivity(layoutName.value)
      }
      default = "MainActivity"
      help = "The name of the activity class to create"
    }
    layoutName = stringParameter {
      name = "Layout Name"
      visible = { !isNewModule }
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = {
        activityToLayout(activityClass.value)
      }
      default = "activity_main"
      help = "The name of the UI layout to create for the activity"
    }
    val isLauncher: BooleanParameter = booleanParameter {
      name = "Launcher Activity"
      visible = { !isNewModule }
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }
    val cppStandard = enumParameter<CppStandardType> {
      name = "C++ Standard"
      default = CppStandardType.`Toolchain Default`
      help = "C++ Standard version"
    }
    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(activityClass),
      TextFieldWidget(layoutName),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget(),
      EnumWidget(cppStandard),
      LabelWidget("C++ feature support depends on Android NDK version."),
      UrlLinkWidget("See documentation", DOCUMENTATION_URL)
    )

    thumb {
      File("cpp_configure.png")
    }

    recipe = { data ->
      generateCppEmptyActivity(
        data as ModuleTemplateData, activityClass.value, layoutName.value, isLauncher.value, packageName.value, cppStandard.value.toString()
      )
    }
  }
