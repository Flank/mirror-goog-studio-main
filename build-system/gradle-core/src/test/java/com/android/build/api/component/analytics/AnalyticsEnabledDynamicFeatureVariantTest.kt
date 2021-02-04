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

import com.android.build.api.component.AndroidTest
import com.android.build.api.variant.Aapt
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFileProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledDynamicFeatureVariantTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    
    @Mock
    lateinit var delegate: DynamicFeatureVariant

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledDynamicFeatureVariant by lazy {
        AnalyticsEnabledDynamicFeatureVariant(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getAaptOptions() {
        val aaptOptions = Mockito.mock(Aapt::class.java)
        Mockito.`when`(delegate.aapt).thenReturn(aaptOptions)
        Truth.assertThat(proxy.aapt).isEqualTo(aaptOptions)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aapt
    }

    @Test
    fun aaptOptionsAction() {
        val function = { param : Aapt -> println(param) }
        proxy.aaptOptions(function)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_ACTION_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptOptions(function)
    }

    @Test
    fun getPackagingOptions() {
        val packagingOptions = Mockito.mock(ApkPackaging::class.java)
        val jniLibsApkPackagingOptions = Mockito.mock(JniLibsApkPackaging::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackaging::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsApkPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packaging).thenReturn(packagingOptions)
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
    fun packagingOptionsActions() {
        val packagingOptions = Mockito.mock(ApkPackaging::class.java)
        Mockito.`when`(delegate.packaging).thenReturn(packagingOptions)
        val action: ApkPackaging.() -> Unit = {
            this.jniLibs {}
            this.resources {}
        }
        proxy.packaging(action)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(3)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_ACTION_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).packaging
    }

    @Test
    fun androidTest() {
        val androidTest = Mockito.mock(AndroidTest::class.java)
        Mockito.`when`(androidTest.applicationId).thenReturn(FakeGradleProperty("appId"))
        Mockito.`when`(delegate.androidTest).thenReturn(androidTest)

        proxy.androidTest.let {
            Truth.assertThat(it?.applicationId?.get()).isEqualTo("appId")
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.ANDROID_TEST_VALUE,
                VariantPropertiesMethodType.APPLICATION_ID_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).androidTest
    }

    @Test
    fun getDexingConfig() {
        val dexing = Mockito.mock(Dexing::class.java)
        val multiDexKeepFile = Mockito.mock(RegularFileProperty::class.java)
        Mockito.`when`(dexing.multiDexKeepFile).thenReturn(multiDexKeepFile)
        val multiDexKeepProguard = Mockito.mock(RegularFileProperty::class.java)
        Mockito.`when`(dexing.multiDexKeepProguard).thenReturn(multiDexKeepProguard)
        Mockito.`when`(delegate.dexing).thenReturn(dexing)

        proxy.dexing.let {
            Truth.assertThat(it.multiDexKeepFile).isEqualTo(multiDexKeepFile)
            Truth.assertThat(it.multiDexKeepProguard).isEqualTo(multiDexKeepProguard)
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(3)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.DEXING_VALUE,
                VariantPropertiesMethodType.MULTI_DEX_KEEP_FILE_VALUE,
                VariantPropertiesMethodType.MULTI_DEX_KEEP_PROGUARD_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).dexing
    }

    @Test
    fun dexingAction() {
        val function = { param : Dexing -> println(param) }
        val dexing = Mockito.mock(Dexing::class.java)
        Mockito.`when`(delegate.dexing).thenReturn(dexing)
        proxy.dexing(function)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEXING_ACTION_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).dexing
    }
}
