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
 */
data class Thumb(
  val path: File
)

/** A wrapper for collection of [Thumb]s with an optional [get]ter. Implementations usually use [Parameter.value] to choose [Thumb]. */
class Thumbs(private val thumbs: Collection<Thumb>, val get: () -> Thumb = { thumbs.first() }) {
  init {
    require(thumbs.isNotEmpty())
  }
}

/**
 * Builder for [Thumbs]. Should be used in internal DSL via [thumb] function.
 */
class ThumbsBuilder {
  private val thumbBuilders = arrayListOf<ThumbBuilder>()

  fun thumb(block: ThumbBuilder.() -> Unit): ThumbBuilder =
    ThumbBuilder().apply(block).also {
      thumbBuilders.add(it)
    }

  fun build() = Thumbs(thumbBuilders.map(ThumbBuilder::build))

  class ThumbBuilder {
    var path: File? = null

    fun build() = Thumb(path!!)
  }
}