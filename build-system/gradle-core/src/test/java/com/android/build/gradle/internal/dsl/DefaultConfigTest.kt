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

import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.core.BuilderConstants
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class DefaultConfigTest {
    private val dslServices = createDslServices()

    @Test
    fun buildConfigFieldOverride() {
        val someFlavor = defaultConfig()

        assertThat(someFlavor).isNotNull()
        someFlavor.buildConfigField("String", "name", "sensitiveValue")
        someFlavor.buildConfigField("String", "name", "sensitiveValue")
        val messages = (dslServices.logger as FakeLogger).infos
        assertThat(messages).hasSize(1)
        assertThat(messages[0]).doesNotContain("sensitiveValue")
    }

    @Test
    fun resValueOverride() {
        val someFlavor = defaultConfig()

        assertThat(someFlavor).isNotNull()
        someFlavor.resValue("String", "name", "sensitiveValue")
        someFlavor.resValue("String", "name", "sensitiveValue")
        val messages = (dslServices.logger as FakeLogger).infos
        assertThat(messages).hasSize(1)
        assertThat(messages[0]).doesNotContain("sensitiveValue")
    }

    @Test
    fun setProguardFilesTest() {
        val flavor: ApplicationBaseFlavor = defaultConfig()
        flavor.apply {
            // Check set replaces
            proguardFiles += dslServices.file("replaced")
            setProguardFiles(listOf("test"))
            assertThat(proguardFiles).hasSize(1)
            assertThat(proguardFiles.single()).hasName("test")
            // Check set self doesn't clear
            setProguardFiles(proguardFiles)
            assertThat(proguardFiles.single()).hasName("test")
        }
    }

    private fun defaultConfig() =
        dslServices.newDecoratedInstance(DefaultConfig::class.java, BuilderConstants.MAIN, dslServices)
}
