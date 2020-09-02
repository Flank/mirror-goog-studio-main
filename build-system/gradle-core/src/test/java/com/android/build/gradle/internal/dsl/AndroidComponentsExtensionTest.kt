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

package com.android.build.gradle.internal.dsl

import com.android.build.api.extension.impl.*
import com.android.build.api.variant.*
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class AndroidComponentsExtensionTest {
    private lateinit var dslServices: DslServices
    @Before
    fun setUp() {
        val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
        Mockito.`when`(sdkComponents.adbExecutableProvider).thenReturn(FakeGradleProvider(null))
        Mockito.`when`(sdkComponents.ndkDirectoryProvider).thenReturn(FakeGradleProvider(null))
        Mockito.`when`(sdkComponents.sdkDirectoryProvider).thenReturn(FakeGradleProvider(null))

        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))
    }

    @Test
    fun testApplicationModuleNoSelection() {
        val operationsRegistrar = OperationsRegistrar<ApplicationVariant>()
        @Suppress("UNCHECKED_CAST")
        testNoSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        operationsRegistrar
                ),
                operationsRegistrar,
                ApplicationVariant::class.java)
    }

    @Test
    fun testLibraryModuleNoSelection() {
        val operationsRegistrar = OperationsRegistrar<LibraryVariant>()
        @Suppress("UNCHECKED_CAST")
        testNoSelection(
                LibraryAndroidComponentsExtensionImpl(
                        dslServices,
                        operationsRegistrar
                ),
                operationsRegistrar,
                LibraryVariant::class.java)
    }

    @Test
    fun testDynamicFeatureModuleNoSelection() {
        val operationsRegistrar = OperationsRegistrar<DynamicFeatureVariant>()
        @Suppress("UNCHECKED_CAST")
        testNoSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                DynamicFeatureVariant::class.java)
    }

    @Test
    fun testTestModuleNoSelection() {
        val operationsRegistrar = OperationsRegistrar<TestVariant>()
        @Suppress("UNCHECKED_CAST")
        testNoSelection(
                TestAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                TestVariant::class.java)
    }

    @Test
    fun testApplicationModuleAllSelection() {
        val operationsRegistrar = OperationsRegistrar<ApplicationVariant>()
        @Suppress("UNCHECKED_CAST")
        testAllSelection(
                ApplicationAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                ApplicationVariant::class.java)
    }

    @Test
    fun testLibraryModuleAllSelection() {
        val operationsRegistrar = OperationsRegistrar<LibraryVariant>()
        @Suppress("UNCHECKED_CAST")
        testAllSelection(
                LibraryAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                LibraryVariant::class.java)
    }

    @Test
    fun testDynamicFeatureModuleAllSelection() {
        val operationsRegistrar = OperationsRegistrar<DynamicFeatureVariant>()
        @Suppress("UNCHECKED_CAST")
        testAllSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                DynamicFeatureVariant::class.java)
    }

    @Test
    fun testTestModuleAllSelection() {
        val operationsRegistrar = OperationsRegistrar<TestVariant>()
        @Suppress("UNCHECKED_CAST")
        testAllSelection(
                TestAndroidComponentsExtensionImpl(dslServices, operationsRegistrar),
                operationsRegistrar,
                TestVariant::class.java)
    }

    private fun  <VariantT: Variant> testAllSelection(
            extension: AndroidComponentsExtensionImpl<VariantT>,
            operationsRegistrar: OperationsRegistrar<VariantT>,
            variantType: Class<VariantT>) {
        val visitedVariants = mutableListOf<VariantT>()
        extension.beforeVariants(extension.selector().all()) {
            visitedVariants.add(this)
        }
        @Suppress("UNCHECKED_CAST")
        operationsRegistrar.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }

    private fun <VariantT: Variant> testNoSelection(
            extension: AndroidComponentsExtensionImpl<VariantT>,
            operationsRegistrar: OperationsRegistrar<VariantT>,
            variantType: Class<VariantT>) {
        val visitedVariants = mutableListOf<VariantT>()
        extension.beforeVariants { visitedVariants.add(this)}
        operationsRegistrar.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }
}
