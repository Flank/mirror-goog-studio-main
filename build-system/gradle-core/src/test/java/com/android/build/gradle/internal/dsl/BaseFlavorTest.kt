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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth
import org.junit.Test

internal class BaseFlavorTest {
    private val dslServices = createDslServices()

    @Test
    fun buildConfigFieldOverride() {
        val someFlavor = object: BaseFlavor("someFlavor", dslServices) {}

        Truth.assertThat(someFlavor).isNotNull()
        someFlavor.buildConfigField("String", "name", "sensitiveValue")
        someFlavor.buildConfigField("String", "name", "sensitiveValue")
        val messages = (dslServices.logger as FakeLogger).infos
        Truth.assertThat(messages).hasSize(1)
        Truth.assertThat(messages[0]).doesNotContain("sensitiveValue")
    }

    @Test
    fun resValueOverride() {
        val someFlavor = object: BaseFlavor("someFlavor", dslServices) {}

        Truth.assertThat(someFlavor).isNotNull()
        someFlavor.resValue("String", "name", "sensitiveValue")
        someFlavor.resValue("String", "name", "sensitiveValue")
        val messages = (dslServices.logger as FakeLogger).infos
        Truth.assertThat(messages).hasSize(1)
        Truth.assertThat(messages[0]).doesNotContain("sensitiveValue")
    }
}
