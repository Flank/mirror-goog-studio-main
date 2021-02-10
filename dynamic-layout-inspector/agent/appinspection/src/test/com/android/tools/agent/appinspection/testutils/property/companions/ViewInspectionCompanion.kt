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

import android.view.View
import android.view.inspector.InspectionCompanion
import android.view.inspector.PropertyMapper
import android.view.inspector.PropertyReader
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyMapper
import com.android.tools.agent.appinspection.testutils.property.EnumPropertyReader

class ViewInspectionCompanion : InspectionCompanion<View> {

    companion object {
        val NUM_PROPERTIES = Property.values().size
    }

    private val visibilityMapper: (Int) -> String? = { value ->
        when (value) {
            View.VISIBLE -> "visible"
            View.INVISIBLE -> "invisible"
            View.GONE -> "gone"
            else -> null
        }
    }

    private enum class Property {
        // TODO: Add way more properties
        VISIBILITY,
    }

    override fun mapProperties(mapper: PropertyMapper) {
        val enumMapper = EnumPropertyMapper<Property>(mapper)
        enumMapper.mapIntEnum(Property.VISIBILITY, visibilityMapper)
    }

    override fun readProperties(view: View, reader: PropertyReader) {
        val enumReader = EnumPropertyReader<Property>(reader)
        enumReader.readIntEnum(Property.VISIBILITY, view.visibility)
    }
}

