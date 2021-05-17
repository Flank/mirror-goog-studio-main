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
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ResourcesApkPackagingOptionsImplTest {

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
            ResourcesApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices)

        val expectedExcludes = defaultExcludes.plus("/META-INF/*.kotlin_module").toMutableSet()
        expectedExcludes.addAll(listOf("foo", "bar", "baz"))
        expectedExcludes.removeAll(listOf("/META-INF/LICENSE", "/META-INF/NOTICE"))
        assertThat(resourcesPackagingOptionsImpl.excludes.get())
            .containsExactlyElementsIn(expectedExcludes)
    }
}
