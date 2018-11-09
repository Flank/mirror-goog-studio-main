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

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.google.common.collect.Iterators
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.IOException
import org.apache.commons.io.FileUtils
import org.gradle.api.file.FileCollection

/**
 * Information containing the signing config metadata that can be consumed by other modules as
 * persisted json file
 */
class SigningConfigMetadata {
    companion object {
        private const val PERSISTED_FILE_NAME = "signing-config.json"

        @Throws(IOException::class)
        fun load(input: FileCollection?): SigningConfig? {
            val persistedFile = if (input != null) getOutputFile(input) else null
            return  if (persistedFile != null) load(persistedFile) else null
        }

        @Throws(IOException::class)
        fun save(outputDirectory: File, signingConfig: SigningConfig?) {
            val outputFile = File(outputDirectory, PERSISTED_FILE_NAME)
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            val gson = gsonBuilder.create()
            FileUtils.write(outputFile, gson.toJson(signingConfig))
        }

        @Throws(IOException::class)
        fun load(input: File): SigningConfig? {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(File::class.java, PlainFileGsonTypeAdaptor())
            val gson = gsonBuilder.create()
            FileReader(input).use { fileReader ->
                return gson.fromJson(
                    fileReader,
                    SigningConfig::class.java
                )
            }
        }

        private fun getOutputFile(input: FileCollection): File? {
            if (input.asFileTree.isEmpty) return null
            val file = input.asFileTree.singleFile
            if (file.name != PERSISTED_FILE_NAME) return null
            return file
        }

        fun getOutputFile(directory: File): File {
            return File(directory, PERSISTED_FILE_NAME)
        }
    }
}
