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
package com.android.tools.idea.wizard.template

import com.google.common.io.Resources
import java.io.File

internal data class TemplateImpl(
  override val revision: Int,
  override val name: String,
  override val description: String,
  override val minSdk: Int,
  override val minCompileSdk: Int,
  override val requireAndroidX: Boolean,
  override val category: Category,
  override val formFactor: FormFactor,
  override val widgets: Collection<Widget<*>>,
  private val _thumb: () -> Thumb,
  override val recipe: Recipe,
  override val uiContexts: Collection<WizardUiContext>
) : Template {
  override fun thumb(): Thumb = _thumb()
}

// TODO(qumeric): split into moduleTemplate and projectTemplate to facilitate using recipe without casting
fun template(block: TemplateBuilder.() -> Unit): Template = TemplateBuilder().apply(block).build()

// TODO(qumeric): use Kotlin DSL annotations to limit visibility scope
class TemplateBuilder {
  var revision: Int? = null
  var name: String? = null
  var description: String? = null
  var minApi: Int = 1
  var minBuildApi: Int = 1
  var requireAndroidX: Boolean = false
  var category: Category? = null
  var formFactor: FormFactor? = null
  @Suppress("RedundantCompanionReference")
  var thumb: () -> Thumb = { Thumb.NoThumb }
  var recipe: Recipe? = null
  var screens: Collection<WizardUiContext> = listOf()
  var widgets = listOf<Widget<*>>()

  fun widgets(vararg widgets: Widget<*>) {
    this.widgets = widgets.toList()
  }

  /** A wrapper for collection of [Thumb]s with an optional [get]ter. Implementations usually use [Parameter.value] to choose [Thumb]. */
  fun thumb(block: () -> File) {
    val res = Resources.getResource(block().name)
    thumb = { Thumb(File(res.file)) }
  }

  internal fun build(): Template {
    checkNotNull(revision) { "Template must have a revision." }
    checkNotNull(name) { "Template must have a name." }
    checkNotNull(description) { "Template must have a description." }
    checkNotNull(category) { "Template have to specify category." }
    checkNotNull(formFactor) { "Template have to specify form factor." }
    checkNotNull(recipe) { "Template must have a recipe to run." }

    return TemplateImpl(
      revision!!,
      name!!,
      description!!,
      minApi,
      minBuildApi,
      requireAndroidX,
      category!!,
      formFactor!!,
      widgets,
      thumb,
      recipe!!,
      screens
    )
  }
}
