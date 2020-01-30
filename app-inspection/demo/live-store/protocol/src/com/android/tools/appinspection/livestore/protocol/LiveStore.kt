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

class Command {
    enum class CommandType {
        UPDATE_VALUE_COMMAND
    }

    data class UpdateValueCommand(val key: String, val value: String)
    class UpdateValueResponse

    @field:SerializedName("commandType")
    val commandType: CommandType? = null

    // set only if commandType == UPDATE_VALUE_COMMAND
    @field:SerializedName("updateValueCommand")
    val updateValueCommand: UpdateValueCommand? = null
}

class Event {
    enum class EventType {
        VALUE_UPDATED_EVENT
    }

    data class ValueUpdatedEvent(val key: String, val value: String)

    @field:SerializedName("eventType")
    val eventType: EventType? = null

    // set only if commandType == UPDATE_VALUE_COMMAND
    @field:SerializedName("valueUpdatedEvent")
    val valueUpdatedEvent: ValueUpdatedEvent? = null
}