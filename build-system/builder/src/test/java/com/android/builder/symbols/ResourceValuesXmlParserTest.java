/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.symbols;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.utils.XmlUtils;
import org.junit.Test;

public class ResourceValuesXmlParserTest {

    @Test
    public void parseAttr() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<attr name=\"a0\" format=\"reference|color\"/>"
                        + "<attr name=\"a1\">"
                        + "  <flag name=\"f0\" value=\"0\"/>"
                        + "  <flag name=\"f1\" value=\"1\"/>"
                        + "</attr>"
                        + "<attr name=\"a2\">"
                        + "  <enum name=\"e0\" value=\"0\"/>"
                        + "  <enum name=\"e1\" value=\"1\"/>"
                        + "</attr>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        // We should ignore all elements declared under "attr", except for "enum" which is turned
        // into an "id" Symbol.
        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "a0", "int", 0x7f_04_0001))
                        .add(SymbolTestUtils.createSymbol("attr", "a1", "int", 0x7f_04_0002))
                        .add(SymbolTestUtils.createSymbol("id", "e0", "int", 0x7f_0c_0001))
                        .add(SymbolTestUtils.createSymbol("id", "e1", "int", 0x7f_0c_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "a2", "int", 0x7f_04_0003))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseBoolean() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<bool name=\"a\">true</bool>"
                        + "<bool name=\"b\">false</bool>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("bool", "a", "int", 0x7f_05_0001))
                        .add(SymbolTestUtils.createSymbol("bool", "b", "int", 0x7f_05_0002))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseColor() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<color name=\"a\">#7fa87f</color>"
                        + "<color name=\"b\">@android:color/black</color>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("color", "a", "int", 0x7f_06_0001))
                        .add(SymbolTestUtils.createSymbol("color", "b", "int", 0x7f_06_0002))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseStyleable() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<declare-styleable name=\"empty\"/>"
                        + "<declare-styleable name=\"oneattr\">"
                        + "  <attr name=\"enums\">"
                        + "     <enum name=\"e1\" value=\"1\"/>"
                        + "     <enum name=\"e2\" value=\"2\"/>"
                        + "  </attr>"
                        + "</declare-styleable>"
                        + "<declare-styleable name=\"twoattrs\">"
                        + "  <attr name=\"flags\">"
                        + "     <flag name=\"f0\" value=\"0\"/>"
                        + "     <flag name=\"f1\" value=\"0x01\"/>"
                        + "  </attr>"
                        + "  <attr name=\"nothing\"/>"
                        + "</declare-styleable>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        int tmp = 0x7f040003;

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("styleable", "empty", "int[]", "{}"))
                        .add(SymbolTestUtils.createSymbol("id", "e1", "int", 0x7f_0c_0001))
                        .add(SymbolTestUtils.createSymbol("id", "e2", "int", 0x7f_0c_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "enums", "int", 0x7f_04_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "oneattr_enums", "int", 0x7f_17_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "oneattr", "int[]", "{0x7f170001}"))
                        .add(SymbolTestUtils.createSymbol("attr", "flags", "int", 0x7f_04_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "twoattrs_flags", "int", 0x7f_17_0002))
                        .add(SymbolTestUtils.createSymbol("attr", "nothing", "int", 0x7f_04_0003))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "twoattrs_nothing", "int", 0x7f_17_0003))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "twoattrs",
                                        "int[]",
                                        "{0x7f170002,0x7f170003}"))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseDimen() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<dimen name=\"a\">16dp</dimen>"
                        + "<dimen name=\"b\">@dimen/abc_control_padding_material</dimen>"
                        + "<item name=\"c\" type=\"dimen\">10%</item>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("dimen", "a", "int", 0x7f_08_0001))
                        .add(SymbolTestUtils.createSymbol("dimen", "b", "int", 0x7f_08_0002))
                        .add(SymbolTestUtils.createSymbol("dimen", "c", "int", 0x7f_08_0003))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseDrawable() throws Exception {
        String xml = "<resources>" + "<drawable name=\"foo\">#0cffffff</drawable>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("drawable", "foo", "int", 0x7f_09_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseId() throws Exception {
        String xml = "<resources>" + "<item name=\"foo\" type=\"id\"/>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("id", "foo", "int", 0x7f_0c_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseInteger() throws Exception {
        String xml = "<resources>" + "<integer name=\"a\">10</integer>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("integer", "a", "int", 0x7f_0d_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseIntegerArray() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<integer-array name=\"ints\">"
                        + "  <item>0</item>"
                        + "  <item>1</item>"
                        + "</integer-array>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("array", "ints", "int", 0x7f_03_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parsePlurals() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<plurals name=\"plu\">"
                        + "  <item quantity=\"one\">p0</item>"
                        + "  <item quantity=\"few\">p1</item>"
                        + "  <item quantity=\"other\">p2</item>"
                        + "</plurals>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("plurals", "plu", "int", 0x7f_13_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseString() throws Exception {
        String xml = "<resources>" + "<string name=\"a\">b</string>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("string", "a", "int", 0x7f_15_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseStringArray() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<string-array name=\"strings\">"
                        + "  <item>foo</item>"
                        + "  <item>bar</item>"
                        + "</string-array>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("array", "strings", "int", 0x7f_03_0001))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseStyle() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<style name=\"empty\"/>"
                        + "<style name=\"s0\" parent=\"android:Widget\">"
                        + "  <item name=\"android:layout\">@layout/abc_alert_dialog_material</item>"
                        + "</style>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("style", "empty", "int", 0x7f_16_0001))
                        .add(SymbolTestUtils.createSymbol("style", "s0", "int", 0x7f_16_0002))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseTypedArray() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<array name=\"string_refs\">"
                        + "  <item>@string/s0</item>"
                        + "  <item>@string/s1</item>"
                        + "  <item>@string/s2</item>"
                        + "</array>"
                        + "<array name=\"colors\">"
                        + "  <item>#FFFF0000</item>"
                        + "  <item>#FF00FF00</item>"
                        + "  <item>#FF0000FF</item>"
                        + "</array>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "array", "string_refs", "int", 0x7f_03_0001))
                        .add(SymbolTestUtils.createSymbol("array", "colors", "int", 0x7f_03_0002))
                        .build();

        assertThat(table).isEqualTo(expected);
    }

    @Test
    public void parseItem() throws Exception {
        String noItemXml =
                ""
                        + "<resources>"
                        + "<declare-styleable name=\"PieChart\">"
                        + "  <attr name=\"showText\" format=\"boolean\" />"
                        + "  <attr name=\"labelPosition\" format=\"enum\">"
                        + "      <enum name=\"left\" value=\"0\"/>"
                        + "      <enum name=\"right\" value=\"1\"/>"
                        + "  </attr>"
                        + "</declare-styleable>"
                        + "</resources>";

        String itemXml =
                ""
                        + "<resources>"
                        + "<item type=\"declare-styleable\" name=\"PieChart\">"
                        + "  <item type=\"attr\" name=\"showText\" format=\"boolean\" />"
                        + "  <item type=\"attr\" name=\"labelPosition\" format=\"enum\">"
                        + "      <item type=\"enum\" name=\"left\" value=\"0\"/>"
                        + "      <item type=\"enum\" name=\"right\" value=\"1\"/>"
                        + "  </item>"
                        + "</item>"
                        + "</resources>";

        SymbolTable itemTable =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(itemXml, false), IdProvider.sequential());
        SymbolTable noItemTable =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(noItemXml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "showText", "int", 0x7f_04_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "PieChart_showText", "int", 0x7f_17_0001))
                        .add(SymbolTestUtils.createSymbol("id", "left", "int", 0x7f_0c_0001))
                        .add(SymbolTestUtils.createSymbol("id", "right", "int", 0x7f_0c_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "attr", "labelPosition", "int", 0x7f_04_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable", "PieChart_labelPosition", "int", 0x7f_17_0002))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "styleable",
                                        "PieChart",
                                        "int[]",
                                        "{0x7f170001,0x7f170002}"))
                        .build();

        assertThat(itemTable).isEqualTo(expected);
        assertThat(noItemTable).isEqualTo(itemTable);
    }

    @Test
    public void parseIncorrectStyleable() throws Exception {
        String xml =
                "<resources>"
                        + "<item type=\"declare-styleable\" name=\"PieChart\">"
                        + "  <item type=\"attr\" name=\"showText\" format=\"boolean\" />"
                        + "  <attr name=\"labelPosition\" format=\"enum\"/>"
                        + "  <enum name=\"left\" value=\"0\"/>"
                        + "  <enum name=\"right\" value=\"1\"/>"
                        + "</item>"
                        + "</resources>";

        try {
            ResourceValuesXmlParser.parse(
                    XmlUtils.parseDocument(xml, false), IdProvider.sequential());
            fail();
        } catch (ResourceValuesXmlParseException e) {
            // expected
            assertThat(e.getMessage())
                    .contains(
                            "Illegal type under declare-styleable:"
                                    + " was <enum>, only accepted is <attr>");
        }
    }

    @Test
    public void parseIncorrectType() throws Exception {
        String xml = "<resources>" + "<myType name=\"foo\"/>" + "</resources>";

        try {
            ResourceValuesXmlParser.parse(
                    XmlUtils.parseDocument(xml, false), IdProvider.sequential());
            fail();
        } catch (ResourceValuesXmlParseException e) {
            // expected
            assertThat(e.getMessage()).contains("Unknown resource value XML element 'myType'");
        }
    }

    @Test
    public void parseOtherAaptAcceptedTypes() throws Exception {
        String xml =
                ""
                        + "<resources>\n"
                        + "    <android:color name=\"colorPrimary\">#3F51B5</android:color>\n"
                        + "    <aapt:color name=\"colorPrimaryDark\">#303F9F</aapt:color>\n"
                        + "    <color name=\"colorAccent\">#FF4081</color>\n"
                        + "    <item type=\"declare-styleable\" name=\"foo_declare_styleable\">\n"
                        + "    </item>\n"
                        + "    <item type=\"anim\" name=\"foo_anim\">idk</item>\n"
                        + "    <item type=\"animator\" name=\"foo_animator\">idk</item>\n"
                        + "    <drawable name=\"foo_drawable\">@drawable/a</drawable>\n"
                        + "    <fraction name=\"foo_fraction\">5%</fraction>\n"
                        + "    <integer name=\"foo_integer\">1</integer>\n"
                        + "    <item type=\"menu\" name=\"foo_menu\">idk</item>\n"
                        + "    <item type=\"mipmap\" name=\"foo_mipmap\">idk</item>\n"
                        + "    <item type=\"raw\" name=\"foo_raw\">idk</item>\n"
                        + "    <style name=\"foo_style\">idk</style>\n"
                        + "    <item type=\"transition\" name=\"foo_transition\">idk</item>\n"
                        + "    <item type=\"xml\" name=\"foo_xml\">idk</item>\n"
                        + "    <public />\n"
                        + "</resources>";

        SymbolTable actual =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
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
                                        "styleable", "foo_declare_styleable", "int[]", "{}"))
                        .add(SymbolTestUtils.createSymbol("anim", "foo_anim", "int", 0x7f_01_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "animator", "foo_animator", "int", 0x7f_02_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "drawable", "foo_drawable", "int", 0x7f_09_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "fraction", "foo_fraction", "int", 0x7f_0b_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "integer", "foo_integer", "int", 0x7f_0d_0001))
                        .add(SymbolTestUtils.createSymbol("menu", "foo_menu", "int", 0x7f_10_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "mipmap", "foo_mipmap", "int", 0x7f_11_0001))
                        .add(SymbolTestUtils.createSymbol("raw", "foo_raw", "int", 0x7f_14_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "style", "foo_style", "int", 0x7f_16_0001))
                        .add(
                                SymbolTestUtils.createSymbol(
                                        "transition", "foo_transition", "int", 0x7f_18_0001))
                        .add(SymbolTestUtils.createSymbol("xml", "foo_xml", "int", 0x7f_19_0001))
                        .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void parseEnumBeforeId() throws Exception {
        String xml =
                ""
                        + "<resources>"
                        + "<attr name=\"a0\" format=\"reference|color\"/>"
                        + "<attr name=\"a1\">"
                        + "  <enum name=\"enum1\" value=\"0\"/>"
                        + "  <enum name=\"enum2\" value=\"1\"/>"
                        + "</attr>"
                        + "<id name=\"enum1\"/>"
                        + "<id name=\"nonEnumId\"/>"
                        + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        // We should ignore all elements declared under "attr", except for "enum" which is turned
        // into an "id" Symbol.
        SymbolTable expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("attr", "a0", "int", 0x7f_04_0001))
                        .add(SymbolTestUtils.createSymbol("attr", "a1", "int", 0x7f_04_0002))
                        .add(SymbolTestUtils.createSymbol("id", "enum1", "int", 0x7f_0c_0003))
                        .add(SymbolTestUtils.createSymbol("id", "nonEnumId", "int", 0x7f_0c_0004))
                        .add(SymbolTestUtils.createSymbol("id", "enum2", "int", 0x7f_0c_0002))
                        .build();

        assertThat(table).isEqualTo(expected);
    }
}
