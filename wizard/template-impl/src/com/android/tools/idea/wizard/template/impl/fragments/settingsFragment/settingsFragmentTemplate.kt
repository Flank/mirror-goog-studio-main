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

package com.android.tools.idea.wizard.template.impl.fragments.settingsFragment

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.*
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.template
import com.android.tools.idea.wizard.template.stringParameter
import java.io.File

val settingsFragmentTemplate
  get() = template {
    name = "Settings Fragment"
    description = "Creates a new fragment that allows a user to configure application settings"
    minApi = MIN_API
    constraints = listOf(TemplateConstraint.AndroidX)

    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "SettingsFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(fragmentClass),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb { File("template_settings_fragment.png") }

    recipe = { data: TemplateData ->
      settingsFragmentRecipe(data as ModuleTemplateData, fragmentClass.value, packageName.value)
    }
  }
