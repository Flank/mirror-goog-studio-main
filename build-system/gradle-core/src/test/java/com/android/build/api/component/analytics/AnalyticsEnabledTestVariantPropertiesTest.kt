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

import com.android.build.api.variant.AaptOptions
import com.android.build.api.variant.TestVariantProperties
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

class AnalyticsEnabledTestVariantPropertiesTest {
    @Mock
    lateinit var delegate: TestVariantProperties

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledTestVariantProperties

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledTestVariantProperties(delegate, stats, FakeObjectFactory.factory)
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
    fun getAaptOptions() {
        val aaptOptions = Mockito.mock(AaptOptions::class.java)
        Mockito.`when`(delegate.aaptOptions).thenReturn(aaptOptions)
        Truth.assertThat(proxy.aaptOptions).isEqualTo(aaptOptions)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptOptions
    }

    @Test
    fun aaptOptionsAction() {
        val function = { param : AaptOptions -> println(param) }
        proxy.aaptOptions(function)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.AAPT_OPTIONS_ACTION_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .aaptOptions(function)
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
}