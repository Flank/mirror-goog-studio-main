/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.utils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import junit.framework.TestCase;

public class HtmlBuilderTest extends TestCase {

    public void testAddLink() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.add("Plain.");
        builder.addLink(" (link) ", "runnable:0");
        builder.add("Plain.");
        // Check that the spaces surrounding the link text are not included in the link range
        assertEquals("Plain. <A HREF=\"runnable:0\">(link)</A>Plain.", builder.getHtml());
    }

    public void testAddBold() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.addBold("This is bold");
        assertEquals("<B>This is bold</B>", builder.getHtml());
    }

    public void testAddUnderlined() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.addUnderlined("This is underlined.");
        assertEquals("<U>This is underlined.</U>", builder.getHtml());
    }

    public void testAddItalic() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.addItalic("This is italic");
        assertEquals("<I>This is italic</I>", builder.getHtml());
    }

    public void testNestLinkInBold() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.add("Plain. ");
        builder.beginBold();
        builder.add("Bold. ");
        builder.addLink("mylink", "foo://bar:123");
        builder.endBold();
        assertEquals("Plain. <B>Bold. <A HREF=\"foo://bar:123\">mylink</A></B>",
                     builder.getHtml());
    }

    public void testNestInUnderline() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.add("Plain. ");
        builder.beginUnderline();
        builder.add("Underlined. ");
        builder.addBold("Bold and underlined.");
        builder.endUnderline();
        assertEquals("Plain. <U>Underlined. <B>Bold and underlined.</B></U>",
                     builder.getHtml());
    }

    public void testAddList() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.add("Plain").newline();
        builder.beginList().listItem().add("item 1").listItem().add("item 2").endList();

        assertEquals("Plain<BR/>" +
                     "<DL>" +
                     "<DD>-&NBSP;item 1" +
                     "<DD>-&NBSP;item 2" +
                     "</DL>", builder.getHtml());
    }

    public void testAddLinkWithBeforeAndAfterText() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.addLink("This is the ", "linked text", "!", "foo://bar");
        assertEquals("This is the <A HREF=\"foo://bar\">linked text</A>!", builder.getHtml());
    }

    public void testAddTable() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.beginTable().addTableRow(true, "Header1", "Header2")
          .addTableRow("Data1", "Data2")
          .endTable();
        assertEquals(
          "<table><tr><th>Header1</th><th>Header2</th></tr><tr><td>Data1</td><td>Data2</td></tr></table>",
          builder.getHtml());
    }

    public void testAddTableWithAlignment() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.beginTable("valign=\"top\"").addTableRow("Data1", "Data2").endTable();
        assertEquals(
          "<table><tr><td valign=\"top\">Data1</td><td valign=\"top\">Data2</td></tr></table>",
          builder.getHtml());
    }

    public void testAddDiv() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals("<div>Hello</div>", builder.beginDiv().add("Hello").endDiv().getHtml());
    }

    public void testAddDivWithPadding() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals("<div style=\"padding: 10px; text-color: gray\">Hello</div>",
                     builder.beginDiv("padding: 10px; text-color: gray").add("Hello").endDiv()
                       .getHtml());
    }

    public void testAddParagraph() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals(
                "<p>Hello</p>", builder.beginParagraph().add("Hello").endParagraph().getHtml());
    }

    public void testAddParagraphWithPadding() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals(
                "<p style=\"padding: 10px; text-color: gray\">Hello</p>",
                builder.beginParagraph("padding: 10px; text-color: gray")
                        .add("Hello")
                        .endParagraph()
                        .getHtml());
    }

    public void testAddSpan() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals("<span>Hello</span>", builder.beginSpan().add("Hello").endSpan().getHtml());
    }

    public void testAddSpanWithPadding() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals("<span style=\"padding: 10px; text-color: gray\">Hello</span>",
                     builder.beginSpan("padding: 10px; text-color: gray").add("Hello").endSpan()
                       .getHtml());
    }

    public void testColoredText() {
        HtmlBuilder builder = new HtmlBuilder();
        assertEquals("<FONT color=\"#804020\">This is a test</FONT>",
                builder.coloredText(new Color(128, 64, 32), "This is a test").getHtml());
    }

    public void testAddImage() throws IOException {
        File f = File.createTempFile("img", "png");
        f.deleteOnExit();

        String actual = new HtmlBuilder().addImage(SdkUtils.fileToUrl(f), "preview").getHtml();
        String path = f.getAbsolutePath();

        if (!path.startsWith("/")) {
            path = '/' + path;
        }
        String expected = String.format("<img src='file:%1$s' alt=\"preview\" />", path);
        if (File.separatorChar != '/') {
            // SdkUtil.fileToUrl always returns / as a separator so adjust
            // Windows path accordingly.
            expected = expected.replace(File.separatorChar, '/');
        }
        assertEquals(expected, actual);
    }

    public void testNewlineIfNecessary() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.newlineIfNecessary();
        assertEquals("<BR/>", builder.getHtml());
        builder.newlineIfNecessary();
        assertEquals("<BR/>", builder.getHtml());
        builder.add("a");
        builder.newlineIfNecessary();
        assertEquals("<BR/>a<BR/>", builder.getHtml());
        builder.newline();
        builder.newlineIfNecessary();
        builder.newlineIfNecessary();
        builder.newlineIfNecessary();
        assertEquals("<BR/>a<BR/><BR/>", builder.getHtml());
    }

    public void testAddHeading() {
        HtmlBuilder builder = new HtmlBuilder();
        builder.addHeading("Welcome to Android Studio!", "black");
        assertEquals(
          "<font style=\"font-weight:bold; color:black;\">Welcome to Android Studio!</font>",
          builder.getHtml());
    }
}
