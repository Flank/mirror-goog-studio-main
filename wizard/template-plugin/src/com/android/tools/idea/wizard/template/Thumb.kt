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

import java.io.File

/**
 * Stores information about a thumb which should be displayed in galleries such as New Activity Gallery.
 * TODO(qumeric): consider adding the following information:
 * resizible: (None, Horizontal, Vertical, Both)
 * expandedStyle: (None, Text, List, Picture)
 * style: (Analog, Digital)
 * adFormat: (Interstitial, Banner)
 * minWidth, minHeight: Int
 *
 * Also, make inline class/typealias?
 */
data class Thumb(
  val path: File
)
