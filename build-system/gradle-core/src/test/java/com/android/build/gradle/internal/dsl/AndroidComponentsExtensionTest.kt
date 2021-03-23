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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.extension.impl.*
import com.android.build.api.variant.*
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.regex.Pattern

class AndroidComponentsExtensionTest {
    private lateinit var dslServices: DslServices
    private lateinit var sdkComponents: SdkComponents

    @Before
    fun setUp() {
        val sdkComponentsBuildService = Mockito.mock(SdkComponentsBuildService::class.java)
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponentsBuildService))
        sdkComponents = Mockito.mock(SdkComponents::class.java)
    }

    @Test
    fun testSdkComponents() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()

        val sdkComponentsFromComponents = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            variantApiOperationsRegistrar
        ).sdkComponents
        assertThat(sdkComponentsFromComponents).isSameInstanceAs(sdkComponents)
    }

    @Test
    fun testApplicationModuleNoSelection() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        testNoSelection(
                ApplicationAndroidComponentsExtensionImpl(
                        dslServices,
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
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
                        sdkComponents,
                        variantApiOperationsRegistrar
                ),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }


    @Test
    fun testBeforeVariants() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar)
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))
        appExtension.beforeVariants {
                    it.minSdkVersion = AndroidVersion(23)
        }

        appExtension.beforeVariants(fooVariant) {
                    it.enabled = false
        }
    }

    @Test
    fun testOnVariantsProperties() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar)
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))

        appExtension.onVariants(fooVariant, Action { it.artifacts.get(ArtifactType.APK) })
        val f1Variant = appExtension.selector().withFlavor("f1" to "dim1")
        appExtension.onVariants(f1Variant, Action { it.artifacts.get(ArtifactType.APK) })

        val debugF1Variant = appExtension.selector()
                .withBuildType("debug")
                .withFlavor("f1" to "dim1")
        appExtension.onVariants(debugF1Variant) {
                    it.artifacts.get(ArtifactType.APK)
        }
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
