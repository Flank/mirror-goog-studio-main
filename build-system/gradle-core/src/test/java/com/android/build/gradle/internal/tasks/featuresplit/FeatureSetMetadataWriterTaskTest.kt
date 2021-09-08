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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.sdklib.AndroidVersion
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException

/** Tests for the [FeatureSetMetadataWriterTask] class  */
@RunWith(Parameterized::class)
class FeatureSetMetadataWriterTaskTest(val minSdkVersion: Int) {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "minSdkVersion={0}")
        fun getParameters(): Collection<Array<Any>> {
            return listOf(
                arrayOf<Any>(AndroidVersion.VersionCodes.LOLLIPOP),
                arrayOf<Any>(AndroidVersion.VersionCodes.O)
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testTask() {
        val inputDirs = ImmutableSet.builder<File>()
        for (i in 0..4) {
            inputDirs.add(generateInputDir("id_$i", "foo.bar.baz$i"))
        }
        val inputFiles = inputDirs.build()

        val outputLocation = File(temporaryFolder.newFolder(), FeatureSetMetadata.OUTPUT_FILE_NAME)
        object : FeatureSetMetadataWriterTask.FeatureSetRunnable() {
            override fun getParameters(): FeatureSetMetadataWriterTask.Params {
                return object : FeatureSetMetadataWriterTask.Params() {
                    override val featureFiles =
                        FakeObjectFactory.factory.fileCollection().from(inputFiles)
                    override val minSdkVersion =
                        FakeGradleProperty(this@FeatureSetMetadataWriterTaskTest.minSdkVersion)
                    override val maxNumberOfFeaturesBeforeOreo =
                        FakeGradleProperty(FeatureSetMetadata.MAX_NUMBER_OF_SPLITS_BEFORE_O)
                    override val outputFile =
                        FakeObjectFactory.factory.fileProperty().fileValue(outputLocation)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        val loaded = FeatureSetMetadata.load(outputLocation)
        for (i in 0..4) {
            assertThat(loaded.getResOffsetFor("id_$i")).isEqualTo(
                if (minSdkVersion < AndroidVersion.VersionCodes.O)
                    FeatureSetMetadata.BASE_ID - i - 1 else
                    FeatureSetMetadata.BASE_ID + i + 1
            )
        }
    }

    @Throws(IOException::class)
    private fun generateInputDir(id: String, appId: String): File {
        val inputDir = temporaryFolder.newFolder()
        val featureSplitDeclaration = FeatureSplitDeclaration(id, appId)
        featureSplitDeclaration.save(inputDir)
        return FeatureSplitDeclaration.getOutputFile(inputDir)
    }
}
