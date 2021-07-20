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
package com.android.tools.agent.appinspection.testutils.property

import android.view.inspector.PropertyMapper
import com.google.common.base.CaseFormat
import java.util.function.IntFunction

const val ATTR_OFFSET = 7001

/**
 * Class which wraps a [PropertyMapper] but adds support for using enums instead of managing your
 * own integer index list.
 */
class EnumPropertyMapper<E : Enum<E>>(
    private val delegateMapper: PropertyMapper,
    private val indexOffset: Int = 0,
    private val namePrefix: String = ""
) {
    fun mapBoolean(e: E) {
        delegateMapper.mapBoolean(nameOf(e), attributeIdOf(e))
    }

    fun mapByte(e: E) {
        delegateMapper.mapByte(nameOf(e), attributeIdOf(e))
    }

    fun mapChar(e: E) {
        delegateMapper.mapChar(nameOf(e), attributeIdOf(e))
    }

    fun mapDouble(e: E) {
        delegateMapper.mapDouble(nameOf(e), attributeIdOf(e))
    }

    fun mapFloat(e: E) {
        delegateMapper.mapFloat(nameOf(e), attributeIdOf(e))
    }

    fun mapInt(e: E) {
        delegateMapper.mapInt(nameOf(e), attributeIdOf(e))
    }

    fun mapLong(e: E) {
        delegateMapper.mapLong(nameOf(e), attributeIdOf(e))
    }

    fun mapShort(e: E) {
        delegateMapper.mapShort(nameOf(e), attributeIdOf(e))
    }

    fun mapObject(e: E) {
        delegateMapper.mapObject(nameOf(e), attributeIdOf(e))
    }

    fun mapColor(e: E) {
        delegateMapper.mapColor(nameOf(e), attributeIdOf(e))
    }

    fun mapGravity(e: E) {
        delegateMapper.mapGravity(nameOf(e), attributeIdOf(e))
    }

    fun mapIntEnum(e: E, mapping: IntFunction<String?>) {
        delegateMapper.mapIntEnum(nameOf(e), attributeIdOf(e), mapping)
    }

    fun mapResourceId(e: E) {
        delegateMapper.mapResourceId(nameOf(e), attributeIdOf(e))
    }

    fun mapIntFlag(e: E, mapping: IntFunction<Set<String?>>) {
        delegateMapper.mapIntFlag(nameOf(e), attributeIdOf(e), mapping)
    }

    /**
     * Takes an enum instance and converts the uppercase name to an Android style attribute name.
     *
     * Example: STATE_LIST_ANIMATOR -> stateListAnimator
     */
    private fun nameOf(e: E): String {
        return namePrefix + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, e.name)
    }

    private fun attributeIdOf(e: E): Int {
        return ATTR_OFFSET + indexOffset + e.ordinal
    }
}
