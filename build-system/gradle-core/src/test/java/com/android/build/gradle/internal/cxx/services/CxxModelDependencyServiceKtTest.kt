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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.google.common.truth.Truth.assertThat

import org.junit.Test
import java.io.File

class CxxModelDependencyServiceKtTest {

    private fun CxxAbiModel.writeAndroidGradleBuildJson(otherBuildFile : String) {
        jsonFile.parentFile.mkdirs()
        jsonFile.writeText("""
            {
              "buildFiles": [
                "${variant.module.makeFile.path.replace("\\", "\\\\")}",
                "$otherBuildFile"
              ]
            }
        """.trimIndent())
    }

    @Test
    fun `simple dependency check`() {
        BasicCmakeMock().apply {
            abi.writeAndroidGradleBuildJson("otherBuildFile.cmake")
            val buildFiles = module.jsonGenerationInputDependencyFileArray(listOf(abi))
            assertThat(buildFiles.toList()).containsExactly(
                variant.module.makeFile,
                File("otherBuildFile.cmake")
            )
        }
    }
}