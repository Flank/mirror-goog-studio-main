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
    fun write(registrar: SnapshotItemRegistrarImpl): String {
        precomputeSizeOfRegistrars(registrar)
        val sb = StringBuilder()
        registrar.write(sb, 0)
        return sb.toString()
    }

    private fun write(
        item: SnapshotItem,
        sb: StringBuilder,
        indent: Int,
        keySpacing: Int,
    ) {
        when (item) {
            is KeyValueItem -> item.write(sb, indent, keySpacing)
            is ValueOnlyItem -> item.write(sb, indent)
            is SnapshotItemRegistrarImpl -> item.write(sb, indent)
            else -> error("Unsupported type of DumpedItem")
        }
    }

    private fun KeyValueItem.write(sb: StringBuilder, indent: Int, keySpacing: Int) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append("- ").append(name)

        if (keySpacing > 0) {
            val spaceLen = keySpacing - name.length
            for (i in 0..spaceLen) sb.append(' ')

        } else {
            sb.append(' ')
        }

        sb.append(separator).append(' ').append(value).append("\n")
    }

    private fun ValueOnlyItem.write(sb: StringBuilder, indent: Int) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        sb.append("* ").append(value).append("\n")
    }

    private fun SnapshotItemRegistrarImpl.write(sb: StringBuilder, indent: Int) {
        val keySpacing = computeKeySpacing()

        val withFooter = sizeOf(this) >= MAX_STRUCT_SIZE

        writeHeader(name, sb, indent, withFooter)

        for (item in items) {
            this@SnapshotItemWriter.write(item, sb, indent + INDENT_STEP2, keySpacing)
        }

        if (withFooter) {
            writeFooter(name, sb, indent)
        }
    }

    private fun writeHeader(name: String, sb: StringBuilder, indent: Int, withFooter: Boolean) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')

        val prefix = if (withFooter) "> " else "- "

        sb.append(prefix).append(name).append(":\n")
    }

    private fun writeFooter(name: String, sb: StringBuilder, indent: Int) {
        if (indent > 0) for (i in 0..indent) sb.append(' ')
        sb.append("< ").append(name).append("\n")
    }

    private fun precomputeSizeOfRegistrars(rootRegistrar: SnapshotItemRegistrarImpl) {
        rootRegistrar.computeSize()
    }

    private fun SnapshotItem.computeSize(): Int {
        return when (this) {
            is KeyValueItem -> 1
            is ValueOnlyItem -> 1
            is SnapshotItemRegistrarImpl -> {
                var cachedSize = sizeOfHolderMap[this]

                if (cachedSize == null) {
                    val itemSize = items.sumBy { it.computeSize() }

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

    private val sizeOfHolderMap = IdentityHashMap<SnapshotItemRegistrarImpl, Int>()

    private fun sizeOf(holder: SnapshotItemRegistrarImpl): Int = sizeOfHolderMap[holder]
            ?: throw RuntimeException("Failed to find size for holder ${holder.name}")
}

const val INDENT_STEP2 = 3
const val MAX_STRUCT_SIZE = 10
