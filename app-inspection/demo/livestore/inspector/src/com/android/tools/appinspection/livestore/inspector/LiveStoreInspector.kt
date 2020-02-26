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
import com.android.tools.appinspection.livestore.LiveStore
import com.android.tools.appinspection.livestore.protocol.*
import com.google.gson.Gson

class LiveStoreInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) : Inspector(connection) {
    private val gson = Gson()

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = gson.fromJson(String(data), LiveStoreCommand::class.java)

        command.fetchAll?.let {
            val enums = LiveStore.enumReferences.map { enumClass ->
                val enumValues = enumClass.enumConstants.map { it.name }
                EnumDefinition(enumClass.name, enumValues)
            }
            val stores = environment.findInstances(LiveStore::class.java).map { store ->
                val keyValues = store.keyValues
                    .map { (name, valueEntry) ->
                        KeyValueDefinition(
                            name,
                            ValueDefinition(valueEntry.type, valueEntry.valueAsString, valueEntry.constraintAsString)
                        )
                    }
                    .sortedBy { it.name }
                    .toMutableList()

                LiveStoreDefinition(store.name, keyValues)
            }

            val response = LiveStoreResponse(fetchAll = LiveStoreResponse.FetchAll(enums, stores))
            callback.reply(response)
        }

        command.updateValue?.let { updateValue ->
            val store = environment.findInstances(LiveStore::class.java).firstOrNull { it.name == updateValue.store }
            val valueEntry = store?.keyValues?.get(updateValue.key)
            if (store == null || valueEntry == null) {
                val response = LiveStoreResponse(
                    updateValue = LiveStoreResponse.UpdateValue(
                        updateValue.store,
                        updateValue.key,
                        false
                    )
                )
                callback.reply(response)
                return@let
            }

            val updated = valueEntry.updateFromString(updateValue.newValue)
            val response = LiveStoreResponse(
                updateValue = LiveStoreResponse.UpdateValue(
                    updateValue.store,
                    updateValue.key,
                    updated
                )
            )
            callback.reply(response)
        }
    }

    private fun CommandCallback.reply(response: LiveStoreResponse) {
        reply(gson.toJson(response).toByteArray())
    }
}