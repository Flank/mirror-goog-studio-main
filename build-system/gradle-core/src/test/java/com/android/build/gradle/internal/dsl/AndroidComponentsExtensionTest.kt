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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.extension.impl.AndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.ApplicationAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.DynamicFeatureAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.LibraryAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.TestAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
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
    private lateinit var applicationExtension: ApplicationExtension

    @Before
    fun setUp() {
        val sdkComponentsBuildService = Mockito.mock(SdkComponentsBuildService::class.java)
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponentsBuildService))
        sdkComponents = Mockito.mock(SdkComponents::class.java)
        applicationExtension = Mockito.mock(ApplicationExtension::class.java)
    }

    @Test
    fun testPluginVersion() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()

        val androidComponents: ApplicationAndroidComponentsExtension = ApplicationAndroidComponentsExtensionImpl(
            dslServices,
            sdkComponents,
            variantApiOperationsRegistrar,
            Mockito.mock(ApplicationExtension::class.java)
        )
        assertThat(androidComponents.pluginVersion).isNotNull()
        assertThat(androidComponents.pluginVersion >= AndroidPluginVersion(4, 2)).isTrue()
    }

    @Test
    fun testSdkComponents() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()

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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>()
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
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
            Mockito.mock(SdkComponents::class.java),
            variantApiOperationsRegistrar,
            Mockito.mock(ApplicationExtension::class.java)
        )
        val fooVariant = appExtension.selector().withName(Pattern.compile("foo"))
        appExtension.beforeVariants {
                    it.minSdk = 23
        }

        appExtension.beforeVariants(fooVariant) {
                    it.enabled = false
        }
    }

    @Test
    fun testOnVariantsProperties() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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

        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
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

    @Test
    fun testApplicationDslFinalizationBlock() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>()
        val extension = Mockito.mock(ApplicationExtension::class.java)
        val appExtension = ApplicationAndroidComponentsExtensionImpl(dslServices,
                Mockito.mock(SdkComponents::class.java),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        appExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks(extension)
        assertThat(called).isTrue()
    }

    @Test
    fun testLibraryDslFinalizationBlock() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<LibraryExtension, LibraryVariantBuilder, LibraryVariant>()
        val extension = Mockito.mock(LibraryExtension::class.java)
        val libraryExtension = LibraryAndroidComponentsExtensionImpl(dslServices,
                Mockito.mock(SdkComponents::class.java),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        libraryExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks(extension)
        assertThat(called).isTrue()
    }

    @Test
    fun testDynamicFeatureDslFinalizationBlock() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<com.android.build.api.dsl.DynamicFeatureExtension, DynamicFeatureVariantBuilder, DynamicFeatureVariant>()
        val extension = Mockito.mock(com.android.build.api.dsl.DynamicFeatureExtension::class.java)
        val dynamicFeatureExtension = DynamicFeatureAndroidComponentsExtensionImpl(dslServices,
                Mockito.mock(SdkComponents::class.java),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        dynamicFeatureExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks(extension)
        assertThat(called).isTrue()
    }

    @Test
    fun testTestDslFinalizationBlock() {
        val variantApiOperationsRegistrar = VariantApiOperationsRegistrar<TestExtension, TestVariantBuilder, TestVariant>()
        val extension = Mockito.mock(TestExtension::class.java)
        val testExtension = TestAndroidComponentsExtensionImpl(dslServices,
                Mockito.mock(SdkComponents::class.java),
                variantApiOperationsRegistrar,
                extension
        )

        var called = false
        testExtension.finalizeDsl {
            assertThat(it).isEqualTo(extension)
            called = true
        }

        variantApiOperationsRegistrar.executeDslFinalizationBlocks(extension)
        assertThat(called).isTrue()
    }

    private fun createExtensionAwareBuildType(extension: ApplicationExtension): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val buildTypesContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<com.android.build.api.dsl.ApplicationBuildType>
        Mockito.`when`(extension.buildTypes).thenReturn(buildTypesContainer)
        val buildTypeExtensionContainer= Mockito.mock(ExtensionContainer::class.java)
        val buildType = Mockito.mock(com.android.build.api.dsl.BuildType::class.java)
        Mockito.`when`(buildType.extensions).thenReturn(buildTypeExtensionContainer)
        val buildTypes = listOf<com.android.build.api.dsl.BuildType>(buildType)
        Mockito.`when`(buildTypesContainer.iterator()).thenAnswer { buildTypes.iterator() }
        return buildTypeExtensionContainer
    }

    private fun createExtensionAwareProductFlavor(extension: ApplicationExtension): ExtensionContainer {
        @Suppress("UNCHECKED_CAST")
        val productFlavorContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<com.android.build.api.dsl.ApplicationProductFlavor>
        Mockito.`when`(extension.productFlavors).thenReturn(productFlavorContainer)
        val extensionContainer= Mockito.mock(ExtensionContainer::class.java)
        val productFlavor = Mockito.mock(com.android.build.api.dsl.ProductFlavor::class.java)
        Mockito.`when`(productFlavor.extensions).thenReturn(extensionContainer)
        val productFlavors = listOf<com.android.build.api.dsl.ProductFlavor>(productFlavor)
        Mockito.`when`(productFlavorContainer.iterator()).thenAnswer { productFlavors.iterator() }
        return extensionContainer
    }

    private fun  <DslExtensionT: CommonExtension<*, *, *, *>, VariantBuilderT: VariantBuilder, VariantT: Variant> testAllSelection(
            extension: AndroidComponentsExtensionImpl<DslExtensionT, VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants(extension.selector().all()) {
            visitedVariants.add(it)
        }
        @Suppress("UNCHECKED_CAST")
        operationsRegistrar.variantBuilderOperations.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }

    private fun <DslExtensionT: CommonExtension<*, *, *, *>, VariantBuilderT: VariantBuilder, VariantT: Variant> testNoSelection(
            extension: AndroidComponentsExtensionImpl<DslExtensionT, VariantBuilderT, VariantT>,
            operationsRegistrar: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
            variantType: Class<VariantBuilderT>) {
        val visitedVariants = mutableListOf<VariantBuilderT>()
        extension.beforeVariants { variant -> visitedVariants.add(variant)}
        operationsRegistrar.variantBuilderOperations.executeOperations(Mockito.mock(variantType))
        assertThat(visitedVariants).hasSize(1)
    }
}
