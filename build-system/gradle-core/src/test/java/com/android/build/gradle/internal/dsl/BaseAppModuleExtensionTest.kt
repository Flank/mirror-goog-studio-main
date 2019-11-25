
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
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.AppVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.testutils.MockitoKt.any
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.model.ObjectFactory
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
/**
 * Tests for [BaseAppModuleExtension]
 */
class BaseAppModuleExtensionTest {
    lateinit var appExtension: BaseAppModuleExtension
    @Suppress("UNCHECKED_CAST")
    @Before
    fun setUp() {
        val project = Mockito.mock(Project::class.java)
        val objectFactory = Mockito.mock(ObjectFactory::class.java)
        Mockito.`when`(project.objects).thenReturn(objectFactory)
        val configurations = Mockito.mock(ConfigurationContainer::class.java)
        Mockito.`when`(project.configurations).thenReturn(configurations)
        Mockito.`when`(configurations.maybeCreate(any(String::class.java))).thenReturn(Mockito.mock(Configuration::class.java))
        val defaultConfig = Mockito.mock(DefaultConfig::class.java)
        val vectorDrawablesOptions = Mockito.mock(VectorDrawablesOptions::class.java)
        Mockito.`when`(defaultConfig.vectorDrawables).thenReturn(vectorDrawablesOptions)
        Mockito.`when`(defaultConfig.name).thenReturn("default")
        Mockito.`when`(objectFactory.newInstance(any(Class::class.java), ArgumentMatchers.any()))
            .thenAnswer { invocation ->
                if (invocation.arguments[0] == DefaultConfig::class.java)
                    defaultConfig
                else Mockito.mock(invocation.arguments[0] as Class<*>)
            }
        Mockito.`when`(objectFactory.domainObjectSet(any(Class::class.java))).thenReturn(Mockito.mock(DomainObjectSet::class.java))
        val extension = ApplicationExtensionImpl(
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<BuildType>,
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<ProductFlavor>,
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<SigningConfig>
        )

        appExtension = BaseAppModuleExtension(
            project,
            Mockito.mock(ProjectOptions::class.java),
            Mockito.mock(GlobalScope::class.java),
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<BaseVariantOutput>,
            Mockito.mock(SourceSetManager::class.java),
            Mockito.mock(ExtraModelInfo::class.java),
            extension)
    }

    @Test
    fun testOnVariants() {
        val onVariants = appExtension.onVariants()
        appExtension.onVariants()
            .withType(AppVariant::class.java)
            .withName("foo") {
                minSdkVersion = 23
            }
        appExtension.onVariants()
            .withName("foo") {
                enabled = false
            }
    }

    @Test
    fun testOnVariantsProperties() {
        appExtension.onVariantProperties()
            .withName("foo", Action {
                it.operations.get(PublicArtifactType.APK) })
        appExtension.onVariantProperties()
            .withFlavor("f1" to "dim1", Action {
                it.operations.get(PublicArtifactType.APK) }
            )

        appExtension.onVariantProperties()
            .withType(AppVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1", Action {
                it.operations.get(PublicArtifactType.APK)
            })

        appExtension.onVariantProperties()
            .withType(AppVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1")
            .withFlavor("f2" to "dim2", Action {
                it.operations.get(PublicArtifactType.APK)
            })
    }
}
