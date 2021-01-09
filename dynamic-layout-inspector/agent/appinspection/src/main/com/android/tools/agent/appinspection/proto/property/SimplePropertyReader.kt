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

import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Interpolator
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.Animation
import android.view.inspector.PropertyReader
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.proto.createResource
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Property
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource

/**
 * A [PropertyReader] implementation which modifies a list of passed in
 * [PropertyBuilder]s as it reads in properties.
 */
class SimplePropertyReader(
    private val stringTable: StringTable,
    private val view: View,
    private val properties: List<PropertyBuilder>,
    private val category: PropertyCategory
) : PropertyReader {

    enum class PropertyCategory {
        VIEW,
        LAYOUT_PARAMS
    }

    private val resourceMap: Map<Int, Int> = view.attributeSourceResourceMap

    override fun readBoolean(id: Int, b: Boolean) {
        readAny(id, b)
    }

    override fun readByte(id: Int, b: Byte) {
        readAny(id, b.toInt())
    }

    override fun readChar(id: Int, c: Char) {
        readAny(id, c.toInt())
    }

    override fun readDouble(id: Int, d: Double) {
        readAny(id, d)
    }

    override fun readFloat(id: Int, f: Float) {
        readAny(id, f)
    }

    override fun readInt(id: Int, i: Int) {
        readAny(id, i)
    }

    override fun readLong(id: Int, l: Long) {
        readAny(id, l)
    }

    override fun readShort(id: Int, s: Short) {
        readAny(id, s.toInt())
    }

    override fun readObject(id: Int, o: Any?) {
        when (o) {
            is String -> {
                readAny(id, o.intern())
                properties[id].setType(Property.Type.STRING)
            }
            is ColorStateList -> {
                readAny(id, o.getColorForState(view.drawableState, o.defaultColor))
                properties[id].setType(Property.Type.COLOR)
            }
            is ColorDrawable -> {
                readAny(id, o.color)
                properties[id].setType(Property.Type.COLOR)
            }
            is Drawable -> {
                readAny(id, o)
                properties[id].setType(Property.Type.DRAWABLE)
            }
            is Animation -> {
                readAny(id, o)
                properties[id].setType(Property.Type.ANIM)
            }
            is StateListAnimator -> {
                readAny(id, o)
                properties[id].setType(Property.Type.ANIMATOR)
            }
            is Interpolator -> {
                readAny(id, o)
                properties[id].setType(Property.Type.INTERPOLATOR)
            }
        }
    }

    override fun readColor(id: Int, color: Int) {
        readAny(id, color)
    }

    override fun readColor(id: Int, color: Long) {
        readAny(id, color)
    }

    override fun readColor(id: Int, color: Color?) {
        readAny(id, color)
    }

    override fun readGravity(id: Int, value: Int) {
        readIntFlag(id, value)
    }

    override fun readIntEnum(id: Int, value: Int) {
        val property = properties[id]
        val mapping = property.enumMapping
        if (mapping != null) {
            val mappedValue = mapping.apply(value)
            if (mappedValue != null) {
                readAny(id, mappedValue)
                return
            }
        }
        readAny(id, value)
        properties[id].setType(Property.Type.INT32)
    }

    override fun readIntFlag(id: Int, value: Int) {
        val property = properties[id]
        val mapping = property.flagMapping
        if (mapping != null) {
            readAny(id, mapping.apply(value))
        }
    }

    override fun readResourceId(id: Int, value: Int) {
        readAny(id, view.createResource(stringTable, value))
    }

    private fun readAny(id: Int, value: Any?) {
        val property = properties[id]
        val metadata = property.metadata
        property.setValue(value)
        property.setIsLayout(category == PropertyCategory.LAYOUT_PARAMS)
        if (category == PropertyCategory.VIEW) {
            property.setSource(getResourceValueOfAttribute(metadata.attributeId))
            for (resourceId in view.getAttributeResolutionStack(metadata.attributeId)) {
                view.createResource(stringTable, resourceId)?.let { resource ->
                    property.addResolutionResource(resource)
                }
            }
        }
    }

    private fun getResourceValueOfAttribute(attributeId: Int): Resource? {
        return resourceMap[attributeId]?.let { resourceId ->
            view.createResource(stringTable, resourceId)
        }
    }
}
