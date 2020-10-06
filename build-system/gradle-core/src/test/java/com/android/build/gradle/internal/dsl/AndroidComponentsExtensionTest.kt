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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        testNoSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                ApplicationVariantBuilder::class.java)
    }

    @Test
    fun testLibraryModuleNoSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryVariantBuilder, LibraryVariant>()
        testNoSelection(
                LibraryAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                LibraryVariantBuilder::class.java)
    }

    @Test
    fun testDynamicFeatureModuleNoSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<DynamicFeatureVariantBuilder, DynamicFeatureVariant>()
        testNoSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar,
                ),
                variantApiOperationsRegistrar,
                DynamicFeatureVariantBuilder::class.java)
    }

    @Test
    fun testTestModuleNoSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestVariantBuilder, TestVariant>()
        testNoSelection(
                TestAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }

    @Test
    fun testApplicationModuleAllSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        testAllSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar),
                variantApiOperationsRegistrar,
                ApplicationVariantBuilder::class.java)
    }

    @Test
    fun testLibraryModuleAllSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryVariantBuilder, LibraryVariant>()
        testAllSelection(
                LibraryAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                LibraryVariantBuilder::class.java)
    }

    @Test
    fun testDynamicFeatureModuleAllSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<DynamicFeatureVariantBuilder, DynamicFeatureVariant>()
        testAllSelection(
                DynamicFeatureAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                DynamicFeatureVariantBuilder::class.java)
    }

    @Test
    fun testTestModuleAllSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestVariantBuilder, TestVariant>()
        testAllSelection(
                TestAndroidComponentsExtensionImpl(
                        dslServices,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }

    private fun  <VariantBuilderT: VariantBuilder, VariantT: Variant> testAllSelection(
            extension: AndroidComponentsExtensionImpl<VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants(extension.selector().all()) {
            visitedVariants.add(it)
        }
        @Suppress("UNCHECKED_CAST")
        operationsRegistrar.variantBuilderOperations.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }

    private fun <VariantBuilderT: VariantBuilder, VariantT: Variant> testNoSelection(
            extension: AndroidComponentsExtensionImpl<VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants { variant -> visitedVariants.add(variant)}
        operationsRegistrar.variantBuilderOperations.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }
}
