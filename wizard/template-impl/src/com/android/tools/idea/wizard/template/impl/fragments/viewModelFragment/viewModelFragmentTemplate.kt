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

package com.android.tools.idea.wizard.template.impl.fragments.viewModelFragment

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.classToResource
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import com.android.tools.idea.wizard.template.underscoreToCamelCase
import java.io.File

val viewModelFragmentTemplate
  get() = template {
    name = "Fragment (with ViewModel)"
    description = "Creates a Fragment with a ViewModel"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "BlankFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, NONEMPTY, UNIQUE)
    }

    val layoutName = stringParameter {
      name = "Fragment Layout Name"
      default = "blank_fragment"
      help = "The name of the layout to create"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { "${classToResource(fragmentClass.value)}_fragment" }
    }

    val viewModelName = stringParameter {
      name = "ViewModel Name"
      default = "BlankViewModel"
      help = "The name of the ViewModel class to create"
      constraints = listOf(CLASS, NONEMPTY, UNIQUE)
      suggest = { "${underscoreToCamelCase(classToResource(fragmentClass.value))}ViewModel" }
    }

    widgets(
      TextFieldWidget(fragmentClass),
      TextFieldWidget(layoutName),
      TextFieldWidget(viewModelName),
      LanguageWidget()
    )

    thumb { File("template_blank_fragment.png") }

    recipe = { data: TemplateData ->
      viewModelFragmentRecipe(data as ModuleTemplateData, fragmentClass.value, layoutName.value, viewModelName.value)
    }
  }
