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

class FontDetailTest {

    @Test
    fun testGenerateStyleName() {
        assertThat(generateStyleName(77, false)).isEqualTo("Custom-Light")
        assertThat(generateStyleName(100, false)).isEqualTo("Thin")
        assertThat(generateStyleName(200, false)).isEqualTo("Extra-Light")
        assertThat(generateStyleName(300, false)).isEqualTo("Light")
        assertThat(generateStyleName(400, false)).isEqualTo("Regular")
        assertThat(generateStyleName(500, false)).isEqualTo("Medium")
        assertThat(generateStyleName(600, false)).isEqualTo("Semi-Bold")
        assertThat(generateStyleName(700, false)).isEqualTo("Bold")
        assertThat(generateStyleName(800, false)).isEqualTo("Extra-Bold")
        assertThat(generateStyleName(900, false)).isEqualTo("Black")
        assertThat(generateStyleName(977, false)).isEqualTo("Custom-Bold")
    }

    @Test
    fun testGenerateStyleNameWithItalics() {
        assertThat(generateStyleName(67, true)).isEqualTo("Custom-Light Italic")
        assertThat(generateStyleName(100, true)).isEqualTo("Thin Italic")
        assertThat(generateStyleName(200, true)).isEqualTo("Extra-Light Italic")
        assertThat(generateStyleName(300, true)).isEqualTo("Light Italic")
        assertThat(generateStyleName(400, true)).isEqualTo("Regular Italic")
        assertThat(generateStyleName(500, true)).isEqualTo("Medium Italic")
        assertThat(generateStyleName(600, true)).isEqualTo("Semi-Bold Italic")
        assertThat(generateStyleName(700, true)).isEqualTo("Bold Italic")
        assertThat(generateStyleName(800, true)).isEqualTo("Extra-Bold Italic")
        assertThat(generateStyleName(900, true)).isEqualTo("Black Italic")
        assertThat(generateStyleName(901, true)).isEqualTo("Custom-Bold Italic")
    }

    @Test
    fun testConstructorAndGetters() {
        val family = createFontFamily(800, 120, false, "http://someurl.com/myfont1.ttf", "MyStyle")
        val font = family.fonts[0]
        assertThat(font.family).isSameAs(family)
        assertThat(font.weight).isEqualTo(800)
        assertThat(font.width).isEqualTo(120)
        assertThat(font.italics).isEqualTo(false)
        assertThat(font.fontUrl).isEqualTo("http://someurl.com/myfont1.ttf")
        assertThat(font.styleName).isEqualTo("MyStyle")
    }

    @Test
    fun testConstructorWithGeneratedStyleName() {
        val font = createFontDetail(800, 110, true, "http://someurl.com/myfont2.ttf", "")
        assertThat(font.styleName).isEqualTo("Extra-Bold Italic")
    }

    @Test
    fun testDerivedConstructor() {
        val font = createFontDetail(800, 110, true, "http://someurl.com/myfont2.ttf", "")
        val derived = FontDetail(font, MutableFontDetail(700, 100, false, "whatever", "", false))
        assertThat(derived.family).isSameAs(font.family)
        assertThat(derived.weight).isEqualTo(700)
        assertThat(derived.width).isEqualTo(100)
        assertThat(derived.italics).isEqualTo(false)
        assertThat(derived.fontUrl).isEqualTo("http://someurl.com/myfont2.ttf")
        assertThat(derived.styleName).isEqualTo("Bold")
    }

    companion object {
        internal fun createFontDetail(weight: Int, width: Int, italics: Boolean, url: String, styleName: String): FontDetail {
            val family = createFontFamily(weight, width, italics, url, styleName)
            return family.fonts[0]
        }

        private fun createFontFamily(weight: Int, width: Int, italics: Boolean, url: String, styleName: String): FontFamily {
            return FontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, "MyFont", "http://someurl.com/mymenufont.ttf", "myMenu",
                    listOf(MutableFontDetail(weight, width, italics, url, styleName, false)))
        }

        private fun generateStyleName(weight: Int, italics: Boolean): String {
            val family = FontFamily(FontProvider.GOOGLE_PROVIDER, "San Serif")
            val font = MutableFontDetail(weight, DEFAULT_WIDTH, italics)
            val detail = FontDetail(family, font)
            return detail.styleName
        }
    }
}
