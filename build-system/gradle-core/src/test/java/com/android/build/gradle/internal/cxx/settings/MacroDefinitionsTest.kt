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

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeAbiModel
import com.android.build.gradle.internal.cxx.model.CxxCmakeModuleModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxProjectModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.settings.Environment.GRADLE
import com.android.build.gradle.internal.cxx.settings.Environment.MICROSOFT_BUILT_IN
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_EXPOSED_BY_HOST
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_PLATFORM
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacroDefinitionsTest {
    @Test
    fun `macro lookup checks`() {
        assertThat(Macro.lookup("thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        assertThat(Macro.lookup("env.thisFile")).isEqualTo(Macro.ENV_THIS_FILE)
        assertThat(Macro.lookup("ndk.moduleNdkVersion")).isEqualTo(Macro.NDK_MODULE_NDK_VERSION)
    }

    @Test
    fun `descriptions must end in period`() {
        Macro.values().forEach { macro->
            assertThat(macro.description)
                .endsWith(".")
        }
    }

    @Test
    fun `only allow forward slashes in example`() {
        Macro.values().forEach { macro->
            assertThat(macro.description)
                .doesNotContain("\\")
        }
    }

    @Test
    fun `ensure all qualified names are distinct`() {
        val seen = mutableSetOf<String>()
        Macro.values().map { macro->
            if (seen.contains(macro.tag)) {
                throw RuntimeException("Tag ${macro.qualifiedName} seen twice")
            }
            seen += macro.qualifiedName
        }
    }

    /**
     * Enforces a naming convention that connects [Macro] values to fields on the [CxxAbiModel]
     * object model.
     */
    @Test
    fun `ensure kotlin enum names match object model names`() {
        Macro.values()
            .map { macro->
                assertThat(macro.name.endsWith("DIRECTORY")).named(macro.name).isFalse() // Should be _DIR
                assertThat(macro.name.endsWith("FOLDER")).named(macro.name).isFalse() // Should be _DIR
                when(macro.environment) {
                    NDK_ABI -> assertThat(macro.name).startsWith("NDK_ABI_")
                    MICROSOFT_BUILT_IN -> assertThat(macro.name).startsWith("ENV_")
                    NDK_EXPOSED_BY_HOST -> assertThat(macro.name).startsWith("NDK_ANDROID_GRADLE_")
                    NDK -> {
                        assertThat(macro.name).startsWith("NDK_")
                        assertThat(macro.name.startsWith("NDK_VARIANT")).named(macro.name).isFalse()
                        assertThat(macro.name.startsWith("NDK_MODULE")).named(macro.name).isFalse()
                        assertThat(macro.name.startsWith("NDK_PROJECT")).named(macro.name).isFalse()
                    }
                    NDK_PLATFORM -> assertThat(macro.name).startsWith("NDK_PLATFORM")
                    GRADLE -> {
                        when (macro.bindingType) {
                            //Macro::class -> macro.takeFrom(macro) ?: fail()
                            CxxCmakeAbiModel::class,
                            CxxAbiModel::class -> {
                                assertThat(macro.name).startsWith("NDK_")
                                assertThat(macro.name.startsWith("NDK_VARIANT")).isFalse()
                                assertThat(macro.name.startsWith("NDK_MODULE")).isFalse()
                                assertThat(macro.name.startsWith("NDK_PROJECT")).isFalse()
                            }
                            CxxVariantModel::class -> assertThat(macro.name).startsWith("NDK_VARIANT_")
                            CxxCmakeModuleModel::class,
                            CxxModuleModel::class -> assertThat(macro.name).startsWith("NDK_MODULE_")
                            CxxProjectModel::class -> assertThat(macro.name).startsWith("NDK_PROJECT_")
                            else -> error("$macro")
                        }
                    }
                    else -> error(macro.environment)
                }
        }
    }

    @Test
    fun `ensure kotlin enum names match environment names`() {
        Macro.values().map { macro->

            val sb = StringBuilder()
            var lastWasDigit = false
            for (c in macro.qualifiedName) {
                when {
                    c.isDigit() -> {
                        if (!lastWasDigit) {
                            sb.append("_")
                        }
                        sb.append(c)
                    }
                    c.isUpperCase() -> sb.append("_$c")
                    c == '.' -> sb.append("_")
                    else -> sb.append(c.toUpperCase())
                }
                lastWasDigit = c.isDigit()
            }
            assertThat(macro.toString()).isEqualTo(sb.toString())
        }
    }
}
