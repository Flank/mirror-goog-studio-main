/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.ide.common.symbols

import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ResourceValuesXmlParserTest {

    private var platformAttrSymbols: SymbolTable? = null

    @Before
    fun setup() {
        platformAttrSymbols = SymbolTable.builder().tablePackage("android").build()
    }

    @After
    fun tearDown() {
        platformAttrSymbols = null
    }

    @Test
    fun parseAttr() {
        val xml = """
<resources>
    <attr name="a0" format="reference|color"/>
    <attr name="a1">
        <flag name="f0" value="0"/>
        <flag name="f1" value="1"/>
    </attr>
    <attr name="a2">
        <enum name="e0" value="0"/>
        <enum name="e1" value="1"/>
    </attr>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        // We should ignore all elements declared under "attr", except for "enum" which is turned
        // into an "id" Symbol.
        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "a0", "int", 0x7f_04_0001))
                        .add(SymbolTestUtils.createSymbol("attr", "a1", "int", 0x7f_04_0002))
                        .add(SymbolTestUtils.createSymbol("id", "e0", "int", 0x7f_0b_0001))
                        .add(SymbolTestUtils.createSymbol("id", "e1", "int", 0x7f_0b_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "a2", "int", 0x7f_04_0003))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseBoolean() {
        val xml = """
<resources>
    <bool name="a">true</bool>
    <bool name="b">false</bool>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("bool", "a", "int", 0x7f_05_0001))
                        .add(SymbolTestUtils.createSymbol("bool", "b", "int", 0x7f_05_0002))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseColor() {
        val xml = """
<resources>
    <color name="a">#7fa87f</color>
    <color name="b">@android:color/black</color>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("color", "a", "int", 0x7f_06_0001))
                        .add(SymbolTestUtils.createSymbol("color", "b", "int", 0x7f_06_0002))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseStyleable() {
        val xml = """
<resources>
    <eat-comment/>
    <!-- my super resources -->
    <declare-styleable name="empty"/>
    <declare-styleable name="oneattr">
        <!-- unrelated comment -->
        <eat-comment/>
        <!-- related comment -->
        <attr name="enums">
            <enum name="e1" value="1"/>
            <enum name="e2" value="2"/>
        </attr>
    </declare-styleable>
    <declare-styleable name="twoattrs">
        <attr name="flags">
            <flag name="f0" value="0"/>
            <flag name="f1" value="0x01"/>
        </attr>
        <attr name="nothing"/>
    </declare-styleable>
</resources>"""

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("styleable", "empty", "int[]", "{  }"))
                        .add(SymbolTestUtils.createSymbol("id", "e1", "int", 0x7f_0b_0001))
                        .add(SymbolTestUtils.createSymbol("id", "e2", "int", 0x7f_0b_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "enums", "int", 0x7f_04_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "oneattr",
                                        "int[]",
                                        "{ 0x7f040001 }",
                                        ImmutableList.of("enums")))
                        .add(SymbolTestUtils.createSymbol("attr", "flags", "int", 0x7f_04_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "nothing", "int", 0x7f_04_0003))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "twoattrs",
                                        "int[]",
                                        "{ 0x7f040002, 0x7f040003 }",
                                        ImmutableList.of("flags", "nothing")))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseStyleableWithAndroidAttr() {
        val androidSymbols =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "foo", "int", 0x10_04_0001))
                        .tablePackage("android")
                        .build()

        val xml = """
<resources>
    <declare-styleable name="oneattr">
        <attr name="android:foo"/>
    </declare-styleable>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        androidSymbols)

        val expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "oneattr",
                                        "int[]",
                                        "{ 0x10040001 }",
                                        ImmutableList.of("android:foo")))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseDimen() {
        val xml = """
<resources>
    <dimen name="a">16dp</dimen>
    <dimen name="b">@dimen/abc_control_padding_material</dimen>
    <item name="c" type="dimen">10%</item>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("dimen", "a", "int", 0x7f_07_0001))
                        .add(SymbolTestUtils.createSymbol("dimen", "b", "int", 0x7f_07_0002))
                        .add(SymbolTestUtils.createSymbol("dimen", "c", "int", 0x7f_07_0003))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseDrawable() {
        val xml = """<resources><drawable name="foo">#0cffffff</drawable></resources>"""
        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("drawable", "foo", "int", 0x7f_08_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseId() {
        val xml = """<resources><item name="foo" type="id"/></resources>"""
        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("id", "foo", "int", 0x7f_0b_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseInteger() {
        val xml = """<resources><integer name="a">10</integer></resources>"""
        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("integer", "a", "int", 0x7f_0c_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseIntegerArray() {
        val xml = """
<resources>
    <integer-array name="ints">
        <item>0</item>
        <item>1</item>
    </integer-array>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("array", "ints", "int", 0x7f_03_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parsePlurals() {
        val xml = """
<resources>
    <plurals name="plu">
        <item quantity="one">p0</item>
        <item quantity="few">p1</item>
        <item quantity="other">p2</item>
    </plurals>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("plurals", "plu", "int", 0x7f_12_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseString() {
        val xml = """<resources><string name="a">b</string></resources>"""
        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("string", "a", "int", 0x7f_14_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseStringArray() {
        val xml = """
<resources>
    <string-array name="strings">
        <item>foo</item>
        <item>bar</item>
    </string-array>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("array", "strings", "int", 0x7f_03_0001))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseStyle() {
        val xml = """
<resources>
    <style name="empty"/>
    <style name="s0" parent="android:Widget">
        <item name="android:layout">@layout/abc_alert_dialog_material</item>
    </style>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("style", "empty", "int", 0x7f_15_0001))
                        .add(SymbolTestUtils.createSymbol("style", "s0", "int", 0x7f_15_0002))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseTypedArray() {
        val xml = """
<resources>
    <array name="string_refs">
        <item>@string/s0</item>
        <item>@string/s1</item>
        <item>@string/s2</item>
    </array>
    <array name="colors">
        <item>#FFFF0000</item>
        <item>#FF00FF00</item>
        <item>#FF0000FF</item>
    </array>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "array", "string_refs", "int", 0x7f_03_0001))
                        .add(SymbolTestUtils.createSymbol("array", "colors", "int", 0x7f_03_0002))
                        .build()

        assertThat(table).isEqualTo(expected)
    }

    @Test
    fun parseItem() {
        val noItemXml = """
<resources>
    <declare-styleable name="PieChart">
        <attr name="showText" format="boolean" />
        <attr name="labelPosition" format="enum">
            <enum name="left" value="0"/>
            <enum name="right" value="1"/>
        </attr>
    </declare-styleable>
</resources>""".trimIndent()

        val itemXml = """
<resources>
    <item type="styleable" name="PieChart">
        <item type="attr" name="showText" format="boolean" />
        <item type="attr" name="labelPosition" format="enum">
            <item type="enum" name="left" value="0"/>
            <item type="enum" name="right" value="1"/>
        </item>
    </item>
</resources>""".trimIndent()

        val itemTable =
                parseValuesResource(
                        XmlUtils.parseDocument(itemXml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)
        val noItemTable =
                parseValuesResource(
                        XmlUtils.parseDocument(noItemXml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "showText", "int", 0x7f_04_0001))
                        .add(SymbolTestUtils.createSymbol("id", "left", "int", 0x7f_0b_0001))
                        .add(SymbolTestUtils.createSymbol("id", "right", "int", 0x7f_0b_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "attr", "labelPosition", "int", 0x7f_04_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "PieChart",
                                        "int[]",
                                        "{ 0x7f040001, 0x7f040002 }",
                                        ImmutableList.of("showText", "labelPosition")))
                        .build()

        assertThat(itemTable).isEqualTo(expected)
        assertThat(noItemTable).isEqualTo(itemTable)
    }

    @Test
    fun parseIncorrectStyleable() {
        val xml = """
<resources>
    <item type="styleable" name="PieChart">
        <item type="attr" name="showText" format="boolean"/>
        <attr name="labelPosition" format="enum"/>
        <enum name="left" value="0"/>
        <enum name="right" value="1"/>
    </item>
</resources>""".trimIndent()

        try {
            parseValuesResource(
                    XmlUtils.parseDocument(xml, true),
                    IdProvider.sequential(),
                    platformAttrSymbols)
            fail()
        } catch (e: ResourceValuesXmlParseException) {
            // expected
            assertThat(e.message).contains(
                    "Illegal type under declare-styleable: was <enum>, only accepted is <attr>")
        }
    }

    @Test
    fun parseIncorrectType() {
        val xml = """<resources><myType name="foo"/></resources>"""

        try {
            parseValuesResource(
                    XmlUtils.parseDocument(xml, true),
                    IdProvider.sequential(),
                    platformAttrSymbols)
            fail()
        } catch (e: ResourceValuesXmlParseException) {
            // expected
            assertThat(e.message).contains("myType")
            assertThat(e.message).contains("foo")
        }
    }

    @Test
    fun parseOtherAaptAcceptedTypes() {
        val xml = """
<resources xmlns:android="http://schemas.android.com/apk/res/anroid" xmlns:aapt="http://schemas.android.com/aapt">
    <android:color name="colorPrimary">#3F51B5</android:color>
    <aapt:color name="colorPrimaryDark">#303F9F</aapt:color>
    <color name="colorAccent">#FF4081</color>
    <item type="styleable" name="foo_declare_styleable">
    </item>
    <item type="anim" name="foo_anim">idk</item>
    <item type="animator" name="foo_animator">idk</item>
    <drawable name="foo_drawable">@drawable/a</drawable>
    <fraction name="foo_fraction">5%</fraction>
    <integer name="foo_integer">1</integer>
    <item type="menu" name="foo_menu">idk</item>
    <item type="mipmap" name="foo_mipmap">idk</item>
    <item type="raw" name="foo_raw">idk</item>
    <style name="foo_style">idk</style>
    <item type="transition" name="foo_transition">idk</item>
    <item type="xml" name="foo_xml">idk</item>
    <public />
</resources>"""

        val actual =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        val expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "color", "colorPrimary", "int", 0x7f_06_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "color", "colorPrimaryDark", "int", 0x7f_06_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "color", "colorAccent", "int", 0x7f_06_0003))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "foo_declare_styleable", "int[]", "{  }"))
                        .add(SymbolTestUtils.createSymbol("anim", "foo_anim", "int", 0x7f_01_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "animator", "foo_animator", "int", 0x7f_02_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "foo_drawable", "int", 0x7f_08_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "fraction", "foo_fraction", "int", 0x7f_0a_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "integer", "foo_integer", "int", 0x7f_0c_0001))
                        .add(SymbolTestUtils.createSymbol("menu", "foo_menu", "int", 0x7f_0f_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "mipmap", "foo_mipmap", "int", 0x7f_10_0001))
                        .add(SymbolTestUtils.createSymbol("raw", "foo_raw", "int", 0x7f_13_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "style", "foo_style", "int", 0x7f_15_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "transition", "foo_transition", "int", 0x7f_17_0001))
                        .add(SymbolTestUtils.createSymbol("xml", "foo_xml", "int", 0x7f_18_0001))
                        .build()

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun parseEnumBeforeId() {
        val xml = """
<resources>
    <attr name="a0" format="reference|color"/>
    <attr name="a1">
        <enum name="enum1" value="0"/>
          <enum name="enum2" value="1"/>
    </attr>
    <id name="enum1"/>
    <id name="nonEnumId"/>
</resources>""".trimIndent()

        val table =
                parseValuesResource(
                        XmlUtils.parseDocument(xml, true),
                        IdProvider.sequential(),
                        platformAttrSymbols)

        // We should ignore all elements declared under "attr", except for "enum" which is turned
        // into an "id" Symbol.
        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "a0", "int", 0x7f_04_0001))
                        .add(SymbolTestUtils.createSymbol("attr", "a1", "int", 0x7f_04_0002))
                        .add(SymbolTestUtils.createSymbol("id", "enum1", "int", 0x7f_0b_0003))
                        .add(SymbolTestUtils.createSymbol("id", "nonEnumId", "int", 0x7f_0b_0004))
                        .add(SymbolTestUtils.createSymbol("id", "enum2", "int", 0x7f_0b_0002))
                        .build()

        assertThat(table).isEqualTo(expected)
    }
}
