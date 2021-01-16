/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset

class IceboxConfigUtilsTest {
    @get:Rule
    val testDir = TemporaryFolder()

    @Test
    fun defaultPortTest() {
        val grpcInfo = findGrpcInfo("")
        assertThat(grpcInfo.port).isEqualTo(DEFAULT_EMULATOR_GRPC_PORT)
        assertThat(grpcInfo.token).isNull()
    }

    @Test
    fun parseConfigTest() {
        val serial = "5556"
        val port = 1234
        val token = "token"
        val testFile = testDir.newFile()
        testFile.printWriter(Charset.defaultCharset()).use {
            it.println("port.serial=$serial")
            it.println("grpc.port=$port")
            it.println("grpc.token=$token")
        }
        val grpcInfo = findGrpcInfo("emulator-$serial", testFile.toPath())
        assertThat(grpcInfo).isNotNull()
        assertThat(grpcInfo!!.port).isEqualTo(port)
        assertThat(grpcInfo!!.token).isEqualTo(token)
    }
}
