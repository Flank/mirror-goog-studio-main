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

package com.android.build.gradle.tasks

import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

class ProcessManifestForBundleTaskTest {

    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var mainSplit: VariantOutputImpl

    @Mock
    lateinit var workers: WorkerExecutor

    private lateinit var task: ProcessManifestForBundleTask
    private lateinit var sourceManifestFolder: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("testManifestForBundle", ProcessManifestForBundleTask::class.java)
        task = taskProvider.get()
        sourceManifestFolder = temporaryFolder.newFolder("source_manifest")
        Mockito.`when`(mainSplit.outputType).thenReturn(
            VariantOutputConfiguration.OutputType.SINGLE)
        Mockito.`when`(mainSplit.filters).thenReturn(listOf())
        task.mainSplit.set(mainSplit)
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testWithoutFeatureNameProcessing() {

        val sourceManifest = File(sourceManifestFolder, "AndroidManifest.xml")
        sourceManifest.writeText("Some content")
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.MERGED_MANIFESTS,
            applicationId = "appId",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(sourceManifest.absolutePath)
            )
        ).saveToDirectory(sourceManifestFolder)
        task.applicationMergedManifests.set(sourceManifestFolder)

        task.bundleManifest.set(temporaryFolder.newFile("output_manifest.xml"))
        task.taskAction()

        Truth.assertThat(task.bundleManifest.get().asFile.readText(Charsets.UTF_8))
            .isEqualTo("Some content")
    }

    @Test
    fun testFeatureNameNotRemoved() {
        val sourceManifest = File(sourceManifestFolder, "AndroidManifest.xml")
        sourceManifest.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:dist="http://schemas.android.com/apk/distribution"
                    featureSplit="feature1"
                    package="com.example.app"
                    android:versionCode="11" >
                
                    <application android:debuggable="true" >
                        <activity
                            android:name="com.example.feature1.FeatureActivity"
                            android:label="Feature Activity" 
                            android:splitName="feature1">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>""".trimIndent()
        )

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.MERGED_MANIFESTS,
            applicationId = "appId",
            variantName = "debug",
            elements = listOf(
                BuiltArtifactImpl.make(sourceManifest.absolutePath)
            )
        ).saveToDirectory(sourceManifestFolder)
        task.applicationMergedManifests.set(sourceManifestFolder)

        task.bundleManifest.set(temporaryFolder.newFile("output_manifest.xml"))
        task.taskAction()

        Truth.assertThat(task.bundleManifest.get().asFile.readText(Charsets.UTF_8))
            .contains("android:splitName=\"feature1\"")
    }
}