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
package com.android.tools.idea.wizard.template.impl

enum class MaterialColor(val colorName: String, val color: String) {
  LIGHT_BLUE_600("light_blue_600", "#039BE5"),
  LIGHT_BLUE_900("light_blue_900", "#01579B"),
  LIGHT_BLUE_A200("light_blue_A200", "#40C4FF"),
  LIGHT_BLUE_A400("light_blue_A400", "#00B0FF");

  fun xmlElement(): String = """<color name="$colorName">$color</color>"""
}