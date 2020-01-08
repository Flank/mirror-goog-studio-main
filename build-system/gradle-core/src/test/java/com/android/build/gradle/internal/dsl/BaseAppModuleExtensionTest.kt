
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
package com.android.build.gradle.internal.dsl

import com.android.build.api.artifact.PublicArtifactType
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant2.createFakeDslScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [BaseAppModuleExtension]
 */
class BaseAppModuleExtensionTest {
    lateinit var appExtension: BaseAppModuleExtension
    @Suppress("UNCHECKED_CAST")
    @Before
    fun setUp() {
        val dslScope = createFakeDslScope()

        val defaultConfig = Mockito.mock(DefaultConfig::class.java)
        val vectorDrawablesOptions = Mockito.mock(VectorDrawablesOptions::class.java)
        Mockito.`when`(defaultConfig.vectorDrawables).thenReturn(vectorDrawablesOptions)
        Mockito.`when`(defaultConfig.name).thenReturn("default")
        val extension = ApplicationExtensionImpl(
            dslScope = dslScope,
            buildTypes = Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<BuildType>,
            defaultConfig = defaultConfig,
            productFlavors = Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<ProductFlavor>,
            signingConfigs = Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<SigningConfig>
        )

        appExtension = BaseAppModuleExtension(
            dslScope,
            Mockito.mock(ProjectOptions::class.java),
            Mockito.mock(GlobalScope::class.java),
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<BaseVariantOutput>,
            Mockito.mock(SourceSetManager::class.java),
            Mockito.mock(ExtraModelInfo::class.java),
            extension)
    }

    @Test
    fun testOnVariants() {
        appExtension.onVariants
            .withName("foo") {
                minSdkVersion = 23
            }

        appExtension.onVariants
            .withName("foo") {
                enabled = false
            }
    }

    @Test
    fun testOnVariantsProperties() {
        appExtension.onVariantProperties
            .withName("foo", Action {
                it.operations.get(PublicArtifactType.APK) })
        appExtension.onVariantProperties
            .withFlavor("f1" to "dim1", Action {
                it.operations.get(PublicArtifactType.APK) }
            )

        appExtension.onVariantProperties
            .withType(ApplicationVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1", Action {
                it.operations.get(PublicArtifactType.APK)
            })

        appExtension.onVariantProperties
            .withType(ApplicationVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1")
            .withFlavor("f2" to "dim2", Action {
                it.operations.get(PublicArtifactType.APK)
            })
    }
}
