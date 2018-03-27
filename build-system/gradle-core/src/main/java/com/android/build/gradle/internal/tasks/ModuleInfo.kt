/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.UncheckedIOException

data class ModuleInfo(
        val path: String,
        val pluginName: String,
        val hasKotlin: Boolean) {

    /**
     * Saves this module info to the given location
     */
    fun save(to: File) {
        val gson = GsonBuilder()
                .registerTypeAdapter(ModuleInfo::class.java, ModuleInfoAdapter())
                .create()

        // just in case
        FileUtils.mkdirs(to.parentFile)

        Files.asCharSink(to, Charsets.UTF_8).write(gson.toJson(this))
    }

    fun anonymize(nameMap: Map<String, String>): ModuleInfo {
        val newPath = nameMap[path] ?: throw RuntimeException("Can't find new name for $path")

        return ModuleInfo(newPath, pluginName, hasKotlin)
    }
}

/**
 * Saves all the given [ModuleInfo] to the given location
 */
fun saveModules(modules: List<ModuleInfo>, to: File) {
    val gson = GsonBuilder()
            .registerTypeAdapter(ModuleInfo::class.java, ModuleInfoAdapter())
            .create()

    // just in case
    FileUtils.mkdirs(to.parentFile)

    Files.asCharSink(to, Charsets.UTF_8).write(gson.toJson(modules))
}

fun loadModule(from: File): ModuleInfo {

    try {
        FileReader(from).use({ reader ->

            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(ModuleInfo::class.java, ModuleInfoAdapter())
            val gson = gsonBuilder.create()

            val recordType = object : TypeToken<ModuleInfo>() {}.type
            return gson.fromJson<ModuleInfo>(reader, recordType)
        })
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }
}

open class ModuleInfoAdapter: TypeAdapter<ModuleInfo>() {

    override fun write(out: JsonWriter, value: ModuleInfo) {
        out.beginObject()

        out.name("path").value(value.path)
        out.name("pluginName").value(value.pluginName)
        out.name("hasKotlin").value(value.hasKotlin)

        out.endObject()

    }

    override fun read(input: JsonReader): ModuleInfo {
        input.beginObject()

        var path: String? = null
        var type: String? = null
        var hasKotlin = false

        while (input.hasNext()) {
            when (input.nextName()) {
                "path" -> path = input.nextString()
                "pluginName" -> type = input.nextString()
                "hasKotlin" -> hasKotlin = input.nextBoolean()
            }
        }
        input.endObject()

        return ModuleInfo(path!!, type!!, hasKotlin)
    }
}

