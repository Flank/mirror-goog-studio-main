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

package com.android.build.gradle.tasks

import com.android.build.VariantOutput
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.`when`

import com.android.build.gradle.AndroidConfig
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.ide.FilterDataImpl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.FeatureVariantData
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.VariantType
import com.android.testutils.truth.MoreTruth
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Tests for the [GenerateSplitAbiRes] class
 */
class GenerateSplitAbiResTest {

    @get:Rule val temporaryFolder = TemporaryFolder()
    @Mock lateinit internal var mockedGlobalScope: GlobalScope
    @Mock lateinit internal var mockedVariantScope: VariantScope
    @Mock lateinit internal var mockedOutputScope: OutputScope
    @Mock lateinit internal var mockedAndroidBuilder: AndroidBuilder
    @Mock lateinit internal var mockedVariantConfiguration: GradleVariantConfiguration
    @Mock lateinit internal var mockedAndroidConfig: AndroidConfig
    @Mock lateinit internal var mockedSplits: Splits
    @Mock lateinit internal var mockedBuildType: CoreBuildType
    @Mock lateinit internal var mockedVariantData: FeatureVariantData
    @Mock lateinit internal var mockedAaptOptions: AaptOptions
    @Mock lateinit internal var mockedOutputFactory: OutputFactory

    internal var localProjectOptions = ProjectOptions(ImmutableMap.of<String, Any>())
    internal val apkData = OutputFactory.DefaultApkData(VariantOutput.OutputType.SPLIT,
            "x86",
            "app",
            "app",
            "dirName",
            "app.apk",
            ImmutableList.of(FilterDataImpl(VariantOutput.FilterType.ABI, "x86")))
    private var project: Project? = null

    @Before
    fun setUp() {

        MockitoAnnotations.initMocks(this)
        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()

        with(mockedGlobalScope) {
            `when`(androidBuilder).thenReturn(mockedAndroidBuilder)
            `when`(projectOptions).thenReturn(localProjectOptions)
            `when`(extension).thenReturn(mockedAndroidConfig)
            `when`(projectBaseName).thenReturn("featureA")
        }

        with(mockedVariantScope) {
            `when`(globalScope).thenReturn(mockedGlobalScope)
            `when`(variantData).thenReturn(mockedVariantData)
            `when`(variantConfiguration).thenReturn(mockedVariantConfiguration)
            `when`(outputScope).thenReturn(mockedOutputScope)
        }

        with(mockedAndroidConfig) {
            `when`(aaptOptions).thenReturn(mockedAaptOptions)
            `when`(splits).thenReturn(mockedSplits)
        }

        with(mockedVariantConfiguration) {
            `when`(buildType).thenReturn(mockedBuildType)
            `when`(applicationId).thenReturn("com.example.app")
        }

        with(mockedVariantData) {
            `when`(outputFactory).thenReturn(mockedOutputFactory)
            `when`(featureName).thenReturn("featureA")
        }

        `when`(mockedSplits.abiFilters).thenReturn(ImmutableSet.of("arm", "x86"))
        `when`(mockedBuildType.isDebuggable).thenReturn(true)
    }

    @Test
    fun testBaseFeatureConfiguration() {

        with(initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantType.FEATURE)
            `when`(mockedVariantScope.isBaseFeature).thenReturn(true)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
        }
    }

    @Test
    fun testNonBaseFeatureConfiguration() {

        with (initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantType.FEATURE)
            `when`(mockedVariantScope.isBaseFeature).thenReturn(false)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isEqualTo("featureA")
        }
    }

    @Test
    fun testNonFeatureConfiguration() {

        with(initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantType.LIBRARY)
            `when`(mockedVariantScope.isBaseFeature).thenReturn(false)
        }) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
        }
    }

    @Test
    fun testCommonConfiguration() {
        with(initTask()) {
            assertThat(applicationId).isEqualTo("com.example.app")
            assertThat(featureName).isNull()
            assertThat(outputBaseName).isEqualTo("base")
            assertThat(isDebuggable).isTrue()
        }
    }

    @Test
    fun testCommonManifestValues() {
        val generatedSplitManifest = initTask().generateSplitManifest("x86", apkData)
        MoreTruth.assertThat(generatedSplitManifest).exists()
        val content = Files.asCharSource(generatedSplitManifest, Charsets.UTF_8).toString()
        assertThat(content.contains("android:versionCode=1"))
        assertThat(content.contains("android:versionName=versionName"))
        assertThat(content.contains("package=com.example.app"))
        assertThat(content.contains("targetABI=x86"))
        assertThat(content.contains("split=\"featureA.comnfig.x86\""))
    }

    @Test
    fun testNonFeatureExecution() {

        val generatedSplitManifest = initTask().generateSplitManifest("x86", apkData)
        MoreTruth.assertThat(generatedSplitManifest).exists()
        MoreTruth.assertThat(generatedSplitManifest).doesNotContain("configForSplit")
    }

    @Test
    fun testBaseFeatureExecution() {

        val generatedSplitManifest = initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantType.FEATURE)
            `when`(mockedVariantScope.isBaseFeature).thenReturn(true)
        }.generateSplitManifest("x86", apkData)

        MoreTruth.assertThat(generatedSplitManifest).exists()
        MoreTruth.assertThat(generatedSplitManifest).doesNotContain("configForSplit")
    }

    @Test
    fun testNonBaseFeatureExecution() {

        val generatedSplitManifest = initTask {
            `when`(mockedVariantConfiguration.type).thenReturn(VariantType.FEATURE)
            `when`(mockedVariantScope.isBaseFeature).thenReturn(false)
        }.generateSplitManifest("x86", apkData)

        MoreTruth.assertThat(generatedSplitManifest).exists()
        MoreTruth.assertThat(generatedSplitManifest).contains("configForSplit=\"featureA\"")
    }

    private fun initTask(initializationLambda : (GenerateSplitAbiRes.ConfigAction) -> Unit = {}) : GenerateSplitAbiRes {
        val configAction = GenerateSplitAbiRes.ConfigAction(mockedVariantScope, temporaryFolder.newFolder())

        initCommonFields()
        initializationLambda(configAction)

        val task = project!!.tasks.create("test", GenerateSplitAbiRes::class.java)
        configAction.execute(task)
        return task
    }

    private fun initCommonFields() {
        `when`(mockedVariantScope.isBaseFeature).thenReturn(false)
        with(mockedVariantConfiguration) {
            `when`(type).thenReturn(VariantType.LIBRARY)
            `when`(fullName).thenReturn("debug")
            `when`(versionCode).thenReturn(1)
            `when`<String>(versionName).thenReturn("versionName")
            `when`(baseName).thenReturn("base")
        }
    }
}
