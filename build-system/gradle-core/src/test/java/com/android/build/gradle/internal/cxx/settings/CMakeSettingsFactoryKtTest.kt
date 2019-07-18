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

import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.settings.PropertyValue.StringPropertyValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class CMakeSettingsFactoryKtTest {

    @Test
    fun `base all locations`() {
        BasicCmakeMock().apply {
            val settings = abi.gatherCMakeSettingsFromAllLocations()
            val resolver = CMakeSettingsNameResolver(settings.environments)
            assertThat(
                resolver.resolve("ndk.cmakeExecutable", listOf("ndk"))!!.get()
            ).contains("cmake")
        }
    }

    @Test
    fun `ninjaExecutable is only set if it exists`() {
        BasicCmakeMock().apply {
            val settings = abi.gatherCMakeSettingsFromAllLocations()
            val resolver = CMakeSettingsNameResolver(settings.environments)
            val cmakeExecutable = resolver.resolve("ndk.cmakeExecutable", listOf("ndk"))!!.get()
            assertThat(
                resolver.resolve("ndk.ninjaExecutable", listOf("ndk"))!!.get()
            ).contains("ninja")
            File(cmakeExecutable).parentFile.apply { mkdirs() }.apply {
                resolve("ninja").apply { if (exists()) delete() }
                resolve("ninja.exe").apply { if (exists()) delete() }
            }
            assertThat(
                resolver.resolve("ndk.ninjaExecutable", listOf("ndk"))!!.get()
            ).isEmpty()
        }
    }

    @Test
    fun `expand macros in inherited environment names`() {
        BasicCmakeMock().apply {
            val settings = abi.gatherCMakeSettingsFromAllLocations()
                .expandInheritEnvironmentMacros(abi)
            val resolver = CMakeSettingsNameResolver(settings.environments)
            assertThat(
                resolver.resolve("ndk.cmakeExecutable", listOf("ndk"))!!.get()
            ).contains("cmake")
            assertThat(
                resolver.resolve("ndk.abiBitness", listOf("ndk"))!!.get()
            ).contains("32")
            assertThat(
                resolver.resolve("ndk.platformCode", listOf("ndk"))!!.get()
            ).contains("K")
        }
    }

    @Test
    fun `nested macro expansion`() {
        assertThat(reifyString("Macro containing \${macro-a}") {
            when(it) {
                "macro-a" -> StringPropertyValue("\${macro-b}")
                "macro-b" -> StringPropertyValue("[expanded value]")
                else -> throw RuntimeException()
            }
        }).isEqualTo("Macro containing [expanded value]")
    }

    @Test
    fun `catch infinite macro expansion`() {
        assertThat(reifyString("Macro containing \${macro-a}") {
            when(it) {
                "macro-a" -> StringPropertyValue("\${macro-b}")
                "macro-b" -> StringPropertyValue("\${macro-a}")
                else -> throw RuntimeException()
            }
        }).isEqualTo("Macro containing \${macro-a}")
    }

    @Test
    fun `weird case`() {
        assertThat(reifyString("Macro containing \${macro-a}\${macro-b}") {
            when(it) {
                "macro-a" -> StringPropertyValue("\${mac")
                "macro-b" -> StringPropertyValue("ro-c}")
                "macro-c" -> StringPropertyValue("macro-c")
                else -> throw RuntimeException()
            }
        }).isEqualTo("Macro containing macro-c")
    }
}
