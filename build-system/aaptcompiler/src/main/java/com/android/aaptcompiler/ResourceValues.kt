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

import com.android.aapt.Resources
import com.android.aaptcompiler.android.ResValue
import com.android.aaptcompiler.android.deviceToHost
import com.android.aaptcompiler.android.hostToDevice
import java.io.File


open class Value {
  var source: Source = Source("")
  var comment = ""
  var weak = false
  var translatable = true
}

abstract class Item : Value() {
  abstract fun clone(newPool: StringPool): Item

  abstract fun flatten(): ResValue?
}

// an ID resource. Has no real value, just a place holder.
class Id: Item() {
  init {
    weak = true
  }

  override fun flatten(): ResValue? {
    return ResValue(ResValue.DataType.INT_BOOLEAN, 0.hostToDevice())
  }

  override fun clone(newPool: StringPool): Id {
    val newId = Id()
    newId.weak = weak
    newId.comment = comment
    newId.source = source
    return newId
  }
}

class Reference(var name: ResourceName = ResourceName("", AaptResourceType.RAW, "")): Item() {
  enum class Type {
    RESOURCE,
    ATTRIBUTE
  }

  var id : Int? = null
  var referenceType : Reference.Type = Reference.Type.RESOURCE
  var isPrivate = false
  var isDynamic = false

  override fun flatten(): ResValue {
    val resId = id ?: 0
    val dynamic = resId.isValidDynamicId() && isDynamic

    val dataType = when {
      referenceType == Reference.Type.RESOURCE ->
        if (dynamic) ResValue.DataType.DYNAMIC_REFERENCE else ResValue.DataType.REFERENCE
      else ->
        if (dynamic) ResValue.DataType.DYNAMIC_ATTRIBUTE else ResValue.DataType.ATTRIBUTE
    }

    return ResValue(dataType, resId.hostToDevice())
  }

  override fun equals(other: Any?): Boolean {
    if (other is Reference) {
      return referenceType == other.referenceType &&
        isPrivate == other.isPrivate &&
        id == other.id &&
        name == other.name
    }
    return false
  }

  override fun clone(newPool: StringPool): Reference {
    val newRef = Reference(this.name)
    newRef.id = id
    newRef.referenceType = referenceType
    newRef.isPrivate = isPrivate
    newRef.isDynamic = isDynamic
    newRef.comment = comment
    newRef.source = source
    return newRef
  }
}

class FileReference(val path: StringPool.Ref): Item() {
  // Handle to the file object from which this file can be read. This is only transient, and not
  // persisted in any format.
  var file: File? = null

  // FileType of the file pointed to by `file` This is used to know how to inflate the file, or
  // if to inflate at all (just copy)
  var type: ResourceFile.Type = ResourceFile.Type.Unknown

  override fun flatten(): ResValue? {
    return ResValue(ResValue.DataType.STRING, path.index().hostToDevice())
  }

  override fun clone(newPool: StringPool): FileReference {
    val newFileRef = FileReference(newPool.makeRef(path))
    newFileRef.file = file
    newFileRef.type = type
    newFileRef.comment = comment
    newFileRef.source = source
    return newFileRef
  }
}

class BinaryPrimitive(val resValue: ResValue): Item() {
  override fun equals(other: Any?): Boolean {
    if (other is BinaryPrimitive) {
      return resValue.dataType == other.resValue.dataType &&
        resValue.data == other.resValue.data
    }
    return false
  }

  override fun flatten(): ResValue? {
    return ResValue(resValue.dataType, resValue.data.hostToDevice())
  }

  override fun clone(newPool: StringPool): BinaryPrimitive {
    val newPrimitive = BinaryPrimitive(resValue)
    newPrimitive.comment = comment
    newPrimitive.source = source
    return newPrimitive
  }
}

class AttributeResource(var typeMask: Int = 0): Value() {
  class Symbol(val symbol: Reference, val value: Int)

  var minInt = Int.MIN_VALUE
  var maxInt = Int.MAX_VALUE
  val symbols = mutableListOf<Symbol>()

  fun matches(item: Item): Boolean {
    val value = item.flatten()!!
    val flattenedData = value.data.deviceToHost()

    // Always allow references
    val actualType = androidTypeToAttributeTypeMask(value.dataType)

    // Only one type must match between the actual and the expected.
    if ((actualType and (typeMask or Resources.Attribute.FormatFlags.REFERENCE_VALUE) == 0)) {
      // TODO(@daniellabar): diagnostics
      return false
    }

    // Enums and flags are encoded as integers, so check them first before doing any range checks.
    if ((typeMask and actualType and Resources.Attribute.FormatFlags.ENUM_VALUE) != 0) {

      for (symbol in symbols) {
        if (flattenedData == symbol.value) {
          return true
        }
      }

      // If the attribute accepts integers, we can't fail here.
      if ((typeMask and Resources.Attribute.FormatFlags.INTEGER_VALUE) == 0) {
        // TODO(@daniellabar): diagnostics
        return false
      }
    }

    if ((typeMask and actualType and Resources.Attribute.FormatFlags.FLAGS_VALUE) != 0) {

      var allFlags = 0
      for (symbol in symbols) {
        allFlags = allFlags or symbol.value
      }

      // Check if the flattened data is covered by the flag bit mask.
      if ((allFlags and flattenedData) == flattenedData) {
        return true
      }

      // If the attribute accepts integers, we can't fail here.
      if ((typeMask and Resources.Attribute.FormatFlags.INTEGER_VALUE) == 0) {
        // TODO(@daniellabar): diagnostics
        return false
      }
    }

    // If the value is an integer, we can't out of range.
    return true
  }

  fun isCompatibleWith(other: AttributeResource): Boolean {
    // if the high bits are set on any of these attribute type masks, then they are incompatible.
    // We don't check that flags and enums are identical.
    if ((typeMask and Resources.Attribute.FormatFlags.ANY_VALUE.inv()) != 0 ||
      (other.typeMask and Resources.Attribute.FormatFlags.ANY_VALUE.inv()) != 0) {
      return false
    }

    // Every attribute accepts a reference.
    val thisTypeMask = typeMask or Resources.Attribute.FormatFlags.REFERENCE_VALUE
    val otherTypeMask = other.typeMask or Resources.Attribute.FormatFlags.REFERENCE_VALUE

    return thisTypeMask == otherTypeMask
  }
}

data class UntranslatableSection(var startIndex: Int, var endIndex: Int = startIndex) {
  fun shift(offset : Int): UntranslatableSection {
    return UntranslatableSection(startIndex + offset, endIndex + offset)
  }
}
