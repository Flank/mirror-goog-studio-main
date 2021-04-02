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
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import kotlin.test.fail

class ProcessMultiApkApplicationManifestTest {

    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var task: ProcessMultiApkApplicationManifest
    private lateinit var sourceManifestFolder: File


    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("testProcessApplicationManifest", ProcessMultiApkApplicationManifest::class.java)
        task = taskProvider.get()
        sourceManifestFolder = temporaryFolder.newFolder("source_manifest")
        task.mainMergedManifest.set(File(sourceManifestFolder, SdkConstants.ANDROID_MANIFEST_XML))
        task.multiApkManifestOutputDirectory.set(temporaryFolder.newFolder("output_manifests"))
        task.compatibleScreensManifest.set(temporaryFolder.newFolder("compatible_screen_manifests"))
        task.applicationId.set("com.android.test")
        task.variantName = "debug"
        task.analyticsService.set(FakeNoOpAnalyticsService())
        initSourceMainManifest(task.mainMergedManifest.get().asFile)
    }

    @Test
    fun testNoSplit() {
        val mainOutput = VariantOutputImpl(
            FakeGradleProperty(value = 0),
            FakeGradleProperty(value = ""),
            FakeGradleProperty(value = true),
            VariantOutputConfigurationImpl(false, listOf()),
            "base_name",
            "main_full_name",
            FakeGradleProperty(value = "output_file_name")
        )
        task.variantOutputs.add(mainOutput)
        task.singleVariantOutput.set(mainOutput)

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = task.applicationId.get(),
            variantName = task.variantName,
            elements = listOf()
        ).saveToDirectory(task.compatibleScreensManifest.get().asFile)

        task.taskAction(Mockito.mock(IncrementalTaskInputs::class.java))

        val listFiles = task.multiApkManifestOutputDirectory.asFile.get().listFiles()
        assertThat(listFiles).hasLength(2)
        assertThat(
            File(task.multiApkManifestOutputDirectory.asFile.get(),
                SdkConstants.ANDROID_MANIFEST_XML).readText())
            .isEqualTo(task.mainMergedManifest.get().asFile.readText())
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(task.multiApkManifestOutputDirectory)
        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.elements).hasSize(1)
        assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        assertThat(builtArtifacts.variantName).isEqualTo("debug")
        val singleOutput = builtArtifacts.elements.single()
        assertThat(singleOutput.variantOutputConfiguration.outputType).isEqualTo(
            VariantOutputConfiguration.OutputType.SINGLE
        )
    }

    @Test
    fun testAbiSplit() {
        val x86 = createVariantOutputForAbi("x86")
        x86.versionCode.set(24)
        task.variantOutputs.add(x86)
        val arm = createVariantOutputForAbi("arm")
        arm.versionCode.set(23)
        task.variantOutputs.add(arm)
        val x86_64 = createVariantOutputForAbi("x86_64")
        x86_64.versionCode.set(22)
        task.variantOutputs.add(x86_64)

        val mainOutput = createVariantOutput()
        task.singleVariantOutput.set(mainOutput)
        task.variantOutputs.add(mainOutput)

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = task.applicationId.get(),
            variantName = task.variantName,
            elements = listOf()
        ).saveToDirectory(task.compatibleScreensManifest.get().asFile)

        task.taskAction(Mockito.mock(IncrementalTaskInputs::class.java))

        val listFiles = task.multiApkManifestOutputDirectory.asFile.get().listFiles()
        assertThat(listFiles).hasLength(5)
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(task.multiApkManifestOutputDirectory)
        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.elements).hasSize(4)
        assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        assertThat(builtArtifacts.variantName).isEqualTo("debug")
        builtArtifacts.elements.forEach {
            val finalMergedManifest = File(it.outputFile)
            val manifestContent = finalMergedManifest.readText()
            assertThat(finalMergedManifest.exists()).isTrue()
            if (it.variantOutputConfiguration.filters.size == 1) {
                val filter = it.variantOutputConfiguration.filters.single()
                assertThat(it.variantOutputConfiguration.outputType).isEqualTo(
                    VariantOutputConfiguration.OutputType.ONE_OF_MANY
                )
                assertThat(filter.filterType).isEqualTo(FilterConfiguration.FilterType.ABI)
                assertThat(finalMergedManifest.absolutePath).contains(filter.identifier)
                val expectedVersion = when(filter.identifier) {
                    "x86" -> 24
                    "arm" -> 23
                    "x86_64" -> 22
                    else -> fail("Unknown ABI filter")
                }
                assertThat(manifestContent).contains("android:versionCode=\"$expectedVersion\"")
            } else {
                assertThat(it.variantOutputConfiguration.outputType).isEqualTo(
                    VariantOutputConfiguration.OutputType.SINGLE
                )
                assertThat(manifestContent).contains("android:versionCode=\"12\"")
            }
        }
    }


    @Test
    fun testSeveralSplitNoUniversal() {
        val xxhpdi = createVariantOutputForDensity("xxhdpi")
        task.variantOutputs.add(xxhpdi)
        val xhdpi = createVariantOutputForDensity("xhdpi")
        task.variantOutputs.add(xhdpi)
        val hdpi = createVariantOutputForDensity("hdpi")
        task.variantOutputs.add(hdpi)

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = task.applicationId.get(),
            variantName = task.variantName,
            elements = listOf(
                xxhpdi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "480")),
                xhdpi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "xhdpi")),
                hdpi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "hdpi"))
            )
        ).saveToDirectory(task.compatibleScreensManifest.get().asFile)

        task.taskAction(Mockito.mock(IncrementalTaskInputs::class.java))

        val listFiles = task.multiApkManifestOutputDirectory.asFile.get().listFiles()
        assertThat(listFiles).hasLength(4)
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(task.multiApkManifestOutputDirectory)
        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.elements).hasSize(3)
        assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        assertThat(builtArtifacts.variantName).isEqualTo("debug")
        builtArtifacts.elements.forEach {
            assertThat(it.variantOutputConfiguration.outputType).isEqualTo(
                VariantOutputConfiguration.OutputType.ONE_OF_MANY
            )
            assertThat(it.variantOutputConfiguration.filters.size).isEqualTo(1)
            val filter = it.variantOutputConfiguration.filters.single()
            assertThat(filter.filterType).isEqualTo(FilterConfiguration.FilterType.DENSITY)
            val finalMergedManifest = File(it.outputFile)
            assertThat(finalMergedManifest.exists()).isTrue()
            assertThat(finalMergedManifest.absolutePath).contains(filter.identifier)
            val manifestContent = finalMergedManifest.readText()
            val densityValue = if (filter.identifier == "xxhdpi") "480" else filter.identifier
            assertThat(manifestContent).contains("android:screenDensity=\"$densityValue\"")
        }
    }

    @Test
    fun testSeveralSplitNoUniversalWithVariantOutputSpecificVersions() {
        val xxhdpi = createVariantOutputForDensity("xxhdpi")
        xxhdpi.versionCode.set(24)
        xxhdpi.versionName.set("twentyfour")
        task.variantOutputs.add(xxhdpi)
        val xhdpi = createVariantOutputForDensity("xhdpi")
        xhdpi.versionCode.set(23)
        xhdpi.versionName.set("twentythree")
        task.variantOutputs.add(xhdpi)
        val hdpi = createVariantOutputForDensity("hdpi")
        hdpi.versionCode.set(22)
        hdpi.versionName.set("twentytwo")
        task.variantOutputs.add(hdpi)

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = task.applicationId.get(),
            variantName = task.variantName,
            elements = listOf(
                xxhdpi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "480")),
                xhdpi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "xhdpi")),
                hdpi.toBuiltArtifact(createCompatibleScreensManifestForDensity(
                    task.compatibleScreensManifest.get().asFile,
                    "hdpi"))
            )
        ).saveToDirectory(task.compatibleScreensManifest.get().asFile)

        task.taskAction(Mockito.mock(IncrementalTaskInputs::class.java))

        val listFiles = task.multiApkManifestOutputDirectory.asFile.get().listFiles()
        assertThat(listFiles).hasLength(4)
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(task.multiApkManifestOutputDirectory)
        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.elements).hasSize(3)
        builtArtifacts.elements.forEach {
            val manifestContent = File(it.outputFile).readText()
            val filter = it.variantOutputConfiguration.filters.single()
            when(filter.identifier) {
                "xxhdpi" -> assertThat(manifestContent).contains("android:versionCode=\"24\"")
                "xhdpi" -> assertThat(manifestContent).contains("android:versionCode=\"23\"")
                "hdpi" -> assertThat(manifestContent).contains("android:versionCode=\"22\"")
                else -> fail("Unknown Density : ${filter.identifier}")
            }
        }
    }

    private fun createVariantOutput(filter: FilterConfigurationImpl? = null) =
        VariantOutputImpl(
            FakeGradleProperty(value = 0),
            FakeGradleProperty(value =""),
            FakeGradleProperty(value =true),
            VariantOutputConfigurationImpl(false, if (filter != null) listOf(filter) else listOf()),
            "base_name",
            "main_full_name",
            FakeGradleProperty(value = "some_output_file")
        )

    private fun createVariantOutputForFilter(filterType: FilterConfiguration.FilterType, identifier: String) =
        createVariantOutput(FilterConfigurationImpl(filterType, identifier))

    private fun createVariantOutputForDensity(density: String) =
        createVariantOutputForFilter(FilterConfiguration.FilterType.DENSITY, density)

    private fun createVariantOutputForAbi(density: String) =
        createVariantOutputForFilter(FilterConfiguration.FilterType.ABI, density)

    private fun createCompatibleScreensManifestForDensity(folder: File, density: String): File {
        val parentFolder = File(folder, density)
        parentFolder.mkdirs()
        val manifestFile = File(parentFolder, SdkConstants.ANDROID_MANIFEST_XML)
        manifestFile.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <uses-sdk android:minSdkVersion="16"/>
                <compatible-screens>
                    <screen android:screenSize="small" android:screenDensity="$density" />
                    <screen android:screenSize="normal" android:screenDensity="$density" />
                    <screen android:screenSize="large" android:screenDensity="$density" />
                    <screen android:screenSize="xlarge" android:screenDensity="$density" />
                </compatible-screens>
            </manifest>
        """.trimIndent())
        return manifestFile
    }

    private fun initSourceMainManifest(outputFile: File) {
        outputFile.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.android.tests.basic.debug"
                android:versionCode="12"
                android:versionName="2.0" >

                <uses-sdk
                    android:minSdkVersion="16"
                    android:targetSdkVersion="16" />

                <uses-permission android:name="com.blah12" />

                <permission-group
                    android:name="foo.permission-group.COST_MONEY"
                    android:description="@string/app_name"
                    android:label="@string/app_name" />

                <permission
                    android:name="foo.permission.SEND_SMS"
                    android:description="@string/app_name"
                    android:label="@string/app_name"
                    android:permissionGroup="foo.permission-group.COST_MONEY" />

                <application
                    android:appComponentFactory="android.support.v4.app.CoreComponentFactory"
                    android:debuggable="true"
                    android:icon="@drawable/icon"
                    android:label="@string/app_name" >
                    <activity
                        android:name="com.android.tests.basic.Main"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity
                        android:name="com.google.android.gms.common.api.GoogleApiActivity"
                        android:exported="false"
                        android:theme="@android:style/Theme.Translucent.NoTitleBar" />

                    <meta-data
                        android:name="com.google.android.gms.version"
                        android:value="@integer/google_play_services_version" />
                </application>

            </manifest>
        """.trimIndent())
    }
}
