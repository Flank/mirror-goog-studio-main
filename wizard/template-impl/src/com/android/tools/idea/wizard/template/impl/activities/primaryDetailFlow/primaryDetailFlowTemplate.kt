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

package com.android.tools.idea.wizard.template.impl.activities.primaryDetailFlow

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.extractLetters
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File
import java.util.Locale

val primaryDetailFlowTemplate
  get() = template {
    name = "Primary/Detail Flow"
    minApi = MIN_API
    description = "Creates a new primary/detail flow, allowing users to view a collection of objects as well as details for each object. This flow is presented using two columns on larger screen devices and one column on handsets and smaller screens. It also includes support for right click on the list items as well as two keyboard shortcuts. This template creates one activity, an item list fragment, and a detail fragment"

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    val objectKind = stringParameter {
      name = "Object Kind"
      default = "Item"
      help = "Other examples are Person, Book, etc."
      constraints = listOf(NONEMPTY)
    }

    val objectKindPlural = stringParameter {
      name = "Object Kind Plural"
      default = "Items"
      help = "Other examples are People, Books, etc."
      constraints = listOf(NONEMPTY)
    }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, the primary activity in the flow will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val mainNavGraphFile = stringParameter {
      name = "Main navigation graph file"
      default = "primary_details_nav_graph"
      help = "The main navigation graph file name"
      visible = { false }
      constraints = listOf(Constraint.NAVIGATION, Constraint.UNIQUE)
      suggest = { "primary_details_nav_graph" }
    }

    val childNavGraphFile = stringParameter {
      name = "Child navigation graph file"
      default = "primary_details_sub_nav_graph"
      help = "The main navigation graph file name"
      visible = { false }
      constraints = listOf(Constraint.NAVIGATION, Constraint.UNIQUE)
      suggest = { "primary_details_sub_nav_graph" }
    }

    val detailNameFragmentLayout = stringParameter {
      name = "Detail layout name"
      constraints = listOf(Constraint.LAYOUT, Constraint.UNIQUE, NONEMPTY)
      default = "fragment_${extractLetters(objectKind.value.toLowerCase(Locale.getDefault()))}_detail"
      suggest = { "fragment_${extractLetters(objectKind.value.toLowerCase(Locale.getDefault()))}_detail" }
      visible = { false }
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(objectKind),
      TextFieldWidget(objectKindPlural),
      CheckBoxWidget(isLauncher),
      PackageNameWidget(packageName),
      LanguageWidget(),
      TextFieldWidget(mainNavGraphFile),
      TextFieldWidget(childNavGraphFile),
      TextFieldWidget(detailNameFragmentLayout)
    )

    thumb { File("template_primary_detail.png") }

    recipe = { data: TemplateData ->
      primaryDetailFlowRecipe(data as ModuleTemplateData,
                              objectKind.value,
                              objectKindPlural.value,
                              isLauncher.value,
                              mainNavGraphFile.value,
                              childNavGraphFile.value,
                              detailNameFragmentLayout.value,
                              packageName.value)
    }
  }
