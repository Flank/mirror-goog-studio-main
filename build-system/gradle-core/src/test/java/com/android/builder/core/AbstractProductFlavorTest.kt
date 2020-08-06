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

import com.android.builder.internal.ClassFieldImpl
import com.google.common.collect.ImmutableMap
import junit.framework.TestCase

class AbstractProductFlavorTest : TestCase() {
    private lateinit var custom: ProductFlavorImpl

    override fun setUp() {
        custom = ProductFlavorImpl("custom")
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
        custom.addResValue(ClassFieldImpl("foo", "one", "oneValue"))
        custom.addResValue(ClassFieldImpl("foo", "two", "twoValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "one", "oneValue"))
        custom.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValue"))
        custom.setVersionNameSuffix("custom")
        custom.setApplicationIdSuffix("custom")
    }

    fun test_initWith() {
        val flavor: AbstractProductFlavor =
            ProductFlavorImpl(custom.name)
        flavor._initWith(custom!!)
        assertEquals(custom.toString(), flavor.toString())
    }

    private class ProductFlavorImpl(
        name: String,
        override val vectorDrawables: DefaultVectorDrawablesOptions = DefaultVectorDrawablesOptions()
    ) : AbstractProductFlavor(name)
}
