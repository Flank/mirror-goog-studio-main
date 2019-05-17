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

class MacroDefinitionsTest {
    @Test
    fun `macro lookup checks`() {
        Truth.assertThat(Macro.lookup("thisFile")).isEqualTo(Macro.BUILT_IN_THIS_FILE)
        Truth.assertThat(Macro.lookup("env.thisFile")).isEqualTo(Macro.BUILT_IN_THIS_FILE)
        Truth.assertThat(Macro.lookup("ndk.version")).isEqualTo(Macro.NDK_VERSION)
    }

    @Test
    fun `descriptions must end in period`() {
        Macro.values().forEach { macro->
            Truth.assertThat(macro.description)
                .endsWith(".")
        }
    }

    @Test
    fun `only allow forward slashes in example`() {
        Macro.values().forEach { macro->
            Truth.assertThat(macro.description)
                .doesNotContain("\\")
        }
    }
}