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

import android.view.View
import android.view.ViewGroup
import android.view.inspector.InspectionCompanion
import android.view.inspector.PropertyReader
import android.view.inspector.StaticInspectionCompanionProvider
import java.util.ArrayList

/**
 * A class which maps class types to data that will be inspected from them.
 *
 * By calling [typeOf], properties will be cached for that type when encountered for the
 * first time.
 *
 * @param T The parent type of classes to cache property types against. This type MUST either match
 *   OR be a subclass of [fqcn].
 * @param fqcn The fully qualified name of the root class that all `T` types should be instances of.
 */
@Suppress("UNCHECKED_CAST") // Companion casts are safe
class PropertyCache<T: Any>(private val fqcn: String) {

    class Data<T: Any>(
        val properties: List<PropertyBuilder>,
        val companions: List<InspectionCompanion<T>>
    ) {
        fun readProperties(inspectable: T, propertyReader: PropertyReader) {
            for (companion in companions) {
                companion.readProperties(inspectable, propertyReader)
            }
        }

    }

    companion object {
        fun createViewCache() = PropertyCache<View>("android.view.View")
        fun createLayoutParamsCache() = PropertyCache<ViewGroup.LayoutParams>("android.view.ViewGroup.LayoutParams")
    }

    private val provider = StaticInspectionCompanionProvider()
    private val typeMap = mutableMapOf<Class<out T>, Data<T>>()

    fun typeOf(inspectable: T): Data<T> {
        return typeOfImpl(inspectable::class.java)
    }

    fun typeOf(inspectable: Class<out T>): Data<T> {
        return typeOfImpl(inspectable)
    }

    private fun typeOfImpl(inspectable: Class<out T>): Data<T> {
        var type = typeMap[inspectable]
        if (type != null) {
            return type
        }

        val companion = loadInspectionCompanion(inspectable)
        val superTypeData =
            if (inspectable.canonicalName != fqcn) {
                typeOfImpl(inspectable.superclass as Class<out T>)
            } else null

        val companions = mutableListOf<InspectionCompanion<T>>()
        if (superTypeData != null) {
            companions.addAll(superTypeData.companions)
        }
        if (companion != null) {
            companions.add(companion)
        }

        var properties: MutableList<PropertyBuilder> = ArrayList()
        if (superTypeData != null) {
            properties.addAll(superTypeData.properties)
        }
        if (companion != null) {
            val mapper = PropertyTypeMapper(properties)
            companion.mapProperties(mapper)
            properties = mapper.properties
        }

        type = Data(properties, companions)
        typeMap[inspectable] = type

        return type
    }

    private fun loadInspectionCompanion(javaClass: Class<out T>): InspectionCompanion<T>? {
        return provider.provide(javaClass) as? InspectionCompanion<T>
    }
}
