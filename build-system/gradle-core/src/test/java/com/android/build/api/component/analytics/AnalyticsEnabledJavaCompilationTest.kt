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
import com.android.build.api.variant.JavaCompilation
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.jetbrains.kotlin.gradle.utils.`is`
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AnalyticsEnabledJavaCompilationTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: JavaCompilation

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledJavaCompilation by lazy {
        AnalyticsEnabledJavaCompilation(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getAnnotationProcessor() {
        val annotationProcessor = Mockito.mock(AnnotationProcessor::class.java)
        Mockito.`when`(delegate.annotationProcessor).thenReturn(annotationProcessor)

        val annotationProcessorProxy = proxy.annotationProcessor
        Truth.assertThat(annotationProcessorProxy.javaClass)
            .`is`(AnalyticsEnabledAnnotationProcessor::class.java)
        Truth.assertThat((annotationProcessorProxy as AnalyticsEnabledAnnotationProcessor).delegate)
            .isEqualTo(annotationProcessor)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ANNOTATION_PROCESSOR_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .annotationProcessor
    }
}
