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

import com.android.build.api.component.Component
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations

class AnalyticsEnabledComponentTest {

    @Mock
    lateinit var delegate: Component

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledComponent

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = object: AnalyticsEnabledComponent(delegate, stats, FakeObjectFactory.factory) {}
    }

    abstract class MockedVisitor : AsmClassVisitorFactory<InstrumentationParameters>

    @Test
    fun transformClasspathWith() {
        val scope = Mockito.mock(InstrumentationScope::class.java)
        val block = { _ : InstrumentationParameters  -> }
        proxy.transformClassesWith(
            MockedVisitor::class.java,
            scope,
            block
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ASM_TRANSFORM_CLASSES_VALUE)
        Mockito.verify(delegate, times(1))
            .transformClassesWith(MockedVisitor::class.java, scope, block)
    }

    @Test
    fun setAsmFramesComputationNode() {
        val mode = Mockito.mock(FramesComputationMode::class.java)
        proxy.setAsmFramesComputationMode(mode)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ASM_FRAMES_COMPUTATION_NODE_VALUE)
        Mockito.verify(delegate, times(1))
            .setAsmFramesComputationMode(mode)
    }

    @Test
    fun getBuildType() {
        Mockito.`when`(delegate.buildType).thenReturn("BuildTypeFoo")
        Truth.assertThat(proxy.buildType).isEqualTo("BuildTypeFoo")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUILD_TYPE_VALUE)
        Mockito.verify(delegate, times(1))
            .buildType
    }

    @Test
    fun getProductFlavors() {
        Mockito.`when`(delegate.productFlavors).thenReturn(listOf("foo" to "bar"))
        Truth.assertThat(proxy.productFlavors).isEqualTo(listOf("foo" to "bar"))

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.PRODUCT_FLAVORS_VALUE)
        Mockito.verify(delegate, times(1))
            .productFlavors
    }

    @Test
    fun getFlavorName() {
        Mockito.`when`(delegate.flavorName).thenReturn("flavorName")
        Truth.assertThat(proxy.flavorName).isEqualTo("flavorName")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.FLAVOR_NAME_VALUE)
        Mockito.verify(delegate, times(1))
            .flavorName
    }
}
