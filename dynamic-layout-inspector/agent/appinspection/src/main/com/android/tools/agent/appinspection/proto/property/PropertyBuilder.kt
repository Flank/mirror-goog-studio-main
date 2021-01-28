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
package com.android.tools.agent.appinspection.proto.property

import com.android.tools.agent.appinspection.proto.StringTable
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.FlagValue
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource
import java.util.function.IntFunction

/**
 * A convenience class for handling the building of a [Property], wrapping a
 * [Property.Builder] but with some extra logic.
 */
class PropertyBuilder(val metadata: PropertyMetadata) {
    var enumMapping: IntFunction<String>? = null
    var flagMapping: IntFunction<Set<String>>? = null

    private val property = Property.newBuilder()
    private var value: Any? = null

    init {
        setType(metadata.type)
    }

    fun setType(type: Property.Type) {
        property.type = type
    }

    fun setValue(value: Any?) {
        // Don't set this on the property until the last possible moment,
        // as it may depend on `type` being set first.
        this.value = value
    }

    fun setSource(source: Resource?) {
        property.source = source ?: Resource.getDefaultInstance()
    }

    fun addResolutionResource(resource: Resource) {
        property.addResolutionStack(resource)
    }

    fun setIsLayout(isLayout: Boolean) {
        property.isLayout = isLayout
    }

    fun build(stringTable: StringTable): Property? {
        return value?.let { value ->
            property.name = stringTable.put(metadata.name)
            property.setValue(stringTable, value)
            return property.build()
        }
    }

    private fun Set<String>.toFlagValue(stringTable: StringTable): FlagValue {
        val flags = this
        return FlagValue.newBuilder().apply {
            for (flag in flags) {
                addFlag(stringTable.put(flag))
            }
        }.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Property.Builder.setValue(stringTable: StringTable, value: Any) {
        when (type) {
            Property.Type.STRING, Property.Type.INT_ENUM -> {
                int32Value = stringTable.put(value as String)
            }
            Property.Type.INT32,
            Property.Type.INT16,
            Property.Type.BYTE,
            Property.Type.CHAR,
            Property.Type.COLOR,
            Property.Type.DIMENSION -> {
                int32Value = value as Int
            }
            Property.Type.BOOLEAN -> {
                int32Value = if (value == true) 1 else 0
            }
            Property.Type.GRAVITY, Property.Type.INT_FLAG -> {
                flagValue = (value as Set<String>).toFlagValue(stringTable)
            }
            Property.Type.INT64 -> {
                int64Value = (value as Long)
            }
            Property.Type.DOUBLE -> {
                doubleValue = (value as Double)
            }
            Property.Type.FLOAT -> {
                floatValue = value as Float
            }
            Property.Type.RESOURCE -> {
                resourceValue = value as Resource
            }
            Property.Type.DRAWABLE,
            Property.Type.ANIM,
            Property.Type.ANIMATOR,
            Property.Type.INTERPOLATOR -> {
                int32Value = stringTable.put(value.javaClass.name)
            }
            else -> error("Unhandled property type: $type")
        }
    }
}
