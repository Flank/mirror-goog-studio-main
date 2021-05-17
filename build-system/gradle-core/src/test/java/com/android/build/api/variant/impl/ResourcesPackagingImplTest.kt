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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ResourcesPackagingImplTest {

    private lateinit var dslPackagingOptions: PackagingOptions
    private val projectServices = createProjectServices()
    private val dslServices: DslServices = createDslServices(projectServices)
    private val variantPropertiesApiServices = createVariantPropertiesApiServices(projectServices)

    interface PackagingOptionsWrapper {
        val packagingOptions: PackagingOptions
    }

    @Before
    fun setUp() {
        dslPackagingOptions = androidPluginDslDecorator.decorate(PackagingOptionsWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices)
            .packagingOptions
    }

    @Test
    fun testExcludes() {
        dslPackagingOptions.excludes.add("foo")
        dslPackagingOptions.excludes.remove("/META-INF/LICENSE")
        dslPackagingOptions.resources.excludes.add("bar")
        dslPackagingOptions.resources.excludes.remove("/META-INF/NOTICE")
        // test setExcludes method too
        val dslResourcesPackagingOptionsImpl =
            dslPackagingOptions.resources
                as com.android.build.gradle.internal.dsl.ResourcesPackagingOptionsImpl
        dslResourcesPackagingOptionsImpl.setExcludes(
            Sets.union(dslPackagingOptions.resources.excludes, setOf("baz"))
        )

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedExcludes = defaultExcludes.toMutableSet()
        expectedExcludes.addAll(listOf("foo", "bar", "baz"))
        expectedExcludes.removeAll(listOf("/META-INF/LICENSE", "/META-INF/NOTICE"))
        assertThat(resourcesPackagingOptionsImpl.excludes.get())
            .containsExactlyElementsIn(expectedExcludes)
    }

    @Test
    fun testPickFirsts() {
        dslPackagingOptions.pickFirsts.add("foo")
        dslPackagingOptions.resources.pickFirsts.add("bar")
        // test setPickFirsts method too
        val dslResourcesPackagingOptionsImpl =
            dslPackagingOptions.resources
                as com.android.build.gradle.internal.dsl.ResourcesPackagingOptionsImpl
        dslResourcesPackagingOptionsImpl.setPickFirsts(
            Sets.union(dslPackagingOptions.resources.pickFirsts, setOf("baz"))
        )

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        assertThat(resourcesPackagingOptionsImpl.pickFirsts.get())
            .containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testMerges() {
        dslPackagingOptions.merges.add("foo")
        dslPackagingOptions.resources.merges.add("bar")
        // test setMerges method too
        val dslResourcesPackagingOptionsImpl =
            dslPackagingOptions.resources
                as com.android.build.gradle.internal.dsl.ResourcesPackagingOptionsImpl
        dslResourcesPackagingOptionsImpl.setMerges(
            Sets.union(dslPackagingOptions.resources.merges, setOf("baz"))
        )

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedMerges = defaultMerges.toMutableSet()
        expectedMerges.addAll(listOf("foo", "bar", "baz"))
        assertThat(resourcesPackagingOptionsImpl.merges.get())
            .containsExactlyElementsIn(expectedMerges)
    }

    @Test
    fun testMerges_removeDefaultViaDeprecatedDsl() {
        dslPackagingOptions.merges.add("foo")
        dslPackagingOptions.merges.remove("/META-INF/services/**")
        dslPackagingOptions.resources.merges.add("bar")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedMerges = defaultMerges.toMutableSet()
        expectedMerges.addAll(listOf("foo", "bar"))
        expectedMerges.remove("/META-INF/services/**")
        assertThat(resourcesPackagingOptionsImpl.merges.get())
            .containsExactlyElementsIn(expectedMerges)
    }

    @Test
    fun testMerges_removeDefaultViaNewDsl() {
        dslPackagingOptions.merges.add("foo")
        dslPackagingOptions.resources.merges.add("bar")
        dslPackagingOptions.resources.merges.remove("/META-INF/services/**")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedMerges = defaultMerges.toMutableSet()
        expectedMerges.addAll(listOf("foo", "bar"))
        expectedMerges.remove("/META-INF/services/**")
        assertThat(resourcesPackagingOptionsImpl.merges.get())
            .containsExactlyElementsIn(expectedMerges)
    }
}
