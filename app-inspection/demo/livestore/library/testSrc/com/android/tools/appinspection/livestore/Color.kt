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

package com.android.tools.appinspection.livestore

import com.android.tools.appinspection.livestore.protocol.IdeHint

private val HEX_REGEX = "[0-9a-f]"
val COLOR_REGEX =
    Regex("""#($HEX_REGEX$HEX_REGEX)($HEX_REGEX$HEX_REGEX)($HEX_REGEX$HEX_REGEX)""", RegexOption.IGNORE_CASE)

private fun Int.toHexString() = String.format("%02X", this)

/**
 * Dummy class that represents an RGB color that is shared across tests
 */
data class Color(val r: Int, val g: Int, val b: Int) {
    companion object {
        fun fromString(strValue: String): Color? {
            return COLOR_REGEX.matchEntire(strValue)
                ?.let { match ->
                    val r = match.groupValues[1].toInt(16)
                    val g = match.groupValues[2].toInt(16)
                    val b = match.groupValues[3].toInt(16)
                    Color(r, g, b)
                }
        }
    }

    override fun toString(): String {
        return "#${r.toHexString()}${g.toHexString()}${b.toHexString()}"
    }
}

fun LiveStore.addColor(name: String, value: Color): ValueEntry<Color> {
    return addCustom(name, value, { Color.fromString(it) }, { it.toString() }, IdeHint.COLOR)
}
