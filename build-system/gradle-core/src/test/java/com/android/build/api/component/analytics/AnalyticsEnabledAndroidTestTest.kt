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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AndroidResources
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.SigningConfig
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.Serializable

class AnalyticsEnabledAndroidTestTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: AndroidTest

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledAndroidTest by lazy {
        AnalyticsEnabledAndroidTest(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getApplicationId() {
        Mockito.`when`(delegate.applicationId).thenReturn(FakeGradleProperty("myApp"))
        Truth.assertThat(proxy.applicationId.get()).isEqualTo("myApp")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.APPLICATION_ID_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .applicationId
    }

    @Test
    fun getAndroidResources() {
        val androidResources = Mockito.mock(AndroidResources::class.java)
        Mockito.`when`(delegate.androidResources).thenReturn(androidResources)
        Truth.assertThat(proxy.androidResources).isEqualTo(androidResources)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .androidResources
    }

    @Test
    fun getPackageName() {
        Mockito.`when`(delegate.namespace).thenReturn(FakeGradleProvider("package.name"))
        Truth.assertThat(proxy.namespace.get()).isEqualTo("package.name")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NAMESPACE_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .namespace
    }

    @Test
    fun instrumentationRunner() {
        Mockito.`when`(delegate.instrumentationRunner).thenReturn(FakeGradleProperty("my_runner"))
        Truth.assertThat(proxy.instrumentationRunner.get()).isEqualTo("my_runner")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.INSTRUMENTATION_RUNNER_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .instrumentationRunner
    }

    @Test
    fun handleProfiling() {
        Mockito.`when`(delegate.handleProfiling).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.handleProfiling.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.HANDLE_PROFILING_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .handleProfiling
    }

    @Test
    fun functionalTest() {
        Mockito.`when`(delegate.functionalTest).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.functionalTest.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.FUNCTIONAL_TEST_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .functionalTest
    }

    @Test
    fun testLabel() {
        Mockito.`when`(delegate.testLabel).thenReturn(FakeGradleProperty("some_label"))
        Truth.assertThat(proxy.testLabel.get()).isEqualTo("some_label")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.TEST_LABEL_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .testLabel
    }

    @Test
    fun getBuildConfigFields() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, BuildConfigField<out Serializable>> =
            Mockito.mock(MapProperty::class.java)
                    as MapProperty<String, BuildConfigField<out Serializable>>
        Mockito.`when`(delegate.buildConfigFields).thenReturn(map)
        Truth.assertThat(proxy.buildConfigFields).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .buildConfigFields
    }


    @Test
    fun getResValues() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<ResValue.Key, ResValue> =
            Mockito.mock(MapProperty::class.java)
                    as MapProperty<ResValue.Key, ResValue>
        Mockito.`when`(delegate.resValues).thenReturn(map)
        Truth.assertThat(proxy.resValues).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.RES_VALUE_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .resValues
    }

    @Test
    fun getManifestPlaceholders() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, String> =
            Mockito.mock(MapProperty::class.java)
                    as MapProperty<String, String>
        Mockito.`when`(delegate.manifestPlaceholders).thenReturn(map)
        Truth.assertThat(proxy.manifestPlaceholders).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.MANIFEST_PLACEHOLDERS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .manifestPlaceholders
    }

    @Test
    fun getSigningConfig() {
        val signingConfig = Mockito.mock(SigningConfig::class.java)
        Mockito.`when`(delegate.signingConfig).thenReturn(signingConfig)
        Truth.assertThat(proxy.signingConfig).isEqualTo(signingConfig)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .signingConfig
    }


    @Test
    fun getRenderscript() {
        val renderscript = Mockito.mock(Renderscript::class.java)
        Mockito.`when`(delegate.renderscript).thenReturn(renderscript)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.renderscript

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.RENDERSCRIPT_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).renderscript
    }

    @Test
    fun getApkPackaging() {
        val apkPackaging = Mockito.mock(ApkPackaging::class.java)
        val jniLibsApkPackagingOptions = Mockito.mock(JniLibsApkPackaging::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackaging::class.java)
        Mockito.`when`(apkPackaging.jniLibs).thenReturn(jniLibsApkPackagingOptions)
        Mockito.`when`(apkPackaging.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packaging).thenReturn(apkPackaging)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.packaging.jniLibs
        proxy.packaging.resources

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
                listOf(
                        VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                        VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_VALUE,
                        VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                        VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_VALUE
                )
        )
        Mockito.verify(delegate, Mockito.times(1)).packaging
    }

    @Test
    fun getProguardFiles() {
        @Suppress("UNCHECKED_CAST")
        val proguardFiles = Mockito.mock(ListProperty::class.java) as ListProperty<RegularFile>
        Mockito.`when`(delegate.proguardFiles).thenReturn(proguardFiles)

        Truth.assertThat(proxy.proguardFiles).isEqualTo(proguardFiles)
        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.PROGUARD_FILES_VALUE)
    }
}
