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

package com.android.ide.common.fonts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MutableDetailTest {
    @Test
    fun testMatch() {
        val font1 = FontDetailTest.createFontDetail(
                400, 100, false, "http://someurl.com/myfont1.ttf", "MyStyle")

        assertThat(MutableFontDetail(400, 100, false).match(font1)).isEqualTo(0)
        assertThat(MutableFontDetail(400, 100, false).match(font1)).isEqualTo(0)
        assertThat(MutableFontDetail(300, 100, false).match(font1)).isEqualTo(100)
        assertThat(MutableFontDetail(500, 100, false).match(font1)).isEqualTo(100)
        assertThat(MutableFontDetail(900, 100, false).match(font1)).isEqualTo(500)
        assertThat(MutableFontDetail(400, 90, false).match(font1)).isEqualTo(10)
        assertThat(MutableFontDetail(400, 100, true).match(font1)).isEqualTo(50)
        assertThat(MutableFontDetail(700, 120, true).match(font1)).isEqualTo(370)
    }

    @Test
    fun testFindBestMatch() {
        val font1 = FontDetailTest.createFontDetail(
                400, 100, false, "http://someurl.com/myfont1.ttf", "MyStyle")
        val font2 = FontDetailTest.createFontDetail(
                400, 100, true, "http://someurl.com/myfont2.ttf", "MyStyle")
        val font3 = FontDetailTest.createFontDetail(
                700, 100, false, "http://someurl.com/myfont3.ttf", "MyStyle")
        val font4 = FontDetailTest.createFontDetail(
                700, 100, true, "http://someurl.com/myfont4.ttf", "MyStyle")
        val fonts = listOf(font1, font2, font3, font4)

        assertThat(MutableFontDetail(900, 100, true).findBestMatch(fonts)).isEqualTo(font4)
    }
}
