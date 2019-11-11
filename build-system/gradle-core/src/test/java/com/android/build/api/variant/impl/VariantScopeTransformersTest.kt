/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.lang.ClassCastException

/**
 * Tests for [VariantScopeTransformers]
 */
class VariantScopeTransformersTest {

    @Mock
    lateinit var variantScope: VariantScope

    @Mock
    lateinit var variantData: BaseVariantData

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(variantScope.variantData).thenReturn(variantData)
    }

    @Test
    fun testLegitimateVariantCasting() {
        val publicVariantApi = Mockito.mock(AppVariantImpl::class.java)
        Mockito.`when`(variantData.publicVariantApi).thenReturn(publicVariantApi)

        val variantApi =
            VariantScopeTransformers.toVariant.transform(variantScope, AppVariant::class.java)

        Truth.assertThat(variantApi).isNotNull()
        Truth.assertThat(variantApi).isInstanceOf(AppVariant::class.java)
    }

    @Test
    fun tesIllegitimateVariantCasting() {
        val publicVariantApi = Mockito.mock(AppVariantImpl::class.java)
        Mockito.`when`(variantData.publicVariantApi).thenReturn(publicVariantApi)
        assertThat(VariantScopeTransformers.toVariant.transform(variantScope, LibraryVariant::class.java)).isNull()
    }

    @Test
    fun testLegitimateVariantPropertiesCasting() {
        val publicVariantPropertiesApi = Mockito.mock(AppVariantPropertiesImpl::class.java)
        Mockito.`when`(variantData.publicVariantPropertiesApi).thenReturn(publicVariantPropertiesApi)

        val variantPropertiesApi =
            VariantScopeTransformers.toVariantProperties.transform(variantScope, AppVariantPropertiesImpl::class.java)

        Truth.assertThat(variantPropertiesApi).isNotNull()
        Truth.assertThat(variantPropertiesApi).isInstanceOf(AppVariantProperties::class.java)
    }

    @Test
    fun tesIllegitimateVariantPropertiesCasting() {
        val publicVariantPropertiesApi = Mockito.mock(AppVariantPropertiesImpl::class.java)
        Mockito.`when`(variantData.publicVariantPropertiesApi).thenReturn(publicVariantPropertiesApi)
        assertThat(VariantScopeTransformers.toVariantProperties.transform(variantScope, LibraryVariantProperties::class.java)).isNull()
    }
}