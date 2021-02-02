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

import android.view.inspector.PropertyReader

/**
 * Class which wraps a [PropertyReader] but adds support for using enums instead of managing your
 * own integer index list.
 */
class EnumPropertyReader<E : Enum<E>>(
    private val delegateReader: PropertyReader,
    private val indexOffset: Int = 0
) {
    fun readBoolean(e: E, value: Boolean) {
        delegateReader.readBoolean(indexOf(e), value)
    }

    fun readByte(e: E, value: Byte) {
        delegateReader.readByte(indexOf(e), value)
    }

    fun readChar(e: E, value: Char) {
        delegateReader.readChar(indexOf(e), value)
    }

    fun readDouble(e: E, value: Double) {
        delegateReader.readDouble(indexOf(e), value)
    }

    fun readFloat(e: E, value: Float) {
        delegateReader.readFloat(indexOf(e), value)
    }

    fun readInt(e: E, value: Int) {
        delegateReader.readInt(indexOf(e), value)
    }

    fun readLong(e: E, value: Long) {
        delegateReader.readLong(indexOf(e), value)
    }

    fun readShort(e: E, value: Short) {
        delegateReader.readShort(indexOf(e), value)
    }

    fun readObject(e: E, value: Any?) {
        delegateReader.readObject(indexOf(e), value)
    }

    fun readColor(e: E, value: Int) {
        delegateReader.readColor(indexOf(e), value)
    }

    fun readGravity(e: E, value: Int) {
        delegateReader.readGravity(indexOf(e), value)
    }

    fun readIntEnum(e: E, value: Int) {
        delegateReader.readIntEnum(indexOf(e), value)
    }

    fun readIntFlag(e: E, value: Int) {
        delegateReader.readIntFlag(indexOf(e), value)
    }

    fun readResourceId(e: E, value: Int) {
        delegateReader.readResourceId(indexOf(e), value)
    }

    private fun indexOf(e: E): Int {
        return indexOffset + e.ordinal
    }
}
