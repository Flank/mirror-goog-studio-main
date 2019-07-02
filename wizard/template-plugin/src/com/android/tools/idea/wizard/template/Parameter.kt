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

/**
 * This is a parameter which is a part of [Template].
 *
 * Each parameter will be rendered to its own field when rendering UI from [Template], albeit possibly disabled or hidden.
 * A user should provide [value]s to all parameters via interacting with UI.
 * Later this data is passed to the [Recipe] and used to render actual template files.
 */
abstract class Parameter<T>(
  private val visible: WizardParameterData.() -> Boolean = { true }
) {
  abstract val name: String
  open val help: String? = null
  /**
   * Returns false if the [Parameter] should be completely ignored.
   */
  open val enabled: () -> Boolean = { true }
  abstract val defaultValue: T
  abstract var value: T
  // should be updated only by [Parameters]
  internal lateinit var wizardParameterData: WizardParameterData

  /**
   * Tells if the [Parameter] should be shown in UI.
   * We do not show parameters which are not visible in UI, but use them (fill with data and send to the [Recipe]).
   * @see enabled
   */
  val isVisible: Boolean
    get() = enabled() && wizardParameterData.visible()
}

/**
 * A wrapped collection of [Parameter]s.
 *
 * Exists because parameters should not be accessed without setting [Parameter.wizardParameterData].
 */
class Parameters(private val parameters: Collection<Parameter<*>>) {
    operator fun invoke(wizardParameterData: WizardParameterData): Collection<Parameter<*>> {
      parameters.forEach { it.wizardParameterData = wizardParameterData }
      return parameters
    }
}
