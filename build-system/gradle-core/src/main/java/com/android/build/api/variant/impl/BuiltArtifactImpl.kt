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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.api.artifact.toArtifactType
import com.android.ide.common.build.CommonBuiltArtifact
import com.android.ide.common.build.CommonBuiltArtifactTypeAdapter
import com.google.common.collect.ImmutableList
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.nio.file.Path

data class BuiltArtifactImpl(
    override val outputFile: Path,
    override val properties: Map<String, String> = mapOf(),
    override val versionCode: Int = -1,
    override val versionName: String = "",
    override val isEnabled: Boolean = true,
    override val outputType: VariantOutputConfiguration.OutputType,
    override val filters: Collection<FilterConfiguration> = listOf(),
    val baseName: String = "",
    val fullName: String = ""
) : BuiltArtifact, CommonBuiltArtifact {

    fun newOutput(newOutputFile: Path): BuiltArtifactImpl {
        return BuiltArtifactImpl(
            outputFile = newOutputFile,
            properties = properties,
            versionCode = versionCode,
            versionName = versionName,
            isEnabled = isEnabled,
            outputType = outputType,
            filters = filters,
            baseName = baseName,
            fullName = fullName
        )
    }
}

internal class BuiltArtifactTypeAdapter: CommonBuiltArtifactTypeAdapter<BuiltArtifactImpl>() {

    override fun writeSpecificAttributes(out: JsonWriter, value: BuiltArtifactImpl) {
        out.name("type").value(value.outputType.toString())
        if (value.baseName.isNotEmpty()) out.name("baseName").value(value.baseName)
        if (value.fullName.isNotEmpty()) out.name("fullName").value(value.fullName)
        out.name("filters").beginArray()
        for (filter in value.filters) {
            out.beginObject()
            out.name("filterType").value(filter.filterType.toString())
            out.name("value").value(filter.identifier)
            out.endObject()
        }
        out.endArray()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): BuiltArtifactImpl {
        var outputType: String? = null
        val filters = ImmutableList.Builder<FilterConfiguration>()
        var baseName: String? = null
        var fullName: String? = null
        return super.read(reader,
            { attributeName: String ->
                when(attributeName) {
                    "type" -> outputType = reader.nextString()
                    "baseName" -> baseName = reader.nextString()
                    "fullName" -> baseName = reader.nextString()
                    "filters" -> readFilters(reader, filters)
                }
            },
            { outputFile: Path,
                properties: Map<String, String>,
                versionCode: Int,
                versionName: String,
                isEnabled: Boolean ->
                BuiltArtifactImpl(
                    outputType = VariantOutputConfiguration.OutputType.valueOf(outputType!!),
                    filters = filters.build(),
                    outputFile = outputFile,
                    properties = properties,
                    versionCode = versionCode,
                    versionName = versionName,
                    isEnabled = isEnabled,
                    baseName = baseName.orEmpty(),
                    fullName = fullName.orEmpty())
            })
    }

    @Throws(IOException::class)
    private fun readFilters(reader: JsonReader, filters: ImmutableList.Builder<FilterConfiguration>) {

        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var filterType: FilterConfiguration.FilterType? = null
            var value: String? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "filterType" -> filterType = FilterConfiguration.FilterType.valueOf(reader.nextString())
                    "value" -> value = reader.nextString()
                }
            }
            if (filterType != null && value != null) {
                filters.add(FilterConfiguration(filterType, value))
            }
            reader.endObject()
        }
        reader.endArray()
    }
}

internal class ArtifactTypeTypeAdapter : TypeAdapter<ArtifactType<*>>() {

    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: ArtifactType<*>) {
        out.beginObject()
        out.name("type").value(value.name())
        out.name("kind").value(value.kind.dataType().simpleName)
        out.endObject()
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): ArtifactType<*> {
        reader.beginObject()
        var artifactType: ArtifactType<*>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> artifactType = reader.nextString().toArtifactType()
                "kind" -> reader.nextString()
            }
        }
        reader.endObject()
        if (artifactType == null) {
            throw IOException("Invalid artifact type declaration")
        }
        return artifactType
    }
}