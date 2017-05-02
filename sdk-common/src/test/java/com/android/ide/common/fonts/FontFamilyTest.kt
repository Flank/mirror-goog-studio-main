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

class FontFamilyTest {

    @Test
    fun testConstructorAndGetters() {
        val family = createFontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, "Roboto", "https://fonts.com/roboto/v15/xyz.ttf", "")
        assertThat(family.provider).isEqualTo(FontProvider.GOOGLE_PROVIDER)
        assertThat(family.fontSource).isEqualTo(FontSource.DOWNLOADABLE)
        assertThat(family.name).isEqualTo("Roboto")
        assertThat(family.menu).isEqualTo("https://fonts.com/roboto/v15/xyz.ttf")
        assertThat(family.menuName).isEqualTo("Roboto")
    }

    @Test
    fun testConstructorAndGettersWithSpecifiedMenuName() {
        val family = createFontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, "Alegreya Sans SC", "file:///fonts/alegreya.ttf", "My Alegreya")
        assertThat(family.provider).isEqualTo(FontProvider.GOOGLE_PROVIDER)
        assertThat(family.fontSource).isEqualTo(FontSource.DOWNLOADABLE)
        assertThat(family.name).isEqualTo("Alegreya Sans SC")
        assertThat(family.menu).isEqualTo("file:///fonts/alegreya.ttf")
        assertThat(family.menuName).isEqualTo("My Alegreya")
    }

    private fun createFontFamily(provider: FontProvider,
                                 fontSource: FontSource,
                                 name: String,
                                 menuUrl: String,
                                 menuName: String): FontFamily {
        return FontFamily(provider, fontSource, name, menuUrl, menuName, listOf(MutableFontDetail(400, 100, false, "https://fonts.com/roboto/v15/qrs.ttf", "", false)))
    }
}
