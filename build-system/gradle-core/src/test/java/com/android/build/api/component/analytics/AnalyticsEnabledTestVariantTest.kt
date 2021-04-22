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
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.JniLibsApkPackaging
import com.android.build.api.variant.Renderscript
import com.android.build.api.variant.ResourcesPackaging
import com.android.build.api.variant.TestVariant
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledTestVariantTest {
    @Mock
    lateinit var delegate: TestVariant

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledTestVariant

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledTestVariant(delegate, stats, FakeObjectFactory.factory)
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
    fun testedApplicationId() {
        Mockito.`when`(delegate.testedApplicationId).thenReturn(FakeGradleProvider("myApp"))
        Truth.assertThat(proxy.testedApplicationId.get()).isEqualTo("myApp")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.TESTED_APPLICATION_ID_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .testedApplicationId
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
}
