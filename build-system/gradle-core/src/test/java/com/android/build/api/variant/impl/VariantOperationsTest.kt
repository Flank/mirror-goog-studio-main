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

import com.android.build.api.component.impl.FilteredComponentAction
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * Tests for [VariantOperations]
 */
class VariantOperationsTest {

    @Test
    fun unfilteredActionsTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<ApplicationVariant>()
        for (i in 1..5) {
            operations.actions.add(Action { counter.getAndAdd(10.0.pow(i - 1).toInt())})
        }
        val variant = createVariant(ApplicationVariant::class.java)

        operations.executeActions(variant)
        Truth.assertThat(counter.get()).isEqualTo(11111)
    }

    @Test
    fun singleFilteredActionTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<ApplicationVariant>()
        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { counter.incrementAndGet() })
        )

        val variant = createVariant(ApplicationVariant::class.java)
        operations.executeActions(variant)
        Truth.assertThat(counter.get()).isEqualTo(1)
    }

    @Test
    fun multipleActionsTest() {
        val counter = AtomicInteger(0)
        val operations = VariantOperations<Variant<*>>()

        operations.actions.add(Action { counter.incrementAndGet()})

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = LibraryVariant::class.java,
                action = Action { counter.getAndAdd(10) }
            )
        )

        operations.addFilteredAction(
            FilteredComponentAction(
                specificType = ApplicationVariant::class.java,
                action = Action { counter.getAndAdd(100) }
            )
        )

        val variant = createVariant(ApplicationVariant::class.java)
        operations.executeActions(variant)

        Truth.assertThat(counter.get()).isEqualTo(101)
    }

    private fun <T: Variant<*>> createVariant(
        @Suppress("UNCHECKED_CAST") variantClass: Class<T> = Variant::class.java as Class<T>
    ): T {
        val variant = Mockito.mock(variantClass)
        return variant
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