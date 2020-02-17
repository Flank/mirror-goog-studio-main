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

package com.android.tools.appinspection.livestore.inspector

import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import com.android.tools.appinspection.livestore.library.LiveStore
import com.android.tools.appinspection.livestore.protocol.Command
import com.android.tools.appinspection.livestore.protocol.Event
import com.google.gson.Gson

/**
 * The inspector that will run on device.
 *
 * It is responsible for two things:
 * 1) Receive UPDATE_VALUE command from studio, and modify store in memory.
 * 2) Send store updates to studio whenever there is an update.
 */
class LiveStoreInspector(connection: Connection, environment: InspectorEnvironment) : Inspector(connection) {

    private val gson = Gson()

    private val instance: LiveStore = environment.findInstances(LiveStore::class.java).let { instances ->
        assert(instances.size == 1) { "For now, we only support a singleton LiveStore instance" }
        instances[0]
    }

    init {
        // On initialization, send everything in the store to studio.
        instance.forEach { sendUpdate(it.key, it.value) }

        // Hook onto calls to [set]. Send value to studio.
        environment.registerEntryHook(
            LiveStore::class.java, "set(Ljava/lang/String;Ljava/lang/String)V"
        ) { _, args ->
            sendUpdate(args[0] as String, args[1] as String)
        }
    }

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val json = String(data, Charsets.UTF_8)
        val command = gson.fromJson(json, Command::class.java)

        if (command.commandType == Command.CommandType.UPDATE_VALUE) {
            command.updateValue?.let { updateValue(it.key, it.value) }
            replyUpdateValueCommand(callback)
        }
    }

    private fun updateValue(key: String, value: String) {
        instance[key] = value
    }

    private fun replyUpdateValueCommand(callback: CommandCallback) {
        callback.reply(gson.toJson(Command.UpdateValueResponse()).toByteArray())
    }

    private fun sendUpdate(key: String, value: String) {
        connection.sendEvent(
            gson.toJson(Event(Event.ValueUpdated(key, value))).toByteArray(Charsets.UTF_8)
        )
    }
}