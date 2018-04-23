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

import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.options.IntegerOption
import com.google.common.base.Splitter
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
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

    @Input
    var minSdkVersion: Int = 1
        internal set

    @Input
    var maxNumberOfFeaturesBeforeOreo: Int = FeatureSetMetadata.MAX_NUMBER_OF_SPLITS_BEFORE_O
        internal set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val featureMetadata = FeatureSetMetadata(maxNumberOfFeaturesBeforeOreo)

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
            featureMetadata.addFeatureSplit(
                minSdkVersion, feature.modulePath, featureNameMap[feature.modulePath]!!)
        }

        // save the list.
        featureMetadata.save(outputFile)
    }

    /**
     * Converts from a list of [FeatureSplitDeclaration] to a map of (module-path -> feature name)
     *
     * This also performs validation to ensure all feature name are unique.
     */
    @VisibleForTesting
    fun computeFeatureNames(features: List<FeatureSplitDeclaration>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // first go through all the module path, and search for duplicates in the last segment
        // we're going to create a map of (leaf -> list(full paths)).
        val leafMap = features.groupBy({ it.modulePath.getLeaf() }, { it.modulePath })

        for ((leaf, modules) in leafMap) {
            if (modules.size == 1) {
                result[modules[0]] = leaf
            } else {
                val message = StringBuilder(
                    "Module name '$leaf' is used by multiple modules. All dynamic features must have a unique name.")
                for (module in modules) {
                    message.append("\n\t-> $module")
                }
                throw RuntimeException(message.toString())
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
            task.minSdkVersion = variantScope.minSdkVersion.apiLevel

            task.outputFile = variantScope.artifacts.appendArtifact(
                    InternalArtifactType.FEATURE_SET_METADATA,
                    task,
                    FeatureSetMetadata.OUTPUT_FILE_NAME)

            task.inputFiles = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.MODULE,
                AndroidArtifacts.ArtifactType.METADATA_FEATURE_DECLARATION
            )
            val maxNumberOfFeaturesBeforeOreo = variantScope.globalScope.projectOptions
                .get(IntegerOption.PRE_O_MAX_NUMBER_OF_FEATURES)
            if (maxNumberOfFeaturesBeforeOreo != null) {
                task.maxNumberOfFeaturesBeforeOreo =
                        Integer.min(100, maxNumberOfFeaturesBeforeOreo)
            }
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
