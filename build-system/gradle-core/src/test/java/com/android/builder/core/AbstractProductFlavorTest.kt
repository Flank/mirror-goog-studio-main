/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.builder.core

import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.internal.ClassFieldImpl
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AbstractProductFlavorTest {

    private val dslServices: DslServices by lazy { createDslServices() }

    private fun productFlavor(name: String): ProductFlavor =
        dslServices.newDecoratedInstance(ProductFlavor::class.java, name, dslServices)

    @Test
    fun testInitWith() {
        val custom = productFlavor("custom")
        custom.setMinSdkVersion(DefaultApiVersion(42))
        custom.setTargetSdkVersion(DefaultApiVersion(43))
        custom.renderscriptTargetApi = 17
        custom.setVersionCode(44)
        custom.setVersionName("42.0")
        custom.setTestApplicationId("com.forty.two.test")
        custom.setTestInstrumentationRunner("com.forty.two.test.Runner")
        custom.setTestHandleProfiling(true)
        custom.setTestFunctionalTest(true)
        custom.addResourceConfiguration("hdpi")
        custom.addManifestPlaceholders(
            ImmutableMap.of(
                "one",
                "oneValue",
                "two",
                "twoValue"
            )
        )
        custom.addResValue("foo/one", ClassFieldImpl("foo", "one", "oneValue"))
        custom.addResValue("foo/two", ClassFieldImpl("foo", "two", "twoValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "one", "oneValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValue"))
        custom.setVersionNameSuffix("custom")
        custom.setApplicationIdSuffix("custom")
        custom.vectorDrawables.useSupportLibrary = true

        val defaultSigning = createDslServices().let { services ->
            services.newDecoratedInstance(SigningConfig::class.java, "defaultConfig", services)
        }

        defaultSigning.storePassword("test")
        custom.setSigningConfig(defaultSigning)
        custom.isDefault = true

        val flavor = productFlavor(custom.name)
        flavor.initWith(custom)
        assertThat(custom.toString()).isEqualTo(flavor.toString())
    }
}
