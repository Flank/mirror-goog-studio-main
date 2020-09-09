
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
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.Variant
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class AnalyticsEnabledVariantBuilderTest {
    @Mock
    lateinit var delegate: AnalyticsEnabledVariantBuilder
    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledVariantBuilder
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = object: AnalyticsEnabledVariantBuilder(delegate, stats) {}
    }
    @Test
    fun setMinSdkVersion() {
        val newAndroidVersion = AndroidVersion(23)
        proxy.minSdkVersion = newAndroidVersion
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE)
        verify(delegate, times(1)).minSdkVersion = newAndroidVersion
    }

    @Test
    fun setMaxSdkVersion() {
        proxy.maxSdkVersion = 23
        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.MAX_SDK_VERSION_VALUE_VALUE)
        verify(delegate, times(1)).maxSdkVersion = 23
    }
}
