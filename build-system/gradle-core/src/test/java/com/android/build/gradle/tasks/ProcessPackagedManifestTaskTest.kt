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
import com.android.SdkConstants.PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX
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
        workItemParameters.privacySandboxSdkManifestSnippets.set(emptyList())
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

    @Test
    fun testManifestSnippetInjection() {
        val sourceFolder = temporaryFolder.newFolder("source_folder")
        val inputXmlFile = File(sourceFolder, SdkConstants.ANDROID_MANIFEST_XML).also {
            it.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="11">
                    <application android:name="android.support.multidex.MultiDexApplication">
                    </application>
                </manifest>
            """.trimIndent())
        }

        val packageName = "com.package"
        val certDigest = "CERT_DIGEST"
        val version = "10000"
        val snippetFile =  File(sourceFolder, "test$PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX")

        snippetFile.writeText("""
                    <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <uses-sdk-library
                                android:name="$packageName"
                                android:certDigest="$certDigest"
                                android:versionMajor="$version"/>
                        </application>
                    </manifest>
            """.trimIndent())

        val workItemParameters =
            project.objects.newInstance(ProcessPackagedManifestTask.WorkItemParameters::class.java)
        workItemParameters.inputXmlFile.set(inputXmlFile)
        val outputFolder = temporaryFolder.newFolder("target_folder")
        workItemParameters.outputXmlFile.set(File(outputFolder, SdkConstants.ANDROID_MANIFEST_XML))
        workItemParameters.analyticsService.set(FakeNoOpAnalyticsService())
        workItemParameters.taskPath.set("taskPath")
        workItemParameters.workerKey.set("workerKey")
        workItemParameters.privacySandboxSdkManifestSnippets.set(listOf(snippetFile))

        project.objects.newInstance(
            ProcessPackagedManifestTask.WorkItem::class.java,
            workItemParameters).execute()
        val outputXml = workItemParameters.outputXmlFile.get().asFile.readText(Charsets.UTF_8)
        assertThat(outputXml.toSystemLineSeparator()).isEqualTo("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.app"
                android:versionCode="11" >

                <application android:name="android.support.multidex.MultiDexApplication" >
                    <uses-sdk-library
                        android:name="$packageName"
                        android:certDigest="$certDigest"
                        android:versionMajor="$version" />
                </application>

            </manifest>
        """.trimIndent().toSystemLineSeparator())
    }

    @Test
    fun testMultipleManifestSnippetInjection() {
        val sourceFolder = temporaryFolder.newFolder("source_folder")
        val inputXmlFile = File(sourceFolder, SdkConstants.ANDROID_MANIFEST_XML).also {
            it.writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.app"
                    android:versionCode="11">
                    <application android:name="android.support.multidex.MultiDexApplication">
                    </application>
                </manifest>
            """.trimIndent()
            )
        }

        val packageName1 = "com.package1"
        val certDigest1 = "CERT_DIGEST1"
        val version1 = "10000"
        val snippetFile1 =
            File(sourceFolder, "test1$PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX")
        snippetFile1.writeText(
            """
                    <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <uses-sdk-library
                                android:name="$packageName1"
                                android:certDigest="$certDigest1"
                                android:versionMajor="$version1"/>
                        </application>
                    </manifest>
            """.trimIndent()
        )

        val packageName2 = "com.package2"
        val certDigest2 = "CERT_DIGEST2"
        val version2 = "20000"
        val snippetFile2 =
            File(sourceFolder, "test2$PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX")
        snippetFile2.writeText(
            """
                    <manifest
                        xmlns:android="http://schemas.android.com/apk/res/android">
                        <application>
                            <uses-sdk-library
                                android:name="$packageName2"
                                android:certDigest="$certDigest2"
                                android:versionMajor="$version2"/>
                        </application>
                    </manifest>
            """.trimIndent()
        )

        val workItemParameters =
            project.objects.newInstance(ProcessPackagedManifestTask.WorkItemParameters::class.java)
        workItemParameters.inputXmlFile.set(inputXmlFile)
        val outputFolder = temporaryFolder.newFolder("target_folder")
        workItemParameters.outputXmlFile.set(File(outputFolder, SdkConstants.ANDROID_MANIFEST_XML))
        workItemParameters.analyticsService.set(FakeNoOpAnalyticsService())
        workItemParameters.taskPath.set("taskPath")
        workItemParameters.workerKey.set("workerKey")
        workItemParameters.privacySandboxSdkManifestSnippets.set(listOf(snippetFile1, snippetFile2))

        project.objects.newInstance(
            ProcessPackagedManifestTask.WorkItem::class.java,
            workItemParameters
        ).execute()
        val outputXml = workItemParameters.outputXmlFile.get().asFile.readText(Charsets.UTF_8)
        assertThat(outputXml.toSystemLineSeparator()).isEqualTo(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.app"
                android:versionCode="11" >

                <application android:name="android.support.multidex.MultiDexApplication" >
                    <uses-sdk-library
                        android:name="$packageName1"
                        android:certDigest="$certDigest1"
                        android:versionMajor="$version1" />
                    <uses-sdk-library
                        android:name="$packageName2"
                        android:certDigest="$certDigest2"
                        android:versionMajor="$version2" />
                </application>

            </manifest>
        """.trimIndent().toSystemLineSeparator()
        )
    }
}
