/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.usb.parser

import com.android.testutils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class LinuxParserTest {
    private lateinit var parser: OutputParser

    @Before
    fun setup() {
        parser = LinuxParser()
    }

    @Test
    fun parseOutput() {
        val file = TestUtils.getWorkspaceFile("tools/base/usb-devices/testData/linux.txt");
        val devices = parser.parse(file.inputStream())
        assertEquals(12, devices.size)
        assertEquals("Intel Corp.", devices[0].name)
        assertEquals("0x8087", devices[0].vendorId)
        assertEquals("0x8002", devices[0].productId)
    }
}
