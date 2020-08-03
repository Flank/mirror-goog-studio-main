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
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.SigningConfig
import com.android.build.api.variant.VariantOutput
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledApplicationVariantPropertiesTest {
    @Mock
    lateinit var delegate: ApplicationVariantProperties

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledApplicationVariantProperties

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledApplicationVariantProperties(delegate, stats)
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
    fun getOutputs() {
        Mockito.`when`(delegate.outputs).thenReturn(listOf())
        Truth.assertThat(proxy.outputs).isEqualTo(listOf<VariantOutput>())

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_OUTPUTS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .outputs
    }

    @Test
    fun getDependenciesInfo() {
        val dependenciesInfo = Mockito.mock(DependenciesInfo::class.java)
        Mockito.`when`(delegate.dependenciesInfo).thenReturn(dependenciesInfo)
        Truth.assertThat(proxy.dependenciesInfo).isEqualTo(dependenciesInfo)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.DEPENDENCIES_INFO_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .dependenciesInfo
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
    fun signingConfigAction() {
        val function = { param : SigningConfig -> println(param) }
        proxy.signingConfig(function)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_ACTION_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .signingConfig(function)
    }
}