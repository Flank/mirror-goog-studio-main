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

import com.android.build.api.variant.AarMetadata
import com.android.build.api.variant.ResValue
import com.android.build.api.variant.TestFixtures
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledTestFixturesTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: TestFixtures

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledTestFixtures by lazy {
        AnalyticsEnabledTestFixtures(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun aarMetadata() {
        val aarMetadata = Mockito.mock(AarMetadata::class.java)
        Mockito.`when`(aarMetadata.minCompileSdk).thenReturn(FakeGradleProperty(5))
        Mockito.`when`(delegate.aarMetadata).thenReturn(aarMetadata)
        Truth.assertThat(proxy.aarMetadata.minCompileSdk.get()).isEqualTo(5)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_VALUE,
                VariantPropertiesMethodType.VARIANT_AAR_METADATA_MIN_COMPILE_SDK_VALUE,
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).aarMetadata
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
}
