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
package com.android.tools.idea.wizard.template.impl.activities.common

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.renderIf
import com.android.tools.idea.wizard.template.underscoreToCamelCase
import com.android.tools.idea.wizard.template.underscoreToLowerCamelCase

fun findViewById(
  language: Language,
  isViewBindingSupported: Boolean,
  id: String,
  bindingName: String = "binding",
  className: String? = null,
  parentView: String? = null
) = when (language) {
  Language.Java -> findViewByIdJava(
    isViewBindingSupported = isViewBindingSupported,
    id = id,
    bindingName = bindingName,
    parentView = parentView)
  Language.Kotlin -> findViewByIdKotlin(
    isViewBindingSupported = isViewBindingSupported,
    id = id,
    bindingName = bindingName,
    className = className,
    parentView = parentView)
}

private fun findViewByIdJava(
  isViewBindingSupported: Boolean,
  id: String,
  bindingName: String,
  parentView: String? = null
) = if (isViewBindingSupported) "$bindingName.${underscoreToLowerCamelCase(id)}"
else """${renderIf(parentView != null) { "$parentView." }} findViewById(R.id.${id})"""

private fun findViewByIdKotlin(
  isViewBindingSupported: Boolean,
  id: String,
  bindingName: String,
  className: String? = null,
  parentView: String? = null
) = if (isViewBindingSupported) "$bindingName.${underscoreToLowerCamelCase(id)}"
else """${renderIf(parentView != null) { "$parentView." }} findViewById${renderIf(className != null) { "<$className>" }}(R.id.${id})"""

fun importViewBindingClass(
  isViewBindingSupported: Boolean,
  packageName: String,
  layoutName: String
) = renderIf(isViewBindingSupported) {
  "import ${escapeKotlinIdentifier(packageName)}.databinding.${layoutToViewBindingClass(layoutName)}"
}

fun layoutToViewBindingClass(layoutName: String) = underscoreToCamelCase(layoutName) + "Binding"
