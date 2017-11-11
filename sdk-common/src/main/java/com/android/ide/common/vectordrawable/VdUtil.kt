/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("VdUtil")
package com.android.ide.common.vectordrawable

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Returns a [DecimalFormat] of sufficient precision to use for formatting coordinate
 * values given the maximum viewport dimension.
 */
fun getCoordinateFormat(maxViewportSize: Float): DecimalFormat {
  val exponent = Math.floor(Math.log10(maxViewportSize.toDouble())).toInt()
  var fractionalDigits = 5 - exponent
  val formatBuilder = StringBuilder("#")
  if (fractionalDigits > 0) {
    // Build a string with decimal places for "#.##...", and cap at 6 digits.
    if (fractionalDigits > 6) {
      fractionalDigits = 6
    }
    formatBuilder.append('.')
    for (i in 0 until fractionalDigits) {
      formatBuilder.append('#')
    }
  }
  val fractionSeparator = DecimalFormatSymbols()
  fractionSeparator.decimalSeparator = '.'
  val format = DecimalFormat(formatBuilder.toString(), fractionSeparator)
  format.roundingMode = RoundingMode.HALF_UP
  return format
}

