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

import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ResourcesPackagingOptionsImplTest {

    private lateinit var dslPackagingOptions: PackagingOptions
    private val variantPropertiesApiServices = createVariantPropertiesApiServices()

    @Before
    fun setUp() {
        dslPackagingOptions = PackagingOptions()
    }

    @Test
    fun testExcludes() {
        dslPackagingOptions.excludes.add("foo")
        dslPackagingOptions.excludes.remove("/META-INF/LICENSE")
        dslPackagingOptions.resources.excludes.add("bar")
        dslPackagingOptions.resources.excludes.remove("/META-INF/NOTICE")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedExcludes = defaultExcludes.toMutableSet()
        expectedExcludes.addAll(listOf("foo", "bar"))
        expectedExcludes.removeAll(listOf("/META-INF/LICENSE", "/META-INF/NOTICE"))
        assertThat(resourcesPackagingOptionsImpl.excludes.get())
            .containsExactlyElementsIn(expectedExcludes)
    }

    @Test
    fun testPickFirsts() {
        dslPackagingOptions.pickFirsts.add("foo")
        dslPackagingOptions.resources.pickFirsts.add("bar")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        assertThat(resourcesPackagingOptionsImpl.pickFirsts.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testMerges() {
        dslPackagingOptions.merges.add("foo")
        dslPackagingOptions.resources.merges.add("bar")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedMerges = defaultMerges.toMutableSet()
        expectedMerges.addAll(listOf("foo", "bar"))
        assertThat(resourcesPackagingOptionsImpl.merges.get())
            .containsExactlyElementsIn(expectedMerges)
    }

    @Test
    fun testMerges_removeDefaultViaDeprecatedDsl() {
        dslPackagingOptions.merges.add("foo")
        dslPackagingOptions.merges.remove("/META-INF/services/**")
        dslPackagingOptions.resources.merges.add("bar")

        val resourcesPackagingOptionsImpl =
            ResourcesPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

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
            ResourcesPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedMerges = defaultMerges.toMutableSet()
        expectedMerges.addAll(listOf("foo", "bar"))
        expectedMerges.remove("/META-INF/services/**")
        assertThat(resourcesPackagingOptionsImpl.merges.get())
            .containsExactlyElementsIn(expectedMerges)
    }
}