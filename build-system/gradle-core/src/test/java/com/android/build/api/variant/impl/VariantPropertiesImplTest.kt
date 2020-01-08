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

import com.android.build.api.artifact.Operations
import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * Tests for [VariantPropertiesImpl]
 */
class VariantPropertiesImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock lateinit var variantScope: VariantScope
    @Mock lateinit var globalScope: GlobalScope
    @Mock lateinit var variantDslInfo: VariantDslInfo
    @Mock lateinit var apkData: ApkData
    @Mock lateinit var operations: Operations
    @Mock lateinit var componentIdentity: ComponentIdentity
    @Mock lateinit var dslScope: DslScope

    lateinit var project: Project

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        Mockito.`when`(variantScope.globalScope).thenReturn(globalScope)
        Mockito.`when`(globalScope.project).thenReturn(project)
        Mockito.`when`(globalScope.projectOptions).thenReturn(Mockito.mock(ProjectOptions::class.java))
        Mockito.`when`(dslScope.objectFactory).thenReturn(project.objects)
        Mockito.`when`(dslScope.providerFactory).thenReturn(project.providers)
        Mockito.`when`(variantScope.variantDslInfo).thenReturn(variantDslInfo)
    }

    @Test
    fun testManifestProvidedVersion() {
        val properties = VariantPropertiesImpl(
            dslScope, variantScope, operations, componentIdentity)
        Mockito.`when`(variantDslInfo.manifestVersionCodeSupplier)
            .thenReturn(IntSupplier { 10 })
        Mockito.`when`(variantDslInfo.manifestVersionNameSupplier)
            .thenReturn(Supplier<String?> { "foo" })

        properties.addVariantOutput(apkData)
        Truth.assertThat(properties.outputs).hasSize(1)

        Truth.assertThat(properties.outputs[0].versionCode.get()).isEqualTo(10)
        Truth.assertThat(properties.outputs[0].versionName.get()).isEqualTo("foo")
    }

    @Test
    fun testDslProvidedVersion() {
        val properties = object: VariantPropertiesImpl(
            dslScope, variantScope, operations, componentIdentity) {
            @Suppress("UNCHECKED_CAST")
            override val applicationId: Property<String> = Mockito.mock(Property::class.java) as Property<String>
        }
        Mockito.`when`(variantDslInfo.getVersionCode(true)).thenReturn(23)
        Mockito.`when`(variantDslInfo.getVersionName(true)).thenReturn("bar")

        properties.addVariantOutput(apkData)
        Truth.assertThat(properties.outputs).hasSize(1)

        Truth.assertThat(properties.outputs[0].versionCode.get()).isEqualTo(23)
        Truth.assertThat(properties.outputs[0].versionName.get()).isEqualTo("bar")
    }

    @Test
    fun testManifestAndDslProvidedVersions() {
        val properties = VariantPropertiesImpl(
            dslScope, variantScope, operations, componentIdentity)
        Mockito.`when`(variantDslInfo.manifestVersionCodeSupplier)
            .thenReturn(IntSupplier { 10 })
        Mockito.`when`(variantDslInfo.manifestVersionNameSupplier)
            .thenReturn(Supplier<String?> { "foo" })
        Mockito.`when`(variantDslInfo.getVersionCode(true)).thenReturn(23)
        Mockito.`when`(variantDslInfo.getVersionName(true)).thenReturn("bar")

        properties.addVariantOutput(apkData)
        Truth.assertThat(properties.outputs).hasSize(1)

        // we expect the DSL-provided versions to trump the manifest-provided versions.
        Truth.assertThat(properties.outputs[0].versionCode.get()).isEqualTo(23)
        Truth.assertThat(properties.outputs[0].versionName.get()).isEqualTo("bar")
    }
}