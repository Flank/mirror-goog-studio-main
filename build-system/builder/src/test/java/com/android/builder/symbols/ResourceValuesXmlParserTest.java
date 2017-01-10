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

import com.android.annotations.NonNull;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class ResourceValuesXmlParserTest {

    /**
     * Parses an XML string into a DOM tree.
     *
     * @param xml the XML string
     * @return the resulting DOM tree
     * @throws Exception failed to parse the XML
     */
    @NonNull
    private Document parse(@NonNull String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xml)));
    }

    @Test
    public void parseAttr() throws Exception {
        Document xml =
                parse(""
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
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("attr", "a0", "int", "1"))
                .add(new Symbol("attr", "a1", "int", "2"))
                .add(new Symbol("attr", "a2", "int", "3"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseBoolean() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<bool name=\"a\">true</bool>"
                        + "<bool name=\"b\">false</bool>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("bool", "a", "int", "1"))
                .add(new Symbol("bool", "b", "int", "2"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseColor() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<color name=\"a\">#7fa87f</color>"
                        + "<color name=\"b\">@android:color/black</color>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("color", "a", "int", "1"))
                .add(new Symbol("color", "b", "int", "2"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseStyleable() throws Exception {
        Document xml =
                parse(""
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
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("styleable", "empty", "int[]", "{}"))
                .add(new Symbol("styleable", "oneattr_enums", "int", "1"))
                .add(new Symbol("styleable", "oneattr", "int[]", "{1}"))
                .add(new Symbol("styleable", "twoattrs_flags", "int", "2"))
                .add(new Symbol("styleable", "twoattrs_nothing", "int", "3"))
                .add(new Symbol("styleable", "twoattrs", "int[]", "{2,3}"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseDimen() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<dimen name=\"a\">16dp</dimen>"
                        + "<dimen name=\"b\">@dimen/abc_control_padding_material</dimen>"
                        + "<item name=\"c\" type=\"dimen\">10%</item>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("dimen", "a", "int", "1"))
                .add(new Symbol("dimen", "b", "int", "2"))
                .add(new Symbol("dimen", "c", "int", "3"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseDrawable() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<drawable name=\"foo\">#0cffffff</drawable>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("drawable", "foo", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseId() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<item name=\"foo\" type=\"id\"/>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("id", "foo", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseInteger() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<integer name=\"a\">10</integer>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("integer", "a", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseIntegerArray() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<integer-array name=\"ints\">"
                        + "  <item>0</item>"
                        + "  <item>1</item>"
                        + "</integer-array>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "ints", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parsePlurals() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<plurals name=\"plu\">"
                        + "  <item quantity=\"one\">p0</item>"
                        + "  <item quantity=\"few\">p1</item>"
                        + "  <item quantity=\"other\">p2</item>"
                        + "</plurals>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("plurals", "plu", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseString() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<string name=\"a\">b</string>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("string", "a", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseStringArray() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<string-array name=\"strings\">"
                        + "  <item>foo</item>"
                        + "  <item>bar</item>"
                        + "</string-array>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "strings", "int", "1"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseStyle() throws Exception {
        Document xml =
                parse(""
                        + "<resources>"
                        + "<style name=\"empty\"/>"
                        + "<style name=\"s0\" parent=\"android:Widget\">"
                        + "  <item name=\"android:layout\">@layout/abc_alert_dialog_material</item>"
                        + "</style>"
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("style", "empty", "int", "1"))
                .add(new Symbol("style", "s0", "int", "2"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseTypedArray() throws Exception {
        Document xml =
                parse(""
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
                        + "</resources>");
        SymbolTable table = ResourceValuesXmlParser.parse(xml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("array", "string_refs", "int", "1"))
                .add(new Symbol("array", "colors", "int", "2"))
                .build();

        assertEquals(expected, table);
    }

    @Test
    public void parseItem() throws Exception {
        Document itemXml =
                parse(""
                        + "<resources>"
                        + "<declare-styleable name=\"PieChart\">"
                        + "  <attr name=\"showText\" format=\"boolean\" />"
                        + "  <attr name=\"labelPosition\" format=\"enum\">"
                        + "      <enum name=\"left\" value=\"0\"/>"
                        + "      <enum name=\"right\" value=\"1\"/>"
                        + "  </attr>"
                        + "</declare-styleable>"
                        + "</resources>");

        Document noItemXml =
                parse(""
                        + "<resources>"
                        + "<item type=\"declare-styleable\" name=\"PieChart\">"
                        + "  <attr name=\"showText\" format=\"boolean\" />"
                        + "  <attr name=\"labelPosition\" format=\"enum\">"
                        + "      <enum name=\"left\" value=\"0\"/>"
                        + "      <enum name=\"right\" value=\"1\"/>"
                        + "  </attr>"
                        + "</item>"
                        + "</resources>");

        SymbolTable itemTable = ResourceValuesXmlParser.parse(itemXml, IdProvider.sequential());
        SymbolTable noItemTable = ResourceValuesXmlParser.parse(noItemXml, IdProvider.sequential());

        SymbolTable expected = SymbolTable.builder()
                .add(new Symbol("styleable", "PieChart", "int[]", "{1,2}"))
                .add(new Symbol("styleable", "PieChart_showText", "int", "1"))
                .add(new Symbol("styleable", "PieChart_labelPosition", "int", "2"))
                .build();

        assertEquals(expected, itemTable);
        assertEquals(itemTable, noItemTable);
    }
}
