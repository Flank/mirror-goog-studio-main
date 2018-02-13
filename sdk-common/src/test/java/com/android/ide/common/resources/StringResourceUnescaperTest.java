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
package com.android.ide.common.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import org.junit.Test;

public final class StringResourceUnescaperTest {

    @Test
    public void unescapeCharacterDataEmpty() {
        assertUnescapedXmlEquals("", "");
    }

    @Test
    public void unescapeCharacterDataDecimalReference() {
        assertUnescapedXmlEquals("&#38;", "&#38;");
    }

    @Test
    public void unescapeCharacterDataHexadecimalReference() {
        assertUnescapedXmlEquals("&#x26;", "&#x26;");
    }

    @Test
    public void unescapeCharacterDataFirstQuestionMark() {
        assertUnescapedXmlEquals("???", "\\???");

        assertUnescapedXmlEquals(
                "?<xliff:g id=\"id\">?</xliff:g>?", "\\?<xliff:g id=\"id\">?</xliff:g>?");
        assertUnescapedXmlEquals(
                "?<xliff:g id=\"id\">?</xliff:g>?", "\\?<xliff:g id='id'>?</xliff:g>?");

        assertUnescapedXmlEquals("?<![CDATA[?]]>?", "\\?<![CDATA[?]]>?");
    }

    @Test
    public void unescapeCharacterDataFirstAtSign() {
        assertUnescapedXmlEquals("@@@", "\\@@@");

        assertUnescapedXmlEquals(
                "@<xliff:g id=\"id\">@</xliff:g>@", "\\@<xliff:g id=\"id\">@</xliff:g>@");
        assertUnescapedXmlEquals(
                "@<xliff:g id=\"id\">@</xliff:g>@", "\\@<xliff:g id='id'>@</xliff:g>@");

        assertUnescapedXmlEquals("@<![CDATA[@]]>@", "\\@<![CDATA[@]]>@");
    }

    @Test
    public void unescapeCharacterDataQuotationMarks() {
        assertUnescapedXmlEquals("\"", "\\\"");

        assertUnescapedXmlEquals(
                "\"<xliff:g id=\"id\">\"</xliff:g>\"", "\\\"<xliff:g id=\"id\">\\\"</xliff:g>\\\"");
        assertUnescapedXmlEquals(
                "\"<xliff:g id=\"id\">\"</xliff:g>\"", "\\\"<xliff:g id='id'>\\\"</xliff:g>\\\"");

        assertUnescapedXmlEquals("\"<![CDATA[\"]]>\"", "\\\"<![CDATA[\"]]>\\\"");
    }

    @Test
    public void unescapeCharacterDataBackslashes() {
        assertUnescapedXmlEquals("\\", "\\\\");

        assertUnescapedXmlEquals(
                "\\<xliff:g id=\"id\">\\</xliff:g>\\", "\\\\<xliff:g id=\"id\">\\\\</xliff:g>\\\\");
        assertUnescapedXmlEquals(
                "\\<xliff:g id=\"id\">\\</xliff:g>\\", "\\\\<xliff:g id='id'>\\\\</xliff:g>\\\\");

        assertUnescapedXmlEquals("\\<![CDATA[\\]]>\\", "\\\\<![CDATA[\\]]>\\\\");
    }

    @Test
    public void unescapeCharacterDataNewlines() {
        assertUnescapedXmlEquals("\n", "\\n");

        assertUnescapedXmlEquals(
                "\n<xliff:g id=\"id\">\n</xliff:g>\n", "\\n<xliff:g id=\"id\">\\n</xliff:g>\\n");
        assertUnescapedXmlEquals(
                "\n<xliff:g id=\"id\">\n</xliff:g>\n", "\\n<xliff:g id='id'>\\n</xliff:g>\\n");

        assertUnescapedXmlEquals("\n<![CDATA[\n]]>\n", "\\n<![CDATA[\n]]>\\n");
    }

