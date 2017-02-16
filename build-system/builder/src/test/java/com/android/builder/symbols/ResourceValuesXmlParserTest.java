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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
                        .add(new Symbol("attr", "a0", "int", "1"))
                        .add(new Symbol("attr", "a1", "int", "2"))
                        .add(new Symbol("id", "e0", "int", "3"))
                        .add(new Symbol("id", "e1", "int", "4"))
                        .add(new Symbol("attr", "a2", "int", "5"))
                        .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("bool", "a", "int", "1"))
                .add(new Symbol("bool", "b", "int", "2"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("color", "a", "int", "1"))
                .add(new Symbol("color", "b", "int", "2"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected =
                SymbolTable.builder()
                        .add(new Symbol("styleable", "empty", "int[]", "{}"))
                        .add(new Symbol("styleable", "oneattr_enums", "int", "1"))
                        .add(new Symbol("attr", "enums", "int", "2"))
                        .add(new Symbol("styleable", "oneattr", "int[]", "{1}"))
                        .add(new Symbol("styleable", "twoattrs_flags", "int", "3"))
                        .add(new Symbol("attr", "flags", "int", "4"))
                        .add(new Symbol("styleable", "twoattrs_nothing", "int", "5"))
                        .add(new Symbol("attr", "nothing", "int", "6"))
                        .add(new Symbol("styleable", "twoattrs", "int[]", "{3,5}"))
                        .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("dimen", "a", "int", "1"))
                .add(new Symbol("dimen", "b", "int", "2"))
                .add(new Symbol("dimen", "c", "int", "3"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseDrawable() throws Exception {
        String xml = "<resources>" + "<drawable name=\"foo\">#0cffffff</drawable>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("drawable", "foo", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseId() throws Exception {
        String xml = "<resources>" + "<item name=\"foo\" type=\"id\"/>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("id", "foo", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseInteger() throws Exception {
        String xml = "<resources>" + "<integer name=\"a\">10</integer>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("integer", "a", "int", "1"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "ints", "int", "1"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("plurals", "plu", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseString() throws Exception {
        String xml = "<resources>" + "<string name=\"a\">b</string>" + "</resources>";
        SymbolTable table =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("string", "a", "int", "1"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "strings", "int", "1"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("style", "empty", "int", "1"))
                .add(new Symbol("style", "s0", "int", "2"))
                .build();

        assertEquals(expected, table);
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

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "string_refs", "int", "1"))
                .add(new Symbol("array", "colors", "int", "2"))
                .build();

        assertEquals(expected, table);
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
                        .add(new Symbol("styleable", "PieChart", "int[]", "{1,3}"))
                        .add(new Symbol("styleable", "PieChart_showText", "int", "1"))
                        .add(new Symbol("attr", "showText", "int", "2"))
                        .add(new Symbol("styleable", "PieChart_labelPosition", "int", "3"))
                        .add(new Symbol("attr", "labelPosition", "int", "4"))
                        .build();

        assertEquals(expected, itemTable);
        assertEquals(itemTable, noItemTable);
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
            assertTrue(
                    e.getMessage()
                            .contains(
                                    "Illegal type under declare-styleable:"
                                            + " was <enum>, only accepted is <attr>"));
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
            assertTrue(e.getMessage().contains("Unknown resource value XML element 'myType'"));
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
                        + "</resources>";

        SymbolTable actual =
                ResourceValuesXmlParser.parse(
                        XmlUtils.parseDocument(xml, false), IdProvider.sequential());

        SymbolTable expected =
                SymbolTable.builder()
                        .add(new Symbol("color", "colorPrimary", "int", "1"))
                        .add(new Symbol("color", "colorPrimaryDark", "int", "2"))
                        .add(new Symbol("color", "colorAccent", "int", "3"))
                        .add(new Symbol("styleable", "foo_declare_styleable", "int[]", "{}"))
                        .add(new Symbol("anim", "foo_anim", "int", "4"))
                        .add(new Symbol("animator", "foo_animator", "int", "5"))
                        .add(new Symbol("drawable", "foo_drawable", "int", "6"))
                        .add(new Symbol("fraction", "foo_fraction", "int", "7"))
                        .add(new Symbol("integer", "foo_integer", "int", "8"))
                        .add(new Symbol("menu", "foo_menu", "int", "9"))
                        .add(new Symbol("mipmap", "foo_mipmap", "int", "10"))
                        .add(new Symbol("raw", "foo_raw", "int", "11"))
                        .add(new Symbol("style", "foo_style", "int", "12"))
                        .add(new Symbol("transition", "foo_transition", "int", "13"))
                        .add(new Symbol("xml", "foo_xml", "int", "14"))
                        .build();

        assertEquals(expected, actual);
    }
}
