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
    fun multipleActionsTest() {
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
}