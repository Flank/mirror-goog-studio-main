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
package com.android.tools.idea.wizard.template.impl.xml.recycleradapter

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.enumParameter
import com.android.tools.idea.wizard.template.extractLetters
import com.android.tools.idea.wizard.template.impl.activities.common.MIN_API
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.impl.fragments.listFragment.ColumnCount
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

@Suppress("EnumEntryName", "Unused")
enum class ColumnCount {
  `1 (List)`,
  `2 (Grid)`,
  `3`,
  `4`
}

var currentRecyclerViewLayout = "recycler_view_layout"
val recyclerViewAdapterFragmentTemplate
  get() = template {
    name = "Adapter with Fragment"
    description = "Creates a new empty fragment containing a list that can be rendered as a grid. Compatible back to API level $MIN_API"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val packageName = defaultPackageNameParameter

    val objectKind = stringParameter {
      name = "Object Kind"
      default = "Item"
      help = "Other examples are Person, Book, etc."
      constraints = listOf(NONEMPTY)
    }

    val fragmentClass = stringParameter {
      name = "Fragment class name"
      default = "ItemFragment"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "${extractLetters(objectKind.value)}Fragment" }
    }

    val columnCount = enumParameter<ColumnCount> {
      name = "Column Count"
      default = ColumnCount.`1 (List)`
      help = "The number of columns in the grid"
    }

    val recyclerViewItem = stringParameter {
      name = "Object content layout file name"
      default = "recycler_item"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      //suggest = { fragmentToLayout(fragmentClass.value) }
    }

    val recyclerViewLayout = stringParameter {
      name = "List layout file name"
      default = "recycler_view_layout"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { currentRecyclerViewLayout }
    }

    val adapterClassName = stringParameter {
      name = "Adapter class name"
      default = "MyItemRecyclerViewAdapter"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "My${extractLetters(objectKind.value)}RecyclerViewAdapter" }
    }

    widgets(
      PackageNameWidget(packageName),
      TextFieldWidget(objectKind),
      TextFieldWidget(fragmentClass),
      EnumWidget(columnCount),
      TextFieldWidget(recyclerViewItem),
      TextFieldWidget(recyclerViewLayout),
      TextFieldWidget(adapterClassName),
      LanguageWidget()
    )

    thumb { File("template_list_fragment.png") }

    recipe = { data: TemplateData ->
      recyclerViewAdapterRecipe(data as ModuleTemplateData, packageName.value,
        fragmentClass.value, columnCount.value,
                                recyclerViewItem.value, recyclerViewLayout.value, adapterClassName.value)
    }
  }

val recyclerViewAdapterNoFragmentTemplate
  get() = template {
    name = "Adapter Only"
    description = "Generates RecyclerView adapter and other boilerplate codes. Compatible back to API level $MIN_API"
    minApi = MIN_API
    category = Category.XML
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val packageName = defaultPackageNameParameter

    val objectKind = stringParameter {
      name = "Object Kind"
      default = "Item"
      help = "Other examples are Person, Book, etc."
      constraints = listOf(NONEMPTY)
    }

    val recyclerViewItem = stringParameter {
      name = "Object content layout file name"
      default = "recycler_item"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
    }

    val adapterClassName = stringParameter {
      name = "Adapter class name"
      default = "MyItemRecyclerViewAdapter"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "My${extractLetters(objectKind.value)}RecyclerViewAdapter" }
    }

    widgets(
      PackageNameWidget(packageName),
      TextFieldWidget(objectKind),
      TextFieldWidget(recyclerViewItem),
      TextFieldWidget(adapterClassName),
      LanguageWidget()
    )

    thumb { File("template_list_fragment.png") }

    recipe = { data: TemplateData ->
      recyclerViewAdapterRecipe(data as ModuleTemplateData, packageName.value,
                                null, null,
                                //fragmentClass.value, columnCount.value,
                                recyclerViewItem.value, currentRecyclerViewLayout, adapterClassName.value)
    }
  }
