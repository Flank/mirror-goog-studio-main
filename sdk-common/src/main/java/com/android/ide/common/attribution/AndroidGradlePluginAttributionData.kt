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

package com.android.ide.common.attribution

import com.android.SdkConstants
import com.android.utils.FileUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.Exception

data class AndroidGradlePluginAttributionData(
    /**
     * A map that maps a task name to its class name
     * ex: mergeDevelopmentDebugResources -> com.android.build.gradle.tasks.MergeResources
     */
    val taskNameToClassNameMap: Map<String, String>,

    /**
     * Contains registered tasks that are not cacheable.
     */
    val noncacheableTasks: Set<String>,

    /**
     * Contains a list of tasks sharing the same outputs.
     * The key of the map represents the absolute path to the file or the directory output and the
     * key contains a list of tasks declaring this file or directory as their output.
     */
    val tasksSharingOutput: Map<String, List<String>>
) : Serializable {
    companion object {
        fun save(outputDir: File, attributionData: AndroidGradlePluginAttributionData) {
            val file = FileUtils.join(
                outputDir,
                SdkConstants.FD_BUILD_ATTRIBUTION,
                SdkConstants.FN_AGP_ATTRIBUTION_DATA
            )
            file.parentFile.mkdirs()
            ObjectOutputStream(BufferedOutputStream(FileOutputStream(file))).use {
                it.writeObject(attributionData)
            }
        }

        fun load(outputDir: File): AndroidGradlePluginAttributionData? {
            val file = FileUtils.join(
                outputDir,
                SdkConstants.FD_BUILD_ATTRIBUTION,
                SdkConstants.FN_AGP_ATTRIBUTION_DATA
            )
            try {
                ObjectInputStream(BufferedInputStream(FileInputStream(file))).use {
                    return it.readObject() as AndroidGradlePluginAttributionData
                }
            } catch (e: Exception) {
                return null
            } finally {
                FileUtils.deleteRecursivelyIfExists(file.parentFile)
            }
        }
    }
}
