/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.other.watchFaceService

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.enumParameter
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

enum class WatchFaceStyle {
  Analog,
  Digital
}

val watchFaceServiceTemplate
  get() = template {
    name = "Watch Face"
    constraints = listOf(TemplateConstraint.AndroidX)
    minApi = 25
    description = "Creates a watch face for Wear OS"

    category = Category.Wear
    formFactor = FormFactor.Wear
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.MenuEntry, WizardUiContext.NewModule)

    val serviceClass = stringParameter {
      name = "Service Name"
      default = "MyWatchFace"
      help = "The name of the service class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val style = enumParameter<WatchFaceStyle> {
      name = "Style"
      default = WatchFaceStyle.Analog
      help = "Watch face style"
    }

    val isInteractive = booleanParameter {
      name = "Interactive"
      default = true
      help = "Whether or not to include code for interactive touch handling"
    }

    val packageName = defaultPackageNameParameter

    widgets(
      TextFieldWidget(serviceClass),
      EnumWidget(style),
      CheckBoxWidget(isInteractive),
      PackageNameWidget(packageName),
      LanguageWidget()
    )

    thumb {
      File("watchface_service_template_thumbnail.png")
    }

    recipe = { data: TemplateData ->
      watchFaceServiceRecipe(data as ModuleTemplateData, serviceClass.value, style.value, isInteractive.value, packageName.value)
    }
  }
