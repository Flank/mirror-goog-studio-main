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
import com.android.sdklib.AndroidVersion.VersionCodes.M
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JniLibsApkPackagingOptionsImplTest {

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

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.excludes.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testPickFirsts() {
        dslPackagingOptions.pickFirsts.add("foo")
        dslPackagingOptions.jniLibs.pickFirsts.add("bar")

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.pickFirsts.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testKeepDebugSymbols() {
        dslPackagingOptions.doNotStrip.add("foo")
        dslPackagingOptions.jniLibs.keepDebugSymbols.add("bar")

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.keepDebugSymbols.get()).containsExactly("foo", "bar")
    }

    @Test
    fun testDefaultLegacyPackaging() {
        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M)
        val legacyJniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M - 1)

        assertThat(jniLibsApkPackagingOptions.legacyPackaging.get()).isFalse()
        assertThat(legacyJniLibsApkPackagingOptions.legacyPackaging.get()).isTrue()
    }

    @Test
    fun testExplicitLegacyPackaging() {
        dslPackagingOptions.jniLibs.legacyPackaging = true

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingOptionsImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.legacyPackaging.get()).isTrue()
    }
}
