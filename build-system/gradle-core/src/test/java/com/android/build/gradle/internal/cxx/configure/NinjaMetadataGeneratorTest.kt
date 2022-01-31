/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure


import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NinjaMetadataGeneratorTest {

    @Test
    fun `check error matcher`() {
        checkError("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Microsoft\\VC\\v160\\Google\\Google.Android.Cpp.Ninja.targets(639,7): error : NinjaRequireMinSdkVersion property value [] must be set to the same value as AndroidMinSdkVersion [15] [C:\\src\\Teapot\\GameApplication\\GameApplication.vcxproj]")
        checkError("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Microsoft\\VC\\v160\\Google\\Google.Android.Cpp.Ninja.targets(639,7): error AGDE1000: NinjaRequireMinSdkVersion property value [] must be set to the same value as AndroidMinSdkVersion [15] [C:\\src\\Teapot\\GameEngine\\GameEngine.vcxproj]")
        checkNotError("Build started 12/29/2021 5:14:45 PM.")
    }

    private fun checkError(line : String) {
        assertThat(isError(line)).isTrue()
    }

    private fun checkNotError(line : String) {
        assertThat(isError(line)).isFalse()
    }
}
