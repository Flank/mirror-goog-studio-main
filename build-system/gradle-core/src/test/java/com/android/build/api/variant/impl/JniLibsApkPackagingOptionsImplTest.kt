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
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.android.sdklib.AndroidVersion.VersionCodes.M
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JniLibsApkPackagingOptionsImplTest {

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
        dslPackagingOptions.jniLibs.excludes.add("bar")
        // test setExcludes method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackagingOptions.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingOptionsImpl
        dslJniLibsPackagingOptionsImpl.setExcludes(
            Sets.union(dslPackagingOptions.jniLibs.excludes, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.excludes.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testPickFirsts() {
        dslPackagingOptions.pickFirsts.add("foo")
        dslPackagingOptions.jniLibs.pickFirsts.add("bar")
        // test setPickFirsts method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackagingOptions.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingOptionsImpl
        dslJniLibsPackagingOptionsImpl.setPickFirsts(
            Sets.union(dslPackagingOptions.jniLibs.pickFirsts, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.pickFirsts.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testKeepDebugSymbols() {
        dslPackagingOptions.doNotStrip.add("foo")
        dslPackagingOptions.jniLibs.keepDebugSymbols.add("bar")
        // test setKeepDebugSymbols method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackagingOptions.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingOptionsImpl
        dslJniLibsPackagingOptionsImpl.setKeepDebugSymbols(
            Sets.union(dslPackagingOptions.jniLibs.keepDebugSymbols, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.keepDebugSymbols.get())
            .containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testDefaultUseLegacyPackaging() {
        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)
        val legacyJniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M - 1)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackaging.get()).isFalse()
        assertThat(legacyJniLibsApkPackagingOptions.useLegacyPackaging.get()).isTrue()
    }

    @Test
    fun testExplicitUseLegacyPackaging() {
        dslPackagingOptions.jniLibs.useLegacyPackaging = true

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackaging.get()).isTrue()
    }

    @Test
    fun testDefaultUseLegacyPackagingFromBundle() {
        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)
        val legacyJniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M - 1)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isFalse()
        assertThat(legacyJniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isFalse()
    }

    @Test
    fun testExplicitUseLegacyPackagingFromBundle() {
        dslPackagingOptions.jniLibs.useLegacyPackaging = true

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackagingOptions, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isTrue()
    }

}
