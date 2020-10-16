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

import com.android.build.api.variant.ExternalNdkBuild
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledExternalNdkBuildTest {
    @Mock
    lateinit var delegate: ExternalNdkBuild

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledExternalNdkBuild

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledExternalNdkBuild(delegate, stats)
    }

    @Test
    fun getAbiFilters() {
        @Suppress("UNCHECKED_CAST") val setProperty: SetProperty<String>
                = Mockito.mock(SetProperty::class.java) as SetProperty<String>
        Mockito.`when`(delegate.abiFilters).thenReturn(setProperty)
        Truth.assertThat(proxy.abiFilters).isEqualTo(setProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NDK_BUILD_OPTIONS_ABI_FILTERS_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).abiFilters
        Mockito.verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getTargets() {
        @Suppress("UNCHECKED_CAST") val setProperty: SetProperty<String>
                = Mockito.mock(SetProperty::class.java) as SetProperty<String>
        Mockito.`when`(delegate.targets).thenReturn(setProperty)
        Truth.assertThat(proxy.targets).isEqualTo(setProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NDK_BUILD_OPTIONS_TARGETS_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).targets
        Mockito.verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getArguments() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = Mockito.mock(ListProperty::class.java) as ListProperty<String>
        Mockito.`when`(delegate.arguments).thenReturn(listProperty)
        Truth.assertThat(proxy.arguments).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NDK_BUILD_OPTIONS_ARGUMENTS_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).arguments
        Mockito.verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getCFlags() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = Mockito.mock(ListProperty::class.java) as ListProperty<String>
        Mockito.`when`(delegate.cFlags).thenReturn(listProperty)
        Truth.assertThat(proxy.cFlags).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NDK_BUILD_OPTIONS_C_FLAGS_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).cFlags
        Mockito.verifyNoMoreInteractions(delegate)
    }

    @Test
    fun getCppFlags() {
        @Suppress("UNCHECKED_CAST") val listProperty: ListProperty<String>
                = Mockito.mock(ListProperty::class.java) as ListProperty<String>
        Mockito.`when`(delegate.cppFlags).thenReturn(listProperty)
        Truth.assertThat(proxy.cppFlags).isEqualTo(listProperty)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.NDK_BUILD_OPTIONS_CPP_FLAGS_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).cppFlags
        Mockito.verifyNoMoreInteractions(delegate)
    }
}
