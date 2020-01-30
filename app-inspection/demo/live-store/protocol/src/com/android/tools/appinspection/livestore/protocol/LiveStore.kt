/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.appinspection.livestore.protocol

import com.google.gson.annotations.SerializedName

class Command private constructor(
    @field:SerializedName("commandType") val commandType: CommandType,
    // set only if commandType == UPDATE_VALUE_COMMAND
    @field:SerializedName("updateValue") val updateValue: UpdateValue? = null
) {
    constructor(updateValue: UpdateValue) : this(
        commandType = CommandType.UPDATE_VALUE,
        updateValue = updateValue
    )

    enum class CommandType {
        UPDATE_VALUE
    }

    data class UpdateValue(val key: String, val value: String)
    class UpdateValueResponse
}

class Event private constructor(
    @field:SerializedName("eventType") val eventType: EventType,
    // set only if commandType == UPDATE_VALUE_COMMAND
    @field:SerializedName("valueUpdated") val valueUpdated: ValueUpdated? = null
) {
    constructor(valueUpdated: ValueUpdated) : this(
        eventType = EventType.VALUE_UPDATED,
        valueUpdated = valueUpdated
    )

    enum class EventType {
        VALUE_UPDATED
    }

    data class ValueUpdated(val key: String, val value: String)
}