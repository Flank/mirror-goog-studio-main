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

package com.android.layoutinspector.parser

import com.android.layoutinspector.model.LayoutFileData
import com.android.layoutinspector.model.getTestFile
import com.android.layoutinspector.model.getTestFileV2
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisplayInfoFactoryTest {
    @Test
    fun testV1ViewNodeParsing() {
        val file = getTestFile()
        val node = LayoutFileData(file).node!!

        val displayInfo = DisplayInfoFactory.createDisplayInfoFromNode(node)
        assertEquals(1080, displayInfo.width)
        assertEquals(1920, displayInfo.height)
        assertTrue(displayInfo.willNotDraw)
    }

    @Test
    fun testV2ViewNodeParsing() {
        val file = getTestFileV2()
        val node = LayoutFileData(file).node!!

        val displayInfo = DisplayInfoFactory.createDisplayInfoFromNode(node)
        assertEquals(1080, displayInfo.width)
        assertEquals(1920, displayInfo.height)
        assertTrue(displayInfo.willNotDraw)
    }
}
