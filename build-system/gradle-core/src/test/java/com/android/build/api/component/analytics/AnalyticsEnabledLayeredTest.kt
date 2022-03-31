/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledLayeredTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: SourceDirectories.Layered

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledLayered by lazy {
        object: AnalyticsEnabledLayered(delegate, stats, FakeObjectFactory.factory) {}
    }

    @Test
    fun getAll() {
        val provider = Mockito.mock(Provider::class.java)
        @Suppress("UNCHECKED_CAST")
        Mockito.`when`(delegate.all).thenReturn(provider as Provider<List<Collection<Directory>>>?)

        val providerProxy = proxy.all
        Truth.assertThat(providerProxy).isEqualTo(provider)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SOURCES_AND_OVERLAY_DIRECTORIES_GET_ALL_VALUE)
        Mockito.verify(delegate, Mockito.times(1)).all
    }
}
