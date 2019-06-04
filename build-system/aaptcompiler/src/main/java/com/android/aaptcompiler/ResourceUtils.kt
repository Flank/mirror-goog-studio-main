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
import com.android.aaptcompiler.android.parseHex
import com.android.aaptcompiler.android.stringToFloat
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

fun tryParseFloat(value: String) : BinaryPrimitive? {
  val floatResource = stringToFloat(value)
  floatResource ?: return null

  return BinaryPrimitive(floatResource)
}

fun tryParseColor(value: String): BinaryPrimitive? {
  val colorStr = value.trim()
  if (colorStr.isEmpty() || colorStr[0] != '#') {
    return null
  }

  val dataType: ResValue.DataType
  var data = 0
  var error = false

  when (colorStr.length) {
    4 -> {
      dataType = ResValue.DataType.INT_COLOR_RGB4
      for (i in 1..3) {
        val hexValue = parseHex(colorStr.codePointAt(i))
        if (hexValue == -1) {
          error = true
          break
        }
        data = (data shl 8) or (hexValue + (hexValue shl 4))
      }
      data = data or 0xff000000.toInt()
    }
    5 -> {
      dataType = ResValue.DataType.INT_COLOR_ARGB4
      for (i in 1..4) {
        val hexValue = parseHex(colorStr.codePointAt(i))
        if (hexValue == -1) {
          error = true
          break
        }
        data = (data shl 8) or (hexValue + (hexValue shl 4))
      }
    }
    7 -> {
      dataType = ResValue.DataType.INT_COLOR_RGB8
      for (i in 1..6) {
        val hexValue = parseHex(colorStr.codePointAt(i))
        if (hexValue == -1) {
          error = true
          break
        }
        data = (data shl 4) or hexValue
      }
      data = data or 0xff000000.toInt()
    }
    9 -> {
      dataType = ResValue.DataType.INT_COLOR_ARGB8
      for (i in 1..8) {
        val hexValue = parseHex(colorStr.codePointAt(i))
        if (hexValue == -1) {
          error = true
          break
        }
        data = (data shl 4) or hexValue
      }
    }
    else -> return null
  }
  return if (error) BinaryPrimitive(ResValue()) else BinaryPrimitive(ResValue(dataType, data))
}

