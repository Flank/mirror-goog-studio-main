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
import com.android.aaptcompiler.android.hostToDevice
import com.android.resources.ResourceType

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

class Reference(var name: ResourceName = ResourceName("", ResourceType.RAW, "")): Item() {
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

data class UntranslatableSection(var startIndex: Int, var endIndex: Int = startIndex) {
  fun shift(offset : Int): UntranslatableSection {
    return UntranslatableSection(startIndex + offset, endIndex + offset)
  }
}
