
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
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledVariantBuilderTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: AnalyticsEnabledVariantBuilder

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledVariantBuilder by lazy {
        object: AnalyticsEnabledVariantBuilder(delegate, stats) {}
    }

    @Test
    fun setMinSdkVersion() {
        proxy.minSdk = 23
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE)
        verify(delegate, times(1)).minSdk = 23
    }

    @Test
    fun setMinSdkVersionPreview() {
        proxy.minSdkPreview = "S"
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.MIN_SDK_PREVIEW_VALUE)
        verify(delegate, times(1)).minSdkPreview = "S"
    }

    @Test
    fun setMaxSdkVersion() {
        proxy.maxSdk = 23
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.MAX_SDK_VERSION_VALUE_VALUE)
        verify(delegate, times(1)).maxSdk = 23
    }

    @Test
    fun setTargetSdkVersion() {
        proxy.targetSdk = 23
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.TARGET_SDK_VERSION_VALUE_VALUE)
        verify(delegate, times(1)).targetSdk = 23
    }

    @Test
    fun setTargetSdkVersionPreview() {
        proxy.targetSdkPreview = "S"
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.TARGET_SDK_PREVIEW_VALUE)
        verify(delegate, times(1)).targetSdkPreview = "S"
    }
}
