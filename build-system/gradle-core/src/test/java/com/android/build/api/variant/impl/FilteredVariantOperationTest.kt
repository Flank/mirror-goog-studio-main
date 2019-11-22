
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

import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.CoreBuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.junit.Test
import org.mockito.Mockito
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.test.fail

/**
 * Tests for [FilteredVariantOperation]
 */
class FilteredVariantOperationTest {

    @Test
    fun testStringNameMatching() {
        val atomicCounter = AtomicInteger(0)
        val operation = FilteredVariantOperation(
            specificType = Variant::class.java,
            variantName = "foo",
            action = Action {
                assertThat(it.name).isEqualTo("foo")
                atomicCounter.incrementAndGet()
            }
        )
        val variantScopes = listOf(createVariantScope("bar"),
            createVariantScope("foo"),
            createVariantScope("foobar"))
        operation.execute(VariantScopeTransformers.toVariant, variantScopes)
        assertThat(atomicCounter.get()).isEqualTo(1)
    }

    @Test
    fun testStringNameNotMatching() {
        val operation = FilteredVariantOperation(
            specificType = Variant::class.java,
            variantName = "fooX",
            action = Action {
                fail("Action invoked")
            }
        )
        val variantScopes = listOf(createVariantScope("bar"),
            createVariantScope("foo"),
            createVariantScope("foobar"))
        try {
            operation.execute(VariantScopeTransformers.toVariant, variantScopes)
        } catch(e: RuntimeException) {
            val message = checkNotNull(e.message)
            assertThat(message.contains("fooX"))
            assertThat(message.contains("bar,foo,foobar"))
        }
    }

    @Test
    fun testStringNamePatternMatching() {
        val atomicCounter = AtomicInteger(0)
        val operation = FilteredVariantOperation(
            specificType = Variant::class.java,
            variantNamePattern = Pattern.compile("foo.*"),
            action = Action {
                assertThat(it.name).startsWith("foo")
                atomicCounter.incrementAndGet()
            }
        )
        val variantScopes = listOf(createVariantScope("bar"),
            createVariantScope("foo"),
            createVariantScope("foobar"))
        operation.execute(VariantScopeTransformers.toVariant, variantScopes)
        assertThat(atomicCounter.get()).isEqualTo(2)
    }

    @Test
    fun testStringNamePatternNotMatching() {
        val operation = FilteredVariantOperation(
            specificType = Variant::class.java,
            variantNamePattern = Pattern.compile("fooX.*"),
            action = Action {
                fail("Action invoked")
            }
        )
        val variantScopes = listOf(createVariantScope("bar"),
            createVariantScope("foo"),
            createVariantScope("foobar"))
        try {
            operation.execute(VariantScopeTransformers.toVariant, variantScopes)
        } catch(e: RuntimeException) {
            val message = checkNotNull(e.message)
            assertThat(message.contains("fooX"))
            assertThat(message.contains("bar,foo,foobar"))
        }
    }

    @Test
    fun testBuildTypeMatching() {
        val atomicCounter = AtomicInteger(0)
        val operation = FilteredVariantOperation(
            specificType = Variant::class.java,
            buildType = "Debug",
            action = Action {
                assertThat(it.name).isEqualTo("foo")
                atomicCounter.incrementAndGet()
            }
        )
        val variantScopes = listOf(
            createVariantScope("bar", buildTypeName = "Release"),
            createVariantScope("foo", buildTypeName = "Debug"),
            createVariantScope("foobar", buildTypeName = "Other"))
        operation.execute(VariantScopeTransformers.toVariant, variantScopes)
        assertThat(atomicCounter.get()).isEqualTo(1)
    }

    @Test
    fun testBuildFlavorMatching() {
        val atomicCounter = AtomicInteger(0)
        val operations = FilteredVariantOperation(
            specificType = Variant::class.java,
            flavorToDimensionData = listOf("f1" to "dim1"),
            action = Action {
                assertThat(it.name).isAnyOf("foo", "bar")
                atomicCounter.incrementAndGet()
            }
        )
        val variantScopes = listOf(
            createVariantScope("bar", buildTypeName = "Release", flavorName = "f1"),
            createVariantScope("foo", buildTypeName = "Debug", flavorName = "f1"),
            createVariantScope("foobar", buildTypeName = "Other", flavorName = "f2"))
        operations.execute(VariantScopeTransformers.toVariant, variantScopes)
        assertThat(atomicCounter.get()).isEqualTo(2)
    }

    private fun createVariantScope(
        name: String,
        buildTypeName: String? = null,
        flavorName: String? = null): VariantScope {
        val variantScope = Mockito.mock(VariantScope::class.java)
        val variantConfiguration = Mockito.mock(GradleVariantConfiguration::class.java)
        val variantData = Mockito.mock(BaseVariantData::class.java)
        val publicVariantApi = Mockito.mock(VariantImpl::class.java)
        Mockito.`when`(variantScope.variantData).thenReturn(variantData)
        Mockito.`when`(variantScope.variantConfiguration).thenReturn(variantConfiguration)
        Mockito.`when`(variantScope.fullVariantName).thenReturn(name)
        Mockito.`when`(variantData.publicVariantApi).thenReturn(publicVariantApi)
        Mockito.`when`(publicVariantApi.name).thenReturn(name)
        if (buildTypeName != null) {
            val buildType = Mockito.mock(BuildType::class.java)
            Mockito.`when`(variantConfiguration.buildType).thenReturn(buildType)
            Mockito.`when`(buildType.name).thenReturn(buildTypeName)
        }
        val productFlavor = Mockito.mock(ProductFlavor::class.java)
        Mockito.`when`(productFlavor.dimension).thenReturn("dim1")
        Mockito.`when`(productFlavor.name).thenReturn(flavorName)
        if (flavorName != null) {
            Mockito.`when`(variantConfiguration.productFlavors).thenReturn(
                listOf(productFlavor)
            )
        }
        return variantScope
    }
}
