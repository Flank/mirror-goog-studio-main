
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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.ApplicationVariantProperties
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.builder.core.VariantTypeImpl
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/**
 * Tests for [BaseAppModuleExtension]
 */
class BaseAppModuleExtensionTest {
    private lateinit var appExtension: BaseAppModuleExtension
    @Suppress("UNCHECKED_CAST")
    @Before
    fun setUp() {
        val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
        `when`(sdkComponents.adbExecutableProvider).thenReturn(FakeGradleProvider(null))
        `when`(sdkComponents.ndkDirectoryProvider).thenReturn(FakeGradleProvider(null))
        `when`(sdkComponents.sdkDirectoryProvider).thenReturn(FakeGradleProvider(null))

        val dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))

        val variantInputModel = LegacyVariantInputManager(
            dslServices,
            VariantTypeImpl.BASE_APK,
            SourceSetManager(
                ProjectFactory.project,
                false,
                dslServices,
                DelayedActionsExecutor()
            )
        )

        val extension = ApplicationExtensionImpl(
            dslServices = dslServices,
            dslContainers = variantInputModel
        )

        appExtension = BaseAppModuleExtension(
            dslServices,
            Mockito.mock(GlobalScope::class.java),
            Mockito.mock(NamedDomainObjectContainer::class.java) as NamedDomainObjectContainer<BaseVariantOutput>,
            variantInputModel.sourceSetManager,
            Mockito.mock(ExtraModelInfo::class.java),
            extension
        )
    }

    @Test
    fun testOnVariants() {
        appExtension.onVariants
            .withName("foo") {
                minSdkVersion = AndroidVersionImpl(23)
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
                it.artifacts.get(ArtifactType.APK) })
        appExtension.onVariantProperties
            .withFlavor("f1" to "dim1", Action {
                it.artifacts.get(ArtifactType.APK) }
            )

        appExtension.onVariantProperties
            .withType(ApplicationVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1", Action {
                it.artifacts.get(ArtifactType.APK)
            })

        appExtension.onVariantProperties
            .withType(ApplicationVariantProperties::class.java)
            .withBuildType("debug")
            .withFlavor("f1" to "dim1")
            .withFlavor("f2" to "dim2", Action {
                it.artifacts.get(ArtifactType.APK)
            })
    }
}