    @Test
    public void unescapeCharacterDataTabs() {
        assertUnescapedXmlEquals("\t", "\\t");

        assertUnescapedXmlEquals(
                "\t<xliff:g id=\"id\">\t</xliff:g>\t", "\\t<xliff:g id=\"id\">\\t</xliff:g>\\t");
        assertUnescapedXmlEquals(
                "\t<xliff:g id=\"id\">\t</xliff:g>\t", "\\t<xliff:g id='id'>\\t</xliff:g>\\t");

        assertUnescapedXmlEquals("\t<![CDATA[\t]]>\t", "\\t<![CDATA[\t]]>\\t");
    }

    @Test
    public void unescapeCharacterDataApostrophesLeadingAndTrailingSpaces() {
        assertUnescapedXmlEquals("'", "\\'");
        assertUnescapedXmlEquals("' ", "\"' \"");
        assertUnescapedXmlEquals(" '", "\" '\"");
        assertUnescapedXmlEquals(" ' ", "\" ' \"");

        assertUnescapedXmlEquals(
                "'<xliff:g id=\"id\">'</xliff:g>'", "\\'<xliff:g id=\"id\">\\'</xliff:g>\\'");
        assertUnescapedXmlEquals(
                "'<xliff:g id=\"id\">'</xliff:g>' ", "\"'<xliff:g id=\"id\">'</xliff:g>' \"");
        assertUnescapedXmlEquals(
                " '<xliff:g id=\"id\">'</xliff:g>'", "\" '<xliff:g id=\"id\">'</xliff:g>'\"");
        assertUnescapedXmlEquals(
                " '<xliff:g id=\"id\">'</xliff:g>' ", "\" '<xliff:g id=\"id\">'</xliff:g>' \"");

        assertUnescapedXmlEquals(
                "'<xliff:g id=\"id\">'</xliff:g>'", "\\'<xliff:g id='id'>\\'</xliff:g>\\'");
        assertUnescapedXmlEquals(
                "'<xliff:g id=\"id\">'</xliff:g>' ", "\"'<xliff:g id='id'>'</xliff:g>' \"");
        assertUnescapedXmlEquals(
                " '<xliff:g id=\"id\">'</xliff:g>'", "\" '<xliff:g id='id'>'</xliff:g>'\"");
        assertUnescapedXmlEquals(
                " '<xliff:g id=\"id\">'</xliff:g>' ", "\" '<xliff:g id='id'>'</xliff:g>' \"");

        assertUnescapedXmlEquals("'<![CDATA[']]>'", "\\'<![CDATA[']]>\\'");
        assertUnescapedXmlEquals("'<![CDATA[']]>' ", "\"'<![CDATA[']]>' \"");
        assertUnescapedXmlEquals(" '<![CDATA[']]>'", "\" '<![CDATA[']]>'\"");
        assertUnescapedXmlEquals(" '<![CDATA[']]>' ", "\" '<![CDATA[']]>' \"");
    }

    @Test
    public void unescapeCharacterDataInvalidXml() {
        try {
            StringResourceUnescaper.unescapeCharacterData("<");
            fail();
        } catch (IllegalArgumentException exception) {
            // Expected
        }
    }

    @Test
    public void unescapeCharacterDataEntities() {
        assertUnescapedXmlEquals("&amp;", "&amp;");
        assertUnescapedXmlEquals("&apos;", "&apos;");
        assertUnescapedXmlEquals("&gt;", "&gt;");
        assertUnescapedXmlEquals("&lt;", "&lt;");
        assertUnescapedXmlEquals("&quot;", "&quot;");
    }

    @Test
    public void unescapeCharacterDataEmptyElement() {
        assertUnescapedXmlEquals("<br/>", "<br/>");
    }

    @Test
    public void unescapeCharacterDataComment() {
        assertUnescapedXmlEquals("<!-- This is a comment -->", "<!-- This is a comment -->");
    }

    @Test
    public void unescapeCharacterDataProcessingInstruction() {
        assertUnescapedXmlEquals(
                "<?xml-stylesheet type=\"text/css\" href=\"style.css\"?>",
                "<?xml-stylesheet type=\"text/css\" href=\"style.css\"?>");
    }

    private static void assertUnescapedXmlEquals(
            @NonNull String expectedUnescapedXml, @NonNull String xml) {
        assertEquals(expectedUnescapedXml, StringResourceUnescaper.unescapeCharacterData(xml));
    }
}
