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

package com.android.ide.common.attribution

import com.android.SdkConstants
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidGradlePluginAttributionDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val data = AndroidGradlePluginAttributionData(
        taskNameToClassNameMap = mapOf("a" to "b", "c" to "d"),
        tasksSharingOutput = mapOf("e" to listOf("f", "g"))
    )

    @Test
    fun testDataSerialization() {
        val outputDir = temporaryFolder.newFolder()
        AndroidGradlePluginAttributionData.save(outputDir, data)

        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        assertThat(file.readLines()[0]).isEqualTo(
            """{"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}]}"""
        )
    }

    @Test
    fun testDeserializationOfOldAgpData() {
        val outputDir = temporaryFolder.newFolder()
        AndroidGradlePluginAttributionData.save(outputDir, data)

        // modify the file to delete the garbage collection data
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        file.writeText(
            file.readLines()[0].replace(
                ""","tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}]""",
                ""
            )
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData.taskNameToClassNameMap).isEqualTo(data.taskNameToClassNameMap)
        assertThat(deserializedData.noncacheableTasks).isEqualTo(data.noncacheableTasks)

        assertThat(deserializedData.tasksSharingOutput).isNotNull()
        assertThat(deserializedData.tasksSharingOutput).isEmpty()
    }

    @Test
    fun testDeserializationOfNewerAgpData() {
        val outputDir = temporaryFolder.newFolder()
        AndroidGradlePluginAttributionData.save(outputDir, data)

        // modify the file to add a new data field at the end
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        val fileContents = file.readLines()[0]
        file.writeText(
            """{"newUndefinedData":{"temp":"test"},""" +
                    fileContents.substring(1, fileContents.length - 1) +
                    ""","newerUndefinedData":{"temp":"test"}}"""
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }

    @Test
    fun testEmptyData() {
        val outputDir = temporaryFolder.newFolder()
        val data = AndroidGradlePluginAttributionData()

        AndroidGradlePluginAttributionData.save(outputDir, data)
        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }
}