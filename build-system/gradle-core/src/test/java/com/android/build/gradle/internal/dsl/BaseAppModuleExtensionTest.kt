
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

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.decorator.DslDecorator
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.builder.core.VariantTypeImpl
import com.google.common.truth.Truth.assertThat
import groovy.util.Eval
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [BaseAppModuleExtension]
 */
class BaseAppModuleExtensionTest {
    private lateinit var appExtension: BaseAppModuleExtension
    @Suppress("UNCHECKED_CAST")
    @Before
    fun setUp() {
        val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
        val dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))
        AndroidLocationsBuildService.RegistrationAction(ProjectFactory.project).execute()
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

        val extension = androidPluginDslDecorator.decorate(ApplicationExtensionImpl::class)
            .getDeclaredConstructor(DslServices::class.java, DslContainerProvider::class.java)
            .newInstance(dslServices, variantInputModel)

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
    fun `check dynamic features`() {
        appExtension.dynamicFeatures += ":df1"
        assertThat(appExtension.dynamicFeatures).containsExactly(":df1")
        Eval.me("android", appExtension, "android.dynamicFeatures = [':other']")
        assertThat(appExtension.dynamicFeatures).containsExactly(":other")
    }

    @Test
    fun `check asset packs`() {
        appExtension.assetPacks += ":ap"
        assertThat(appExtension.assetPacks).containsExactly(":ap")
        Eval.me("android", appExtension, "android.assetPacks = [':other']")
        assertThat(appExtension.assetPacks).containsExactly(":other")
    }
}
