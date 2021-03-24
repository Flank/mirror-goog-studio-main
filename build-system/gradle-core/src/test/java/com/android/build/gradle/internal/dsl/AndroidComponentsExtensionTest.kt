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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestExtension
import com.android.build.api.extension.DslExtension
import com.android.build.api.extension.impl.*
import com.android.build.api.variant.*
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.plugins.ExtensionContainer
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.regex.Pattern

class AndroidComponentsExtensionTest {
    private lateinit var dslServices: DslServices
    private lateinit var sdkComponents: SdkComponents
    private lateinit var applicationExtension: ApplicationExtension<*, *, *, *, *>

    @Before
    fun setUp() {
        val sdkComponentsBuildService = Mockito.mock(SdkComponentsBuildService::class.java)
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponentsBuildService))
        sdkComponents = Mockito.mock(SdkComponents::class.java)
        applicationExtension = Mockito.mock(ApplicationExtension::class.java)
    }

    @Test
    fun testSdkComponents() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()

        val sdkComponentsFromComponents = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            variantApiOperationsRegistrar,
            Mockito.mock(ApplicationExtension::class.java)
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(ApplicationExtension::class.java)
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(LibraryExtension::class.java)
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
                        Mockito.mock(DynamicFeatureExtension::class.java)
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(TestExtension::class.java)
                ),
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(ApplicationExtension::class.java)
                ),
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(LibraryExtension::class.java)
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(DynamicFeatureExtension::class.java)
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
                        variantApiOperationsRegistrar,
                        Mockito.mock(TestExtension::class.java)
                ),
                variantApiOperationsRegistrar,
                TestVariantBuilder::class.java)
    }


    @Test
    fun testBeforeVariants() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            Mockito.mock(ApplicationExtension::class.java)
        )
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
            variantApiOperationsRegistrar,
            Mockito.mock(ApplicationExtension::class.java)
        )
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))

        appExtension.onVariants(fooVariant, Action { it.artifacts.get(SingleArtifact.APK) })
        val f1Variant = appExtension.selector().withFlavor("f1" to "dim1")
        appExtension.onVariants(f1Variant, Action { it.artifacts.get(SingleArtifact.APK) })

        val debugF1Variant = appExtension.selector()
                .withBuildType("debug")
                .withFlavor("f1" to "dim1")
        appExtension.onVariants(debugF1Variant) {
                    it.artifacts.get(SingleArtifact.APK)
        }
    }

    interface DslExtensionType
    interface VariantExtensionType: VariantExtension

    @Test
    fun testRegisterBuildTypeExtension() {

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val extension = Mockito.mock(ApplicationExtension::class.java)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            extension
        )
        val extensionContainer = createExtensionAwareBuildType(extension)
        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .build()) {
                    object: VariantExtensionType {}
        }
        Mockito.verify(extensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testRegisterProductFlavorExtension() {

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val extension = Mockito.mock(ApplicationExtension::class.java)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            extension
        )
        val extensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        Mockito.verify(extensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testRegisterMultipleExtension() {

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val extension = Mockito.mock(ApplicationExtension::class.java)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            extension
        )

        val buildTypeExtensionContainer = createExtensionAwareBuildType(extension)
        val productFlavorExtensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("extension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        Mockito.verify(buildTypeExtensionContainer).add("extension", DslExtensionType::class.java)
        Mockito.verify(productFlavorExtensionContainer).add("extension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(1)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("extension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    @Test
    fun testMultipleRegisterExtension() {

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationVariantBuilder, ApplicationVariant>()
        val extension = Mockito.mock(ApplicationExtension::class.java)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            extension
        )

        val buildTypeExtensionContainer = createExtensionAwareBuildType(extension)
        val productFlavorExtensionContainer = createExtensionAwareProductFlavor(extension)

        appExtension.registerExtension(
            DslExtension.Builder("buildTypeExtension")
                .extendBuildTypeWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        appExtension.registerExtension(
            DslExtension.Builder("productFlavorExtension")
                .extendProductFlavorWith(DslExtensionType::class.java)
                .build()) {
            object: VariantExtensionType {}
        }
        Mockito.verify(buildTypeExtensionContainer).add("buildTypeExtension", DslExtensionType::class.java)
        Mockito.verify(productFlavorExtensionContainer).add("productFlavorExtension", DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions).hasSize(2)
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.dslName)
            .isEqualTo("buildTypeExtension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[0].dslExtensionTypes.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        assertThat(variantApiOperationsRegistrar.dslExtensions[1].dslExtensionTypes.dslName)
            .isEqualTo("productFlavorExtension")
        assertThat(variantApiOperationsRegistrar.dslExtensions[1].dslExtensionTypes.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
    }

    private fun createExtensionAwareBuildType(extension: ApplicationExtension<*, *, *, *, *>): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val buildTypesContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<com.android.build.api.dsl.ApplicationBuildType<*>>
        Mockito.`when`(extension.buildTypes).thenReturn(buildTypesContainer)
        val buildTypeExtensionContainer= Mockito.mock(ExtensionContainer::class.java)
        val buildType = Mockito.mock(com.android.build.api.dsl.BuildType::class.java)
        Mockito.`when`(buildType.extensions).thenReturn(buildTypeExtensionContainer)
        val buildTypes = listOf<com.android.build.api.dsl.BuildType>(buildType)
        Mockito.`when`(buildTypesContainer.iterator()).thenAnswer { buildTypes.iterator() }
        return buildTypeExtensionContainer
    }

    private fun createExtensionAwareProductFlavor(extension: ApplicationExtension<*, *, *, *, *>): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val productFlavorContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<com.android.build.api.dsl.ApplicationProductFlavor<*>>
        Mockito.`when`(extension.productFlavors).thenReturn(productFlavorContainer)
        val extensionContainer= Mockito.mock(ExtensionContainer::class.java)
        val productFlavor = Mockito.mock(com.android.build.api.dsl.ProductFlavor::class.java)
        Mockito.`when`(productFlavor.extensions).thenReturn(extensionContainer)
        val productFlavors = listOf<com.android.build.api.dsl.ProductFlavor>(productFlavor)
        Mockito.`when`(productFlavorContainer.iterator()).thenAnswer { productFlavors.iterator() }
        return extensionContainer
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
