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

package com.android.tools.agent.layoutinspector.testing

import androidx.compose.SlotTable
import androidx.ui.tooling.CallGroup
import androidx.ui.tooling.Group
import androidx.ui.tooling.tables
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxBounds

/**
 * Reader for a CSV file with a SlotTree
 *
 * Use this class to read a CSV files that emulates the SlotTree created with the tooling support
 * found in Compose. The result is loaded into the global variable: tables in Inspectable.kt.
 * The input format is the same as the output generated by ComposeTree while displaying a composable
 * in the layout inspector.
 */
class SlotTableReader : CsvReader(6) {
    private val slots = mutableListOf<Group>()

    override fun resetState() {
        slots.clear()
    }

    override fun storeState() {
        val slotTable = SlotTable(arrayOf(slots[0]))
        tables.clear()
        tables.add(slotTable)
    }

    override fun parseColumns(columns: List<String>) {
        val indent = parseIndent(columns[0])
        val position = parsePosition(columns[1])
        val left = parseInt(columns[2])
        val right = parseInt(columns[3])
        val top = parseInt(columns[4])
        val bottom = parseInt(columns[5])
        val bounds = toBounds(left, right, top, bottom)
        addNode(indent, position, bounds)
    }

    private fun parsePosition(position: String): String? {
        if (position == "null") {
            return null
        }
        return parseQuotedString(position)
    }

    private fun toBounds(left: Int, right: Int, top: Int, bottom: Int): IntPxBounds =
        IntPxBounds(IntPx(left), IntPx(top), IntPx(right), IntPx(bottom))

    private fun addNode(indent: Int, position: String?, bounds: IntPxBounds) {
        val node = CallGroup(position, bounds, emptyList(), mutableListOf())
        if (indent > slots.size) {
            error("Cannot parse line $lineNumber: The indent \"$indent\" is a jump up")
        }
        if (indent < slots.size) {
            slots.subList(indent, slots.size).clear()
        }
        (slots.lastOrNull()?.children as? MutableList)?.add(node)
        slots.add(node)
    }
}
