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

package com.android.build.gradle.integration.application

import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RenderScriptLinkerTest {
    private data class AbiData (
        val toolchain: String,
        val linkerArgs: Array<String>
    )


    private val expected32BitAbis = mapOf(
        "armeabi-v7a" to AbiData(
            "armv7-none-linux-gnueabi",
            arrayOf(
                "-dynamic-linker",
                "/system/bin/linker",
                "-X",
                "-m",
                "armelf_linux_eabi")
        ),
        "mips" to AbiData(
            "mipsel-unknown-linux",
            arrayOf("-EL")
        ),
        "x86" to AbiData(
            "i686-unknown-linux",
            arrayOf("-m", "elf_i386")
        )
    )

    private val expected64BitAbis = mapOf(
        "arm64-v8a" to AbiData(
            "aarch64-linux-android",
            arrayOf("-X", "--fix-cortex-a53-843419")
        ),

        "x86_64" to AbiData(
            "x86_64-unknown-linux",
            arrayOf("-m", "elf_x86_64")
        )
    )

    @Test
    fun testAbiNewLinkerArgs() {
        val abis32NewLinker = RenderScriptProcessor.getAbis("32")
        val abis64NewLinker = RenderScriptProcessor.getAbis("64")

        assertThat(abis32NewLinker!!.map{ it.device })
            .containsExactlyElementsIn(expected32BitAbis.keys)
        assertThat(abis64NewLinker!!.map{ it.device })
            .containsExactlyElementsIn(expected64BitAbis.keys)

        for(abi in abis32NewLinker) {
            assertThat(abi.toolchain).isEqualTo(expected32BitAbis.getValue(abi.device).toolchain)
            assertThat(abi.linker).isEqualTo(BuildToolInfo.PathId.LLD)
            assertThat(abi.getLinkerArgs().toList()).containsExactlyElementsIn(
                arrayOf("-flavor", "ld") + expected32BitAbis.getValue(abi.device).linkerArgs)
        }

        for(abi in abis64NewLinker) {
            assertThat(abi.toolchain).isEqualTo(expected64BitAbis.getValue(abi.device).toolchain)
            assertThat(abi.linker).isEqualTo(BuildToolInfo.PathId.LLD)
            assertThat(abi.getLinkerArgs().toList()).containsExactlyElementsIn(
                arrayOf("-flavor", "ld") + expected64BitAbis.getValue(abi.device).linkerArgs)
        }
    }
}
