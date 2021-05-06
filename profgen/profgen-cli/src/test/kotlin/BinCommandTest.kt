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
package com.android.tools.profgen.cli

import com.google.common.truth.Truth
import kotlinx.cli.ExperimentalCli
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.fail

@ExperimentalCli
class BinCommandTest {

    @Test
    fun test() {
        val command = BinCommand()
        command.parse(arrayOf("--apk", "fake", "--output", "out", "bla"))
        try {
            command.execute()
            fail("should fail")
        } catch (e: IllegalArgumentException) {
            Truth.assertThat(e).hasMessageThat().contains("File not found")
        }
    }
}
