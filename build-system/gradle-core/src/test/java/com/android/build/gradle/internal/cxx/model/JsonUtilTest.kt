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

package com.android.build.gradle.internal.cxx.model

import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonUtilTest {
    @Test
    fun `round trip`() {
        BasicCmakeMock().apply {
            val json = abi.toJsonString()
            val writtenBackAbi = createCxxAbiModelFromJson(json)
            val writtenBackJson = writtenBackAbi.toJsonString()
            assertThat(json).isEqualTo(writtenBackJson)
            assertThat(writtenBackAbi.cxxBuildFolder.parentFile.parentFile.parentFile.path).endsWith(".cxx")
            assertThat(writtenBackAbi.variant.module.ndkVersion.toString()).isEqualTo(
                NDK_DEFAULT_VERSION
            )
        }
    }
}
