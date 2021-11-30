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
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.TestFixtures
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
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
    fun testFixtures() {
        val testFixtures = Mockito.mock(TestFixtures::class.java)
        Mockito.`when`(testFixtures.pseudoLocalesEnabled).thenReturn(FakeGradleProperty(false))
        Mockito.`when`(delegate.testFixtures).thenReturn(testFixtures)

        proxy.testFixtures.let {
            Truth.assertThat(it?.pseudoLocalesEnabled?.get()).isEqualTo(false)
        }

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.TEST_FIXTURES_VALUE,
                VariantPropertiesMethodType.VARIANT_PSEUDOLOCALES_ENABLED_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).testFixtures
    }
}
