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

package com.android.ide.common.build

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Common behaviors for loading and saving [T] to a json file.
 *
 * @param T the in memory representation of the output.json file.
 */
abstract class CommonBuiltArtifactTypeAdapter<T: CommonBuiltArtifact>: TypeAdapter<T>() {

    /**
     * Subclasses should use this hook to write their specific attributes to the
     * json output stream.
     *
     * @param out the json writer to write to.
     * @param value the instance to serialize to json.
     */
    abstract fun writeSpecificAttributes(out: JsonWriter, value: T)

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: T?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        writeSpecificAttributes(out, value)
        out.name("properties").beginArray()
        for (entry in value.properties.entries) {
            out.beginObject()
            out.name("key").value(entry.key)
            out.name("value").value(entry.value)
            out.endObject()
        }
        out.endArray()
        out.name("versionCode").value(value.versionCode)
        out.name("versionName").value(value.versionName)
        out.name("enabled").value(value.isEnabled)
        out.name("outputFile").value(value.outputFile.toString())
        out.endObject()
    }

    /**
     * Common reading behaviors for reading all attributes belonging to [CommonBuiltArtifact].
     *
     * Subclasses specific attributes should be handled by the [handleAttribute] lambda which
     * will be called for any attributes this method does not know about.
     *
     * Instantiation of the [T] object will be handled by the [instantiate] lambda that will be
     * passed with all the parsed attributes as intrinsic types.
     *
     * @param handleAttribute lambda to handle attributes unknown to this method.
     * @param instantiate lambda to instantiate [T] with the parsed attributes.
     */
    @Throws(IOException::class)
    fun read(reader: JsonReader,
        handleAttribute: (attributeName: String) -> Unit,
        instantiate: (outputFile: Path,
            properties: Map<String, String>,
            versionCode: Int,
            versionName: String,
            isEnabled: Boolean) -> T): T {

        reader.beginObject()
        val properties =
            ImmutableMap.Builder<String, String>()
        var versionCode= 0
        var versionName: String? = null
        var outputFile: String? = null
        var isEnabled = true

        while (reader.hasNext()) {
            when (val attributeName = reader.nextName()) {
                "properties" -> readProperties(reader, properties)
                "versionCode" -> versionCode = reader.nextInt()
                "versionName" -> versionName = reader.nextString()
                "outputFile" -> outputFile = reader.nextString()
                "enabled" -> isEnabled = reader.nextBoolean()
                else -> handleAttribute(attributeName)
            }
        }
        reader.endObject()

        return instantiate(
            FileSystems.getDefault().getPath(outputFile!!),
            properties.build(),
            versionCode,
            versionName.orEmpty(),
            isEnabled
        )

    }

    @Throws(IOException::class)
    private fun readProperties(reader: JsonReader, properties: ImmutableMap.Builder<String, String>) {

        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var key: String? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "key" -> key = reader.nextString()
                    "value" -> value = reader.nextString()
                }
            }
            if (key != null) {
                properties.put(key, value.orEmpty())
            }
            reader.endObject()
        }
        reader.endArray()
    }
}

internal class GenericBuiltArtifactTypeAdapter: CommonBuiltArtifactTypeAdapter<GenericBuiltArtifact>() {

    override fun writeSpecificAttributes(out: JsonWriter, value: GenericBuiltArtifact) {
        out.name("type").value(value.outputType)
        out.name("filters").beginArray()
        for (filter in value.filters) {
            out.beginObject()
            out.name("filterType").value(filter.filterType)
            out.name("value").value(filter.identifier)
            out.endObject()
        }
        out.endArray()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): GenericBuiltArtifact {
        var outputType: String? = null
        val filters = ImmutableList.Builder<GenericFilterConfiguration>()
        return super.read(reader,
            { attributeName: String ->
                when(attributeName) {
                    "type" -> outputType = reader.nextString()
                    "filters" -> readFilters(reader, filters)
                    // any other attribute we do not know about is AGP implementation details
                    // we do not care about. it has to be a String though.
                    else -> reader.nextString()
                }
            },
            { outputFile: Path,
                properties: Map<String, String>,
                versionCode: Int,
                versionName: String,
                isEnabled: Boolean ->
                GenericBuiltArtifact(
                    outputType = outputType.orEmpty(),
                    filters = filters.build(),
                    outputFile = outputFile,
                    properties = properties,
                    versionCode = versionCode,
                    versionName = versionName,
                    isEnabled = isEnabled)
            })
    }

    @Throws(IOException::class)
    private fun readFilters(reader: JsonReader, filters: ImmutableList.Builder<GenericFilterConfiguration>) {

        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var filterType: String? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "filterType" -> filterType = reader.nextString()
                    "value" -> value = reader.nextString()
                }
            }
            if (filterType != null && value != null) {
                filters.add(GenericFilterConfiguration(filterType, value))
            }
            reader.endObject()
        }
        reader.endArray()
    }
}