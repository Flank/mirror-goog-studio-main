/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.projectmodel

import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Prints the properties of the given Kotlin object. Any optional parameters that are equal to
 * the default values will be omitted. All non-optional properties included in [defaultValues]
 * will not be used, since non-optional properties are always printed. For this reason, defaultValues
 * should be filled in with the simplest-possible values for any non-optional attributes.
 */
internal fun <T : Any> printProperties(toPrint: T, defaultValues: T): String {
    val clazz = toPrint.javaClass.kotlin
    val optionalParameters = clazz.primaryConstructor?.parameters.orEmpty().filter { it.isOptional }
        .mapNotNull { it.name }.toSet()
    val propertyDescriptions = ArrayList<String>()
    for (prop in clazz.memberProperties) {
        val actualValue = prop.get(toPrint)
        if (!optionalParameters.contains(prop.name) || (prop.get(defaultValues) != actualValue)) {
            propertyDescriptions.add("${prop.name}=$actualValue")
        }
    }
    return propertyDescriptions.joinToString(",", clazz.simpleName + "(", ")")
}