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

import com.android.resources.ResourceType

class ResourceFile {
    lateinit var name :ResourceName
    lateinit var configuration: ConfigDescription
    lateinit var source: Source
    var type: Type = Type.Unknown
    var exportedSymbols = ArrayList<SourcedResourceName>()

    enum class Type {
        Unknown,
        Png,
        BinaryXml,
        ProtoXml
    }

    fun copy(): ResourceFile {
        val copy = ResourceFile()
        copy.name = name
        copy.configuration = configuration
        copy.source = source
        copy.type = type
        return copy
    }
}

data class ResourceName(
    val pck: String?,
    val type: ResourceType = ResourceType.RAW,
    val entry: String? = null): Comparable<ResourceName> {

  override fun compareTo(other: ResourceName): Int {
    val pckCompare = when {
      pck === other.pck -> 0
      pck == null -> -1
      other.pck == null -> 1
      else -> pck.compareTo(other.pck)
    }
    if (pckCompare != 0) {
      return pckCompare
    }

    val typeCompare = type.compareTo(other.type)
    if (typeCompare != 0) {
      return typeCompare
    }

    val entryCompare = when {
      entry === other.pck -> 0
      entry == null -> -1
      other.entry == null -> 1
      else -> entry.compareTo(other.entry)
    }
    return entryCompare
  }

  override fun toString() : String {
        val maybePck = if (pck != null && pck.isNotEmpty()) "$pck:" else ""
        return "$maybePck${type.getName()}/$entry"
    }
}

class SourcedResourceName(val name: ResourceName, val line: Int)

fun Int.isValidId(): Boolean = (this and 0xff000000.toInt()) != 0 && this.isValidDynamicId()

fun Int.isValidDynamicId(): Boolean = (this and 0x00ff0000) != 0

fun Int.getPackageId(): Byte = (this shr 24).toByte()

fun Int.getTypeId(): Byte = (this shr 16).toByte()

fun Int.getEntryId(): Short = this.toShort()

fun resourceIdFromParts(packageId: Byte, typeId: Byte, entryId: Short): Int =
  (packageId.toInt() shl 24) or (typeId.toInt() shl 16) or (entryId.toInt())
