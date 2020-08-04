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
import com.android.utils.toSystemLineSeparator
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

class ProcessPackagedManifestTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    private lateinit var task: ProcessPackagedManifestTask
    private lateinit var project: Project

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        project= ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register(
            "testManifestForPackage", ProcessPackagedManifestTask::class.java
        )
        task = taskProvider.get()
    }

    @Test
    fun testDynamicFeatureDecorationsRemoval() {
        val sourceFolder = temporaryFolder.newFolder("source_folder")
        val inputXmlFile = File(sourceFolder, SdkConstants.ANDROID_MANIFEST_XML).also {
            it.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="11" >

                    <application
                        android:name="android.support.multidex.MultiDexApplication"
                        android:debuggable="true" >
                        <activity
                            android:name="com.example.app.BaseActivity"
                            android:label="Base Activity" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <activity
                            android:name="com.example.feature1.FeatureActivity"
                            android:label="Feature Activity"
                            android:splitName="feature1" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <activity
                            android:name="com.example.feature2.FeatureActivity"
                            android:label="Feature Activity 2"
                            android:splitName="feature2" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent())
        }
        val workItemParameters =
            project.objects.newInstance(ProcessPackagedManifestTask.WorkItemParameters::class.java)
        workItemParameters.inputXmlFile.set(inputXmlFile)
        val outputFolder = temporaryFolder.newFolder("target_folder")
        workItemParameters.outputXmlFile.set(File(outputFolder, SdkConstants.ANDROID_MANIFEST_XML))
        workItemParameters.analyticsService.set(FakeNoOpAnalyticsService())
        workItemParameters.taskPath.set("taskPath")
        workItemParameters.workerKey.set("workerKey")

        project.objects.newInstance(
            ProcessPackagedManifestTask.WorkItem::class.java,
            workItemParameters).execute()
        val outputXml = workItemParameters.outputXmlFile.get().asFile.readText(Charsets.UTF_8)
        assertThat(outputXml).doesNotContain("android:splitName")
    }

    @Test
    fun testIdemPotent() {
        val sourceFolder = temporaryFolder.newFolder("source_folder")
        val inputXmlFile = File(sourceFolder, SdkConstants.ANDROID_MANIFEST_XML).also {
            it.writeText(
                """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.app"
                android:versionCode="11" >

                <application
                    android:name="android.support.multidex.MultiDexApplication"
                    android:debuggable="true" >
                    <activity
                        android:name="com.example.app.BaseActivity"
                        android:label="Base Activity" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity
                        android:name="com.example.feature1.FeatureActivity"
                        android:label="Feature Activity" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            
            </manifest>
        """.trimIndent()
            )
        }
        val workItemParameters =
            project.objects.newInstance(ProcessPackagedManifestTask.WorkItemParameters::class.java)
        workItemParameters.inputXmlFile.set(inputXmlFile)
        val outputFolder = temporaryFolder.newFolder("target_folder")
        workItemParameters.outputXmlFile.set(
            File(
                outputFolder,
                SdkConstants.ANDROID_MANIFEST_XML
            )
        )
        workItemParameters.analyticsService.set(FakeNoOpAnalyticsService())
        workItemParameters.taskPath.set("taskPath")
        workItemParameters.workerKey.set("workerKey")

        project.objects.newInstance(
            ProcessPackagedManifestTask.WorkItem::class.java,
            workItemParameters
        ).execute()
        val outputXml = workItemParameters.outputXmlFile.get().asFile.readText(Charsets.UTF_8)
        assertThat(outputXml.toSystemLineSeparator()).isEqualTo(
            inputXmlFile.readText(Charsets.UTF_8).toSystemLineSeparator())
    }
}