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

package com.android.build.gradle.integration.common.fixture.testprojects

class AndroidComponentsBuilderImpl: AndroidComponentsBuilder {

    private class EnabledState {
        var main = true
        var androidTest = true
        var unitTest = true
    }

    private val disabledMap = mutableMapOf<String, EnabledState>()
    private val registeredSourceTypes = mutableListOf<String>()

    override fun disableVariant(variantName: String) {
        val state = disabledMap.computeIfAbsent(variantName) {
            EnabledState()
        }

        state.main = false
    }

    override fun disableAndroidTest(variantName: String) {
        val state = disabledMap.computeIfAbsent(variantName) {
            EnabledState()
        }

        state.androidTest = false
    }

    override fun disableUnitTest(variantName: String) {
        val state = disabledMap.computeIfAbsent(variantName) {
            EnabledState()
        }

        state.unitTest = false
    }

    override fun registerSourceType(name: String) {
        registeredSourceTypes += name
    }

    fun writeBuildFile(sb: StringBuilder) {
        if (disabledMap.isNotEmpty() || registeredSourceTypes.isNotEmpty()) {
            sb.append("androidComponents {\n")

            for ((name, state) in disabledMap) {
                sb.append("  beforeVariants(selector().withName(\"$name\")) { variant ->\n")
                if (!state.main) {
                    sb.append("    variant.enable = false\n")
                }
                if (!state.androidTest) {
                    sb.append("    variant.enableAndroidTest = false\n")
                }

                if (!state.unitTest) {
                    sb.append("    variant.enableUnitTest = false\n")
                }

                sb.append("  }\n") // beforeVariants
            }

            for (name in registeredSourceTypes) {
                sb.append("  registerSourceType(\"$name\")\n")
            }

            sb.append("}\n")
        }
    }
}
