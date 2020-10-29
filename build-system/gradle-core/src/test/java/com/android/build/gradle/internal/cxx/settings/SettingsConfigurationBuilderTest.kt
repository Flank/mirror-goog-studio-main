/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.settings


import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.configure.CmakeProperty
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsConfigurationBuilderTest {

    @Test
    fun `round trip through builder`() {
        RandomInstanceGenerator().synthetics(SettingsConfiguration::class.java)
            .forEach { initial ->
                val builder =
                    SettingsConfigurationBuilder().initialize(initial)
                val recovered = builder.build()
                assertThat(initial.toJsonString()).isEqualTo(recovered.toJsonString())
            }
    }

    @Test
    fun `add property works`() {
        RandomInstanceGenerator()
            .synthetics(SettingsConfiguration::class.java)
            .forEach { initial ->
                val builder =
                    SettingsConfigurationBuilder()
                        .initialize(initial)
                        .putVariable(CmakeProperty.ANDROID_ABI, "x86")
                val recovered = builder.build()
                assertThat(recovered.variables).contains(
                    SettingsConfigurationVariable(
                        CmakeProperty.ANDROID_ABI.name,
                        "x86"
                    )
                )
            }
    }

    @Test
    fun `exercise other properties`() {
        RandomInstanceGenerator()
            .synthetics(SettingsConfiguration::class.java)
            .forEach { initial ->
                val builder =
                    SettingsConfigurationBuilder()
                        .initialize(initial)
                builder.configurationType = "ddd"
                builder.installRoot = "xxx"
                builder.cmakeToolchain = "yyy"
                builder.cmakeCommandArgs = "zzz"
                builder.buildCommandArgs = "aaa"
                builder.ctestCommandArgs = "bbb"
                builder.inheritedEnvironments = listOf("ccc")

                val recovered = builder.build()
                assertThat(recovered.configurationType).isEqualTo("ddd")
                assertThat(recovered.installRoot).isEqualTo("xxx")
                assertThat(recovered.cmakeToolchain).isEqualTo("yyy")
                assertThat(recovered.cmakeCommandArgs).isEqualTo("zzz")
                assertThat(recovered.buildCommandArgs).isEqualTo("aaa")
                assertThat(recovered.ctestCommandArgs).isEqualTo("bbb")
                assertThat(recovered.inheritEnvironments).containsExactly("ccc")
            }
    }
}
