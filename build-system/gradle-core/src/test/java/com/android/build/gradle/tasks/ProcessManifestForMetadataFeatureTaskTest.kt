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
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.utils.PositionXmlParser
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
import javax.inject.Inject

class ProcessManifestForMetadataFeatureTaskTest {

    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var mainSplit: VariantOutputImpl

    @Mock
    lateinit var variantOutputConfiguration: VariantOutputConfigurationImpl

    private lateinit var task: ProcessManifestForMetadataFeatureTask
    private lateinit var sourceManifestFolder: File
    private lateinit var workerExecutor: WorkerExecutor

    abstract class ProcessManifestForMetadataFeatureTaskForTest @Inject constructor(
        testWorkerExecutor: WorkerExecutor
    ) : ProcessManifestForMetadataFeatureTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "testManifestForMetadataFeature",
            ProcessManifestForMetadataFeatureTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        sourceManifestFolder = temporaryFolder.newFolder("source_manifest")
        Mockito.`when`(mainSplit.variantOutputConfiguration).thenReturn(variantOutputConfiguration)
        Mockito.`when`(variantOutputConfiguration.outputType).thenReturn(
            VariantOutputConfiguration.OutputType.SINGLE)
        Mockito.`when`(variantOutputConfiguration.filters).thenReturn(listOf())
    }

    @Test
    fun testNonDynamicFeatureModule() {

        val sourceManifest = File(sourceManifestFolder, "AndroidManifest.xml")
        sourceManifest.writeText("Some content")
        task.dynamicFeature.set(false)
        task.bundleManifest.set(sourceManifest)
        task.metadataFeatureManifest.set(temporaryFolder.newFile("output_manifest.xml"))
        task.taskAction()

        Truth.assertThat(task.metadataFeatureManifest.get().asFile.readText(Charsets.UTF_8))
            .isEqualTo("Some content")
    }

    @Test
    @Throws(Exception::class)
    fun testFeatureMetadataManifestForDynamicModule() {
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

        task.dynamicFeature.set(true)
        task.bundleManifest.set(sourceManifest)
        task.metadataFeatureManifest.set(temporaryFolder.newFile("output_manifest.xml"))
        task.taskAction()

        val xmlDocument =
            PositionXmlParser.parse(task.metadataFeatureManifest.get().asFile.readText(Charsets.UTF_8))

        Truth.assertThat(xmlDocument.documentElement.getAttribute(SdkConstants.ATTR_FEATURE_SPLIT))
            .isEqualTo("feature")

        Truth.assertThat(
            xmlDocument
                .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                .item(0)
                .attributes
                .getNamedItemNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_SPLIT_NAME
                )
                .nodeValue
        ).isEqualTo("feature")

        // make sure we kept target sdk version
        Truth.assertThat(
            xmlDocument
                .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                .item(0)
                .attributes
                .getNamedItemNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_TARGET_SDK_VERSION
                )
                .nodeValue
        ).isEqualTo("27")

        // but removed the min sdk version.
        Truth.assertThat(
            xmlDocument
                .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                .item(0)
                .attributes
                .getNamedItemNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_MIN_SDK_VERSION
                )).isNull()

        Truth.assertThat(
            xmlDocument
                .getElementsByTagName(SdkConstants.TAG_USES_SPLIT).length).isEqualTo(0)
    }

    @Test
    @Throws(Exception::class)
    fun testFeatureMetadataManifestForNonDynamicModule() {
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

        task.dynamicFeature.set(false)
        task.bundleManifest.set(sourceManifest)
        task.metadataFeatureManifest.set(temporaryFolder.newFile("output_manifest.xml"))
        task.taskAction()

        val xmlDocument =
            PositionXmlParser.parse(task.metadataFeatureManifest.get().asFile.readText(Charsets.UTF_8))

        // make sure we kept min sdk version since this is not a dynamic module
        Truth.assertThat(
            xmlDocument
                .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                .item(0)
                .attributes
                .getNamedItemNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_MIN_SDK_VERSION
                )
                .nodeValue
        ).isEqualTo("22")

    }
}