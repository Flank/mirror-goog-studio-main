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
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JniLibsPackagingOptionsImplTest {

    private lateinit var dslPackagingOptions: PackagingOptions
    private val variantPropertiesApiServices = createVariantPropertiesApiServices()

    @Before
    fun setUp() {
        dslPackagingOptions = PackagingOptions()
    }

    @Test
    fun testExcludes() {
        dslPackagingOptions.excludes.add("foo")
        dslPackagingOptions.jniLibs.excludes.add("bar")

        val jniLibsPackagingOptions =
            JniLibsPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.excludes.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testPickFirsts() {
        dslPackagingOptions.pickFirsts.add("foo")
        dslPackagingOptions.jniLibs.pickFirsts.add("bar")

        val jniLibsPackagingOptions =
            JniLibsPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.pickFirsts.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testKeepDebugSymbols() {
        dslPackagingOptions.doNotStrip.add("foo")
        dslPackagingOptions.jniLibs.keepDebugSymbols.add("bar")

        val jniLibsPackagingOptions =
            JniLibsPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.keepDebugSymbols.get()).containsExactly("foo", "bar")
    }
}
