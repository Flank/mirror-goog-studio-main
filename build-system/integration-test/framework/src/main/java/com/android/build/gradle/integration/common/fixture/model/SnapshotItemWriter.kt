/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.model

import java.util.IdentityHashMap

/**
 * Class responsible for writing a dumped model to a string
 */
class SnapshotItemWriter {
    companion object {
        const val NULL_STRING = "(null)"
    }

    /**
     * Writes the registrar into a string and returns it
     */
    fun write(registrar: SnapshotContainer): String {
        precomputeSizeOfRegistrars(registrar)
        val sb = StringBuilder()
        registrar.write(sb)
        return sb.toString()
    }

    private fun write(
        item: SnapshotItem,
        sb: StringBuilder,
        indent: String,
        keySpacing: Int,
    ) {
        when (item) {
            is KeyValueItem -> item.write(sb, indent, keySpacing)
            is ValueOnlyItem -> item.write(sb, indent)
            is SnapshotContainer -> item.write(sb, indent, keySpacing)
            else -> error("Unsupported type of DumpedItem")
        }
    }

    private fun KeyValueItem.write(sb: StringBuilder, indent: String, keySpacing: Int) {
        sb
            .append(indent)
            .append("- ")
            .appendNameAndSpacing(name, keySpacing)
            .append(separator)
            .append(' ')
            .append(value)
            .append("\n")
    }

    private fun StringBuilder.appendNameAndSpacing(name: String, keySpacing: Int): StringBuilder {
        append(name)

        if (keySpacing > 0) {
            val spaceLen = keySpacing - name.length
            append(" ".repeat(spaceLen))
        } else {
            append(' ')
        }

        return this
    }

    private fun ValueOnlyItem.write(sb: StringBuilder, indent: String) {
        sb.append(indent).append("* ").append(value).append("\n")
    }

    private fun SnapshotContainer.write(
        sb: StringBuilder,
        indent: String = "",
        parentKeySpacing: Int = 0
    ) {
        val keySpacing = computeKeySpacing()

        val withFooter = sizeOf(this) >= MAX_STRUCT_SIZE

        writeHeader(sb, indent, parentKeySpacing, withFooter)

        val newIndent = indent + " ".repeat(INDENT_STEP2)
        items?.forEach { item ->
            this@SnapshotItemWriter.write(item, sb, newIndent, keySpacing)
        }

        if (withFooter) {
            writeFooter(name, sb, indent)
        }
    }

    private fun SnapshotContainer.writeHeader(
        sb: StringBuilder,
        indent: String,
        keySpacing: Int,
        withFooter: Boolean
    ) {
        sb.append(indent).append(if (withFooter) "> " else "- ")
        if (contentType.isList) {
            val items = this.items
            when {
                items == null -> sb.appendNameAndSpacing(name, keySpacing).append("= (null)\n")
                items.isEmpty() -> sb.appendNameAndSpacing(name, keySpacing).append("= []\n")
                else -> sb.append(name).append(":\n")
            }
        } else {
            if (items == null) {
                sb.appendNameAndSpacing(name, keySpacing).append("= (null)\n")
            } else {
                sb.append(name).append(":\n")
            }
        }
    }

    private fun writeFooter(name: String, sb: StringBuilder, indent: String) {
        sb.append(indent).append("< ").append(name).append("\n")
    }

    private fun precomputeSizeOfRegistrars(rootRegistrar: SnapshotContainer) {
        rootRegistrar.computeSize()
    }

    private fun SnapshotItem.computeSize(): Int {
        return when (this) {
            is KeyValueItem -> 1
            is ValueOnlyItem -> 1
            is SnapshotContainer -> {
                var cachedSize = sizeOfHolderMap[this]

                if (cachedSize == null) {
                    val itemSize = items?.sumBy { it.computeSize() } ?: 0

                    // include header/footer as needed
                    cachedSize = if (itemSize >= MAX_STRUCT_SIZE) {
                        // header + footer
                        itemSize + 2
                    } else {
                        // header only
                        itemSize + 1
                    }

                    sizeOfHolderMap[this] = cachedSize
                }

                cachedSize
            }
            else -> error("Unsupported type of DumpedItem")
        }
    }

    private val sizeOfHolderMap = IdentityHashMap<SnapshotContainer, Int>()

    private fun sizeOf(container: SnapshotContainer): Int = sizeOfHolderMap[container]
            ?: throw RuntimeException("Failed to find size for holder ${container.name}")
}

const val INDENT_STEP2 = 3
const val MAX_STRUCT_SIZE = 10
