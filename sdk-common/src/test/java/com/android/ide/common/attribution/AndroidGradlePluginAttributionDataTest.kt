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
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData.BuildInfo
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData.JavaInfo
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.StringReader

class AndroidGradlePluginAttributionDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val data = AndroidGradlePluginAttributionData(
        taskNameToClassNameMap = mapOf("a" to "b", "c" to "d"),
        tasksSharingOutput = mapOf("e" to listOf("f", "g")),
        garbageCollectionData = mapOf("gc" to 100L),
        buildSrcPlugins = setOf("h", "i"),
        javaInfo = JavaInfo(
                version = "11.0.8",
                vendor = "JetBrains s.r.o",
                home = "/tmp/test/java/home",
                vmArguments = listOf("-Xmx8G", "-XX:+UseSerialGC")
        ),
        buildscriptDependenciesInfo = setOf(
                "a.a:a:1.0",
                "b.b:b:1.0",
                "c.c:c:1.0"
        ),
        buildInfo = BuildInfo(
                agpVersion = "7.0.0",
                configurationCacheIsOn = true
        )
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
        assertThat(file.readLines()[0]).isEqualTo("""
|{
|"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"garbageCollectionData":[{"gcName":"gc","duration":100}],
|"buildSrcPlugins":["h","i"],
|"javaInfo":{
    |"javaVersion":"11.0.8",
    |"javaVendor":"JetBrains s.r.o",
    |"javaHome":"/tmp/test/java/home",
    |"vmArguments":["-Xmx8G","-XX:+UseSerialGC"]
|},
|"buildscriptDependencies":[
    |"a.a:a:1.0",
    |"b.b:b:1.0",
    |"c.c:c:1.0"
|],
|"buildInfo":{
    |"agpVersion":"7.0.0",
    |"configurationCacheIsOn":true
|}
|}
""".trimMargin().replace("\n", "")
        )
    }

    @Test
    fun testDeserializationOfOldAgpData() {
        val outputDir = temporaryFolder.newFolder()
        // Create file of old format with some data missing.
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        file.parentFile.mkdirs()
        file.writeText("""
|{
|"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"buildSrcPlugins":["h","i"]
|}
""".trimMargin().replace("\n", "")
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData.taskNameToClassNameMap).isEqualTo(data.taskNameToClassNameMap)
        assertThat(deserializedData.noncacheableTasks).isEqualTo(data.noncacheableTasks)
        assertThat(deserializedData.tasksSharingOutput).isEqualTo(data.tasksSharingOutput)
        assertThat(deserializedData.buildSrcPlugins).isEqualTo(data.buildSrcPlugins)

        assertThat(deserializedData.garbageCollectionData).isNotNull()
        assertThat(deserializedData.garbageCollectionData).isEmpty()
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
        file.parentFile.mkdirs()
        file.writeText("""
|{
|"newUndefinedData":{"temp":"test"},
|"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"garbageCollectionData":[{"gcName":"gc","duration":100}],
|"buildSrcPlugins":["h","i"],
|"javaInfo":{
    |"javaVersion":"11.0.8",
    |"javaVendor":"JetBrains s.r.o",
    |"javaHome":"/tmp/test/java/home",
    |"vmArguments":["-Xmx8G","-XX:+UseSerialGC"]
|},
|"newerUndefinedData":{"temp":"test"},
|"buildscriptDependencies":[
    |"a.a:a:1.0",
    |"b.b:b:1.0",
    |"c.c:c:1.0"
|],
|"buildInfo":{
    |"agpVersion":"7.0.0",
    |"configurationCacheIsOn":true
|}
|}
""".trimMargin().replace("\n", "")
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }

    @Test
    fun testEmptyBuildInfo() {
        val outputDir = temporaryFolder.newFolder()
        val data = AndroidGradlePluginAttributionData(buildInfo = BuildInfo(null, null))

        AndroidGradlePluginAttributionData.save(outputDir, data)
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
