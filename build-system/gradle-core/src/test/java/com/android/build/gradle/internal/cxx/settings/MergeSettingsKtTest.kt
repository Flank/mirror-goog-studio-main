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

import com.google.common.truth.Truth
import org.junit.Test

class MergeSettingsKtTest {

    @Test
    fun mergeCMakeSettings() {
        val s1 = createSettingsFromJsonString("""
            {
             "environments": [ { "environment": "e1" } ],
              "configurations": [ { name: "c1" } ]
             }
        """.trimIndent())
        val s2 = createSettingsFromJsonString("""
            {
             "environments": [ { "environment": "e2" } ],
              "configurations": [ { name: "c2" } ]
             }
        """.trimIndent())

        val merged = mergeSettings(s1, s2)
        Truth.assertThat(merged.configurations).hasSize(2)
        Truth.assertThat(merged.environments).hasSize(2)
        Truth.assertThat(merged.environments[0].environment).isEqualTo("e1")
        Truth.assertThat(merged.environments[1].environment).isEqualTo("e2")
        Truth.assertThat(merged.configurations[0].name).isEqualTo("c1")
        Truth.assertThat(merged.configurations[1].name).isEqualTo("c2")
    }
}
