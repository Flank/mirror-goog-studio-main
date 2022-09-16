/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder
import com.android.zipflinger.ZipArchive
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.file.Files
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeBytes

/**
 * Generates privacy sandbox manifest snippets from the ASAR to be merged into the app manifest.
 */
@CacheableTransform
abstract class AsarMetadataExtractionTransform: TransformAction<GenericTransformParameters> {

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    abstract fun getInputArtifact(): Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = getInputArtifact().get().asFile.toPath()
        val outputFile = outputs.file(inputFile.fileName.nameWithoutExtension + "_SdkMetadata.pb").toPath()

        ZipArchive(inputFile).use {
            it.getInputStream("SdkMetadata.pb").use { protoBytes ->
                Files.copy(protoBytes, outputFile)
            }
        }

    }
}
