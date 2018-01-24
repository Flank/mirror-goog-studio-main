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

package com.android.build.gradle.internal.scope

import com.android.build.FilterData
import com.android.build.VariantOutput
import com.android.build.api.artifact.ArtifactType
import com.android.build.gradle.internal.ide.FilterDataImpl
import com.android.ide.common.build.ApkInfo
import com.android.ide.common.internal.WaitableExecutor
import com.google.common.collect.ImmutableList
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.file.FileCollection
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.Reader
import java.nio.file.Path

/**
 * Factory for {@link BuildElements} that can load its content from save metadata file (.json)
 */
class ExistingBuildElements {
    companion object {

        val executor: WaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool()
        /**
         * create a {@link BuildElement} from a previous task execution metadata file collection.
         * @param elementType the expected element type of the BuildElements.
         * @param from the file collection containing the metadata file.
         */
        @JvmStatic
        fun from(elementType: ArtifactType, from: FileCollection): BuildElements {
            val metadataFile = getMetadataFileIfPresent(from)
            return _from(elementType, metadataFile)
        }

        /**
         * create a {@link BuildElement} from a previous task execution metadata file.
         * @param elementType the expected element type of the BuildElements.
         * @param from the folder containing the metadata file.
         */
        @JvmStatic
        fun from(elementType: ArtifactType, from: File): BuildElements {

            val metadataFile = getMetadataFileIfPresent(from)
            return _from(elementType, metadataFile)
        }

        private fun _from(elementType: ArtifactType,
                metadataFile: File?): BuildElements {
            if (metadataFile == null || !metadataFile.exists()) {
                return BuildElements(ImmutableList.of())
            }
            try {
                FileReader(metadataFile).use({ reader ->
                    return BuildElements(load(metadataFile.parentFile.toPath(),
                            elementType,
                            reader))
                })
            } catch (e: IOException) {
                return BuildElements(ImmutableList.of<BuildOutput>())
            }
        }

        private fun getMetadataFileIfPresent(fileCollection: FileCollection): File? {
            return fileCollection.asFileTree.files.find { it.name == "output.json" }
        }

        @JvmStatic
        fun getMetadataFileIfPresent(folder: File): File? {
            val outputFile = getMetadataFile(folder)
            return if (outputFile.exists()) outputFile else null
        }

        @JvmStatic
        fun getMetadataFile(folder: File): File {
            return File(folder, "output.json")
        }

        @JvmStatic
        fun persistApkList(apkInfos: Collection<ApkInfo>): String {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeHierarchyAdapter(ApkInfo::class.java, ApkInfoAdapter())
            val gson = gsonBuilder.create()
            return gson.toJson(apkInfos)
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun loadApkList(file: File): Collection<ApkInfo> {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeHierarchyAdapter(ApkInfo::class.java, ApkInfoAdapter())
            gsonBuilder.registerTypeAdapter(
                    ArtifactType::class.java,
                    OutputTypeTypeAdapter())
            val gson = gsonBuilder.create()
            val recordType = object : TypeToken<List<ApkInfo>>() {}.type
            return gson.fromJson(FileReader(file), recordType)
        }

        @JvmStatic
        fun load(
                projectPath: Path,
                outputType: ArtifactType?,
                reader: Reader): Collection<BuildOutput> {
            val gsonBuilder = GsonBuilder()

            gsonBuilder.registerTypeAdapter(ApkInfo::class.java, ApkInfoAdapter())
            gsonBuilder.registerTypeAdapter(
                    ArtifactType::class.java,
                    OutputTypeTypeAdapter())
            val gson = gsonBuilder.create()
            val recordType = object : TypeToken<List<BuildOutput>>() {}.type
            val buildOutputs = gson.fromJson<Collection<BuildOutput>>(reader, recordType)
            // resolve the file path to the current project location.
            return buildOutputs
                    .asSequence()
                    .filter { outputType == null || it.type == outputType }
                    .map { buildOutput ->
                        BuildOutput(
                                buildOutput.type,
                                buildOutput.apkInfo,
                                projectPath.resolve(buildOutput.outputPath),
                                buildOutput.properties)
                    }
                    .toList()
        }
    }

    internal class ApkInfoAdapter: TypeAdapter<ApkInfo>() {

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ApkInfo?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.beginObject()
            out.name("type").value(value.type.toString())
            out.name("splits").beginArray()
            for (filter in value.filters) {
                out.beginObject()
                out.name("filterType").value(filter.filterType)
                out.name("value").value(filter.identifier)
                out.endObject()
            }
            out.endArray()
            out.name("versionCode").value(value.versionCode.toLong())
            if (value.versionName != null) {
                out.name("versionName").value(value.versionName)
            }
            out.name("enabled").value(value.isEnabled)
            if (value.filterName != null) {
                out.name("filterName").value(value.filterName)
            }
            if (value.outputFileName != null) {
                out.name("outputFile").value(value.outputFileName)
            }
            out.name("fullName").value(value.fullName)
            out.name("baseName").value(value.baseName)
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(reader: JsonReader): ApkInfo {
            reader.beginObject()
            var outputType: String? = null
            val filters = ImmutableList.builder<FilterData>()
            var versionCode = 0
            var versionName: String? = null
            var enabled = true
            var outputFile: String? = null
            var fullName: String? = null
            var baseName: String? = null
            var filterName: String? = null

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> outputType = reader.nextString()
                    "splits" -> readFilters(reader, filters)
                    "versionCode" -> versionCode = reader.nextInt()
                    "versionName" -> versionName = reader.nextString()
                    "enabled" -> enabled = reader.nextBoolean()
                    "outputFile" -> outputFile = reader.nextString()
                    "filterName" -> filterName = reader.nextString()
                    "baseName" -> baseName = reader.nextString()
                    "fullName" -> fullName = reader.nextString()
                }
            }
            reader.endObject()

            val filterData = filters.build()
            val apkType = VariantOutput.OutputType.valueOf(outputType!!)

            return ApkInfo.of(
                    apkType,
                    filterData,
                    versionCode,
                    versionName,
                    filterName,
                    outputFile,
                    fullName,
                    baseName,
                    enabled)
        }

        @Throws(IOException::class)
        private fun readFilters(reader: JsonReader, filters: ImmutableList.Builder<FilterData>) {

            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var filterType: VariantOutput.FilterType? = null
                var value: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "filterType" -> filterType = VariantOutput.FilterType.valueOf(reader.nextString())
                        "value" -> value = reader.nextString()
                    }
                }
                if (filterType != null && value != null) {
                    filters.add(FilterDataImpl(filterType, value))
                }
                reader.endObject()
            }
            reader.endArray()
        }
    }

    internal class OutputTypeTypeAdapter : TypeAdapter<ArtifactType>() {

        @Throws(IOException::class)
        override fun write(out: JsonWriter, value: ArtifactType) {
            out.beginObject()
            out.name("type").value(value.name())
            out.endObject()
        }

        @Throws(IOException::class)
        override fun read(reader: JsonReader): ArtifactType {
            reader.beginObject()
            if (!reader.nextName().endsWith("type")) {
                throw IOException("Invalid format")
            }
            val nextString = reader.nextString()
            val outputType: ArtifactType = try {
                InternalArtifactType.valueOf(nextString)
            } catch (e: IllegalArgumentException) {
                TaskOutputHolder.AnchorOutputType.valueOf(nextString)
            }

            reader.endObject()
            return outputType
        }
    }
}