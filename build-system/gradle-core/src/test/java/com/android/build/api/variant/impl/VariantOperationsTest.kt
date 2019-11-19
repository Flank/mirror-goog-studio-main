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
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [VariantOperations]
 */
class VariantOperationsTest {

    @Test
    fun unfilteredScopesTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<AppVariant>(VariantScopeTransformers.toVariant)
        for (i in 1..5) {
            operations.actions.add(Action { _ -> counter.incrementAndGet()})
        }
        val variantScopes = listOf(Mockito.mock(VariantScope::class.java),
            Mockito.mock(VariantScope::class.java),
            Mockito.mock(VariantScope::class.java))

        variantScopes.forEach { variantScope ->
            val baseVariantData = Mockito.mock(BaseVariantData::class.java)
            Mockito.`when`(baseVariantData.publicVariantApi).thenReturn(
                Mockito.mock(AppVariantImpl::class.java))
            Mockito.`when`(variantScope.variantData).thenReturn(baseVariantData)
        }

        operations.executeOperations<AppVariant>(variantScopes)
        Truth.assertThat(counter.get()).isEqualTo(15) // 5x3
    }

    @Test
    fun singleFilteredScopesTest() {
        val atomicCounter = AtomicInteger(0)
        val variantOperation = VariantOperations<Variant<*>>(VariantScopeTransformers.toVariant)
        variantOperation.addFilteredAction(FilteredVariantOperation(
            specificType = Variant::class.java,
            action = Action { atomicCounter.incrementAndGet() })
        )
        variantOperation.executeOperations<Variant<VariantProperties>>(listOf(createVariantScope()))
        Truth.assertThat(atomicCounter.get()).isEqualTo(1)
    }

    @Test
    fun multipleFilteredScopesTest() {
        val atomicCounter = AtomicInteger(0)
        val variantOperation = VariantOperations<Variant<*>>(
            VariantScopeTransformers.toVariant)
        variantOperation.addFilteredAction(
            FilteredVariantOperation(
                specificType = Variant::class.java,
                action = Action { atomicCounter.incrementAndGet() }
            )
        )
        variantOperation.executeOperations<Variant<VariantProperties>>(listOf(createVariantScope(),
            createVariantScope(),
            createVariantScope()))
        Truth.assertThat(atomicCounter.get()).isEqualTo(3)
    }

    private fun createVariantScope(): VariantScope {
        val variantScope = Mockito.mock(VariantScope::class.java)
        val variantData = Mockito.mock(BaseVariantData::class.java)
        val publicVariantApi = Mockito.mock(VariantImpl::class.java)
        Mockito.`when`(variantScope.variantData).thenReturn(variantData)
        Mockito.`when`(variantData.publicVariantApi).thenReturn(publicVariantApi)
        return variantScope
    }
}