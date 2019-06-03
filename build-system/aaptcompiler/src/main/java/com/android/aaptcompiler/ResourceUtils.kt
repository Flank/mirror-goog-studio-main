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

package com.android.aaptcompiler

import com.android.aaptcompiler.android.ResValue
import com.android.aaptcompiler.android.stringToInt

fun tryParseBool(string: String) : BinaryPrimitive? {
  val boolean = parseAsBool(string)
  boolean ?: return null

  val data = if (boolean) -1 else 0
  return BinaryPrimitive(ResValue(ResValue.DataType.INT_BOOLEAN, data))
}

fun parseAsBool(string: String) : Boolean? =
  when (string.trim()) {
    "true", "True", "TRUE" -> true
    "false", "False", "FALSE" -> false
    else -> null
  }

fun tryParseNullOrEmpty(value: String): Item? {
  val trimmedValue = value.trim()
  return when (trimmedValue) {
    "@null" -> makeNull()
    "@empty" -> makeEmpty()
    else -> null
  }
}

fun makeNull() : Reference {
  return Reference()
}

fun makeEmpty() : BinaryPrimitive {
  return BinaryPrimitive(ResValue(ResValue.DataType.NULL, ResValue.NullFormat.EMPTY))
}

fun tryParseInt(value: String) : BinaryPrimitive? {
  val trimmedValue = value.trim()
  val resValue = stringToInt(trimmedValue)
  return if (resValue != null) BinaryPrimitive(resValue) else null
}

fun parseResourceId(value: String): Int? {
  val resValue = stringToInt(value)
  if (resValue != null &&
    resValue.dataType == ResValue.DataType.INT_HEX &&
    resValue.data.isValidDynamicId()) {
    return resValue.data
  }
  return null
}
