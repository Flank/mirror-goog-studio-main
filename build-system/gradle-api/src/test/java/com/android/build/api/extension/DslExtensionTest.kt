/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.extension

import com.android.build.api.variant.DslExtension
import com.google.common.truth.Truth
import org.junit.Test

class DslExtensionTest {

    interface DslExtensionType

    @Test
    fun testBuildTypeExtension() {
        val dslExtension = DslExtension.Builder("extension")
            .extendBuildTypeWith(DslExtensionType::class.java)
            .build()

        Truth.assertThat(dslExtension.dslName).isEqualTo("extension")
        Truth.assertThat(dslExtension.buildTypeExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        Truth.assertThat(dslExtension.productFlavorExtensionType).isNull()
        Truth.assertThat(dslExtension.projectExtensionType).isNull()
    }

    @Test
    fun testModuleExtension() {
        val dslExtension = DslExtension.Builder("extension")
            .extendProjectWith(DslExtensionType::class.java)
            .build()

        Truth.assertThat(dslExtension.dslName).isEqualTo("extension")
        Truth.assertThat(dslExtension.projectExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        Truth.assertThat(dslExtension.productFlavorExtensionType).isNull()
        Truth.assertThat(dslExtension.buildTypeExtensionType).isNull()
    }

    @Test
    fun testProductFlavorExtension() {
        val dslExtension = DslExtension.Builder("extension")
            .extendProductFlavorWith(DslExtensionType::class.java)
            .build()

        Truth.assertThat(dslExtension.dslName).isEqualTo("extension")
        Truth.assertThat(dslExtension.productFlavorExtensionType)
            .isEqualTo(DslExtensionType::class.java)
        Truth.assertThat(dslExtension.projectExtensionType).isNull()
        Truth.assertThat(dslExtension.buildTypeExtensionType).isNull()
    }
}
