/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.SigningConfig
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledSigningConfigTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryDirectory= TemporaryFolder()

    @Mock
    lateinit var delegate: SigningConfig

    private val stats = GradleBuildVariant.newBuilder()

    private val proxy: AnalyticsEnabledSigningConfig by lazy {
        AnalyticsEnabledSigningConfig(delegate, stats)
    }

    @Test
    fun enableV1Signing() {
        Mockito.`when`(delegate.enableV1Signing).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.enableV1Signing.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V1_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).enableV1Signing
    }

    @Test
    fun enableV2Signing() {
        Mockito.`when`(delegate.enableV2Signing).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.enableV2Signing.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V2_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).enableV2Signing
    }

    @Test
    fun enableV3Signing() {
        Mockito.`when`(delegate.enableV3Signing).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.enableV3Signing.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V3_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).enableV3Signing
    }

    @Test
    fun enableV4Signing() {
        Mockito.`when`(delegate.enableV4Signing).thenReturn(FakeGradleProperty(true))
        Truth.assertThat(proxy.enableV4Signing.get()).isEqualTo(true)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SIGNING_CONFIG_ENABLE_V4_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).enableV4Signing
    }
}
