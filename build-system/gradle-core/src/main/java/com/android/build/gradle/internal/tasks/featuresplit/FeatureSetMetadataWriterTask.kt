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

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.utils.FileUtils
import com.google.common.base.Splitter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException
import java.util.regex.Pattern

/** Task to write the FeatureSetMetadata file.  */
open class FeatureSetMetadataWriterTask : AndroidVariantTask() {

    @get:InputFiles
    lateinit var inputFiles: FileCollection
        internal set

    @get:OutputFile
    lateinit var outputFile: File
        internal set


    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val featureMetadata = FeatureSetMetadata()

        val featureFiles = inputFiles.asFileTree.files
        val features = mutableListOf<FeatureSplitDeclaration>()

        for (file in featureFiles) {
            try {
                features.add(FeatureSplitDeclaration.load(file))
            } catch (e: FileNotFoundException) {
                throw BuildException("Cannot read features split declaration file", e)
            }
        }

        val featureNameMap = computeFeatureNames(features)

        for (feature in features) {
            featureMetadata.addFeatureSplit(feature.modulePath, featureNameMap[feature.modulePath]!!)
        }

        // save the list.
        featureMetadata.save(outputFile)
    }

    /**
     * Returns a map of (module-path -> feature name) where the feature names are guaranteed to be
     * unique.
     */
    private fun computeFeatureNames(features: List<FeatureSplitDeclaration>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // first go through all the module path, and search for duplicates in the last segment
        // we're going to create a map of (leaf -> list(full paths))
        val leafMap = features.groupBy({ it.modulePath.getLeaf() }, { it.modulePath })

        for ((leaf, modules) in leafMap) {
            if (modules.size == 1) {
                result[modules[0]] = leaf
            } else {
                var index = 'A'
                for (module in modules) {
                    result[module] = leaf + index
                    index++
                }
            }
        }

        return result
    }

    class ConfigAction(private val variantScope: VariantScope) :
        TaskConfigAction<FeatureSetMetadataWriterTask> {

        override fun getName(): String {
            return variantScope.getTaskName("generate", "FeatureMetadata")
        }

        override fun getType(): Class<FeatureSetMetadataWriterTask> {
            return FeatureSetMetadataWriterTask::class.java
        }

        override fun execute(task: FeatureSetMetadataWriterTask) {
            task.variantName = variantScope.fullVariantName

            task.outputFile = variantScope.buildArtifactsHolder.appendArtifact(
                    InternalArtifactType.FEATURE_SET_METADATA,
                    task,
                    FeatureSetMetadata.OUTPUT_FILE_NAME)

            task.inputFiles = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.MODULE,
                AndroidArtifacts.ArtifactType.METADATA_FEATURE_DECLARATION
            )
        }
    }
}

/** Regular expression defining the character to be replaced in the split name.  */
private val FEATURE_REPLACEMENT = Pattern.compile("-")

/** Regular expression defining the characters to be excluded from the split name.  */
private val FEATURE_EXCLUSION = Pattern.compile("[^a-zA-Z0-9_]")

private fun String.getLeaf(): String {
    val baseName: String = Splitter.on(':').split(this).last()!!
    if (baseName.isEmpty()) {
        return "root"
    }

    // Compute the split value name for the manifest.
    val splitName = FEATURE_REPLACEMENT
        .matcher(baseName)
        .replaceAll("_")
    return FEATURE_EXCLUSION.matcher(splitName).replaceAll("")
}
