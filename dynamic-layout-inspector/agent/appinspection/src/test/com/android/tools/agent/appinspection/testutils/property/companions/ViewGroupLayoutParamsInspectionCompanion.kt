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

package com.android.tools.agent.appinspection.testutils.property.companions

import android.view.ViewGroup
import android.view.inspector.InspectionCompanion
import android.view.inspector.PropertyMapper
import android.view.inspector.PropertyReader
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyMapper
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyReader

class ViewGroupLayoutParamsInspectionCompanion : InspectionCompanion<ViewGroup.LayoutParams> {

    private val sizeMapping: (Int) -> String? = { value: Int ->
        when (value) {
            ViewGroup.LayoutParams.MATCH_PARENT -> "match_parent"
            ViewGroup.LayoutParams.WRAP_CONTENT -> "wrap_content"
            else -> null
        }
    }

    private enum class Property {
        WIDTH,
        HEIGHT
    }

    override fun mapProperties(propertyMapper: PropertyMapper) {
        val mapper = EnumPropertyMapper<Property>(propertyMapper, namePrefix = "layout_")
        mapper.mapIntEnum(Property.WIDTH, sizeMapping)
        mapper.mapIntEnum(Property.HEIGHT, sizeMapping)
    }

    override fun readProperties(
        layoutParams: ViewGroup.LayoutParams,
        propertyReader: PropertyReader
    ) {
        val reader = EnumPropertyReader<Property>(propertyReader)
        reader.readIntEnum(Property.WIDTH, layoutParams.width)
        reader.readIntEnum(Property.HEIGHT, layoutParams.height)
    }
}

