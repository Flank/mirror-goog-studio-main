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

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.tasks.ProcessManifestForInstantAppTask.WorkItem
import com.android.build.gradle.tasks.ProcessManifestForInstantAppTask.WorkItemParameters
import com.android.utils.PositionXmlParser
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

class ProcessManifestForInstantAppTaskTest {

    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var task: ProcessManifestForInstantAppTask
    private lateinit var sourceManifestFolder: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register(
            "testManifestForInstantApp", ProcessManifestForInstantAppTask::class.java)
        task = taskProvider.get()
        sourceManifestFolder = temporaryFolder.newFolder("source_manifests")
        task.mergedManifests.set(sourceManifestFolder)
    }

    @Test
    @Throws(Exception::class)
    fun testInstantAppAddingTargetSandbox() {
        val sourceManifest = File(sourceManifestFolder, "AndroidManifest.xml").also {
            it.writeText(
                """
                <manifest
                    package="com.foo.example"
                    featureSplit="feature"
                    xmlns:t="http://schemas.android.com/apk/res/android">
                    <application t:name=".applicationOne">
                        <activity t:name="activityOne" t:splitName="feature"/>
                    </application>
                    <uses-split t:name="featureA"/>
                    <uses-sdk
                        t:minSdkVersion="22"
                        t:targetSdkVersion="27" />
                </manifest>""".trimIndent()
            )
        }
        testWithSourceXml(sourceManifest)
    }

    @Test
    @Throws(Exception::class)
    fun testInstantAppReplacingTargetSandbox() {
        val sourceManifest = File(sourceManifestFolder, "AndroidManifest.xml").also {
            it.writeText(
                """
                <manifest
                    package="com.foo.example"
                    featureSplit="feature"
                    xmlns:t="http://schemas.android.com/apk/res/android"
                    t:targetSandboxVersion="1">
                    <application t:name=".applicationOne">
                        <activity t:name="activityOne" t:splitName="feature"/>
                    </application>
                    <uses-split t:name="featureA"/>
                    <uses-sdk
                        t:minSdkVersion="22"
                        t:targetSdkVersion="27" />
                </manifest>""".trimIndent()
            )
        }
        testWithSourceXml(sourceManifest)
    }

    private fun testWithSourceXml(sourceManifest: File) {

        val workItemParameters =
            project.objects.newInstance(WorkItemParameters::class.java)
        workItemParameters.inputXmlFile.set(sourceManifest)
        workItemParameters.outputXmlFile.set(
            File(temporaryFolder.newFolder("target_manifests"),
                SdkConstants.ANDROID_MANIFEST_XML)
        )
        workItemParameters.analyticsService.set(FakeNoOpAnalyticsService())
        workItemParameters.taskPath.set("taskPath")
        workItemParameters.workerKey.set("workerKey")
        project.objects.newInstance(WorkItem::class.java, workItemParameters as WorkItemParameters).execute()

        val xmlDocument =
            PositionXmlParser.parse(
                workItemParameters.outputXmlFile.get().asFile.readText(Charsets.UTF_8))

        assertThat(xmlDocument.documentElement.getAttributeNS(
            SdkConstants.ANDROID_URI,
            SdkConstants.ATTR_TARGET_SANDBOX_VERSION)
        )
            .isEqualTo("2")

    }
}