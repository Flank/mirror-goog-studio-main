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

import com.android.build.api.variant.AnnotationProcessor
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.process.CommandLineArgumentProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AnalyticsEnabledAnnotationProcessorTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: AnnotationProcessor

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledAnnotationProcessor by lazy {
        AnalyticsEnabledAnnotationProcessor(delegate, stats)
    }

    @Test
    fun getClassNames() {
        @Suppress("UNCHECKED_CAST")
        val list = Mockito.mock(ListProperty::class.java) as ListProperty<String>

        Mockito.`when`(delegate.classNames).thenReturn(list)
        Truth.assertThat(proxy.classNames).isEqualTo(list)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ANNOTATION_PROCESSOR_CLASS_NAMES_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .classNames
    }

    @Test
    fun getArguments() {
        @Suppress("UNCHECKED_CAST")
        val map = Mockito.mock(MapProperty::class.java) as MapProperty<String, String>

        Mockito.`when`(delegate.arguments).thenReturn(map)
        Truth.assertThat(proxy.arguments).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ANNOTATION_PROCESSOR_ARGUMENTS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .arguments
    }

    @Test
    fun getArgumentProviders() {
        @Suppress("UNCHECKED_CAST")
        val list = mutableListOf<CommandLineArgumentProvider>()

        Mockito.`when`(delegate.argumentProviders).thenReturn(list)
        Truth.assertThat(proxy.argumentProviders).isEqualTo(list)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ANNOTATION_PROCESSOR_ARGUMENT_PROVIDERS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .argumentProviders
    }
}
