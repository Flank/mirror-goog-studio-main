/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.layoutinspector.model

import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LayoutFileDataTest {
    @Test
    @Throws(IOException::class)
    fun testParsingLayoutFile() {
        val file = getTestFile()
        assert(file.exists())

        val fileData = LayoutFileData(file)

        assertNotNull(fileData.bufferedImage)
        assertEquals(1920, fileData.bufferedImage!!.height)
        assertEquals(1080, fileData.bufferedImage!!.width)

        assertNotNull(fileData.node)
        assertEquals(3, fileData.node!!.childCount)
    }
}
