/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.XMLNS;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import junit.framework.TestCase;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@SuppressWarnings("javadoc")
public class XmlUtilsTest extends TestCase {
    public void testlookupNamespacePrefix() throws Exception {
        // Setup
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element rootElement = document.createElement("root");
        Attr attr = document.createAttributeNS(SdkConstants.XMLNS_URI,
                "xmlns:customPrefix");
        attr.setValue(SdkConstants.ANDROID_URI);
        rootElement.getAttributes().setNamedItemNS(attr);
        document.appendChild(rootElement);
        Element root = document.getDocumentElement();
        root.appendChild(document.createTextNode("    "));
        Element foo = document.createElement("foo");
        root.appendChild(foo);
        root.appendChild(document.createTextNode("    "));
        Element bar = document.createElement("bar");
        root.appendChild(bar);
        Element baz = document.createElement("baz");
        root.appendChild(baz);

        String prefix = XmlUtils.lookupNamespacePrefix(baz, SdkConstants.ANDROID_URI);
        assertEquals("customPrefix", prefix);

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/tools", "tools", false);
        assertEquals("tools", prefix);

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/apk/res/my/pkg", "app", false);
        assertEquals("app", prefix);
        assertFalse(declaresNamespace(document, "http://schemas.android.com/apk/res/my/pkg"));

        prefix = XmlUtils.lookupNamespacePrefix(baz,
                "http://schemas.android.com/apk/res/my/pkg", "app", true /*create*/);
        assertEquals("app", prefix);
        assertTrue(declaresNamespace(document, "http://schemas.android.com/apk/res/my/pkg"));
    }

    private static boolean declaresNamespace(Document document, String uri) {
        NamedNodeMap attributes = document.getDocumentElement().getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getName();
            if (name.startsWith(XMLNS) && uri.equals(attribute.getValue())) {
                return true;
            }
        }

        return false;
    }

    public void testToXmlAttributeValue() throws Exception {
        assertEquals("", XmlUtils.toXmlAttributeValue(""));
        assertEquals("foo", XmlUtils.toXmlAttributeValue("foo"));
        assertEquals("foo&lt;bar", XmlUtils.toXmlAttributeValue("foo<bar"));
        assertEquals("foo>bar", XmlUtils.toXmlAttributeValue("foo>bar"));

        assertEquals("&quot;", XmlUtils.toXmlAttributeValue("\""));
        assertEquals("&apos;", XmlUtils.toXmlAttributeValue("'"));
        assertEquals("foo&quot;b&apos;&apos;ar",
                XmlUtils.toXmlAttributeValue("foo\"b''ar"));
        assertEquals("&lt;&quot;&apos;>&amp;", XmlUtils.toXmlAttributeValue("<\"'>&"));
    }

    public void testFromXmlAttributeValue() throws Exception {
        assertEquals("", XmlUtils.fromXmlAttributeValue(""));
        assertEquals("foo", XmlUtils.fromXmlAttributeValue("foo"));
        assertEquals("foo<bar", XmlUtils.fromXmlAttributeValue("foo&lt;bar"));
        assertEquals("foo<bar<bar>foo", XmlUtils.fromXmlAttributeValue("foo&lt;bar&lt;bar&gt;foo"));
        assertEquals("foo>bar", XmlUtils.fromXmlAttributeValue("foo>bar"));

        assertEquals("\"", XmlUtils.fromXmlAttributeValue("&quot;"));
        assertEquals("'", XmlUtils.fromXmlAttributeValue("&apos;"));
        assertEquals("foo\"b''ar", XmlUtils.fromXmlAttributeValue("foo&quot;b&apos;&apos;ar"));
        assertEquals("<\"'>&", XmlUtils.fromXmlAttributeValue("&lt;&quot;&apos;>&amp;"));
        assertEquals("a\nb\nc", XmlUtils.fromXmlAttributeValue("a&#xA;b&#xA;c"));
    }

    public void testAppendXmlAttributeValue() throws Exception {
        StringBuilder sb = new StringBuilder();
        XmlUtils.appendXmlAttributeValue(sb, "<\"'>&\n\n]]>");
        assertEquals("&lt;&quot;&apos;>&amp;&#xA;&#xA;]]&gt;", sb.toString());
    }

    public void testToXmlTextValue() throws Exception {
        assertEquals("&lt;\"'>&amp;\n", XmlUtils.toXmlTextValue("<\"'>&\n"));
    }

    public void testAppendXmlTextValue() throws Exception {
        StringBuilder sb = new StringBuilder();
        XmlUtils.appendXmlTextValue(sb, "<\"'>&\n");
        assertEquals("&lt;\"'>&amp;\n", sb.toString());
    }

    public void testHasChildren() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        assertFalse(XmlUtils.hasElementChildren(document));
        document.appendChild(document.createElement("A"));
        Element a = document.getDocumentElement();
        assertFalse(XmlUtils.hasElementChildren(a));
        a.appendChild(document.createTextNode("foo"));
        assertFalse(XmlUtils.hasElementChildren(a));
        Element b = document.createElement("B");
        a.appendChild(b);
        assertTrue(XmlUtils.hasElementChildren(a));
        assertFalse(XmlUtils.hasElementChildren(b));
    }

    public void testToXml() throws Exception {
        Document doc = createEmptyPlainDocument();
        assertNotNull(doc);
        Element root = doc.createElement("myroot");
        doc.appendChild(root);
        root.setAttribute("foo", "bar");
        root.setAttribute("baz", "baz");
        Element child = doc.createElement("mychild");
        root.appendChild(child);
        Element child2 = doc.createElement("hasComment");
        root.appendChild(child2);
        Node comment = doc.createComment("This is my comment");
        child2.appendChild(comment);
        Element child3 = doc.createElement("hasText");
        root.appendChild(child3);
        Node text = doc.createTextNode("  This is my text  ");
        child3.appendChild(text);

        String xml = XmlUtils.toXml(doc);
        assertEquals(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<myroot baz=\"baz\" foo=\"bar\"><mychild/><hasComment><!--This is my comment--></hasComment><hasText>  This is my text  </hasText></myroot>",
                xml);
    }

    public void testToXml2() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string \n"
                + "        name=\"description_search\">Search</string>\n"
                + "    <string \n"
                + "        name=\"description_map\">Map</string>\n"
                + "    <string\n"
                + "         name=\"description_refresh\">Refresh</string>\n"
                + "    <string \n"
                + "        name=\"description_share\">Share</string>\n"
                + "</resources>";

        Document doc = parse(xml);

        String formatted = XmlUtils.toXml(doc);
        assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"description_search\">Search</string>\n"
                + "    <string name=\"description_map\">Map</string>\n"
                + "    <string name=\"description_refresh\">Refresh</string>\n"
                + "    <string name=\"description_share\">Share</string>\n"
                + "</resources>",
                formatted);
    }

    public void testToXml3() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root>\n"
                + "    <!-- ============== -->\n"
                + "    <!-- Generic styles -->\n"
                + "    <!-- ============== -->\n"
                + "</root>";
        Document doc = parse(xml);

        String formatted = XmlUtils.toXml(doc);
        assertEquals(xml, formatted);
    }

    public void testToXml3b() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "  <!-- ============== -->\n"
                + "  <!-- Generic styles -->\n"
                + "         <!-- ============== -->\n"
                + " <string     name=\"test\">test</string>\n"
                + "</resources>";
        Document doc = parse(xml);

        String formatted = XmlUtils.toXml(doc);
        assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "  <!-- ============== -->\n"
                + "  <!-- Generic styles -->\n"
                + "         <!-- ============== -->\n"
                + " <string name=\"test\">test</string>\n"
                + "</resources>",
                formatted);
    }


    public void testToXml4() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- ============== -->\n"
                + "<!-- Generic styles -->\n"
                + "<!-- ============== -->\n"
                + "<root/>";
        Document doc = parse(xml);

        xml = XmlUtils.toXml(doc);
        assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- ============== --><!-- Generic styles --><!-- ============== --><root/>",
                xml);
    }

    public void testToXml5() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root>\n"
                + "    <!-- <&'>\" -->\n"
                + "</root>";
        Document doc = parse(xml);

        String formatted = XmlUtils.toXml(doc);
        assertEquals(xml, formatted);
    }

    public void testToXml6() throws Exception {
        // Check CDATA
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string \n"
                + "        name=\"description_search\">Search</string>\n"
                + "    <string name=\"map_at\">At %1$s:<![CDATA[<br><b>%2$s</b>]]></string>\n"
                + "    <string name=\"map_now_playing\">Now playing:\n"
                + "<![CDATA[\n"
                + "<br><b>%1$s</b>\n"
                + "]]></string>\n"
                + "</resources>";

        Document doc = parse(xml);

        String formatted = XmlUtils.toXml(doc);
        assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string name=\"description_search\">Search</string>\n"
                + "    <string name=\"map_at\">At %1$s:<![CDATA[<br><b>%2$s</b>]]></string>\n"
                + "    <string name=\"map_now_playing\">Now playing:\n"
                + "<![CDATA[\n"
                + "<br><b>%1$s</b>\n"
                + "]]></string>\n"
                + "</resources>",
                formatted);
    }

    public void testPositionAwareXmlXmlBuilder() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "    <string \n"
                + "        name=\"description_search\">Search</string>\n"
                + "    <string \n"
                + "        name=\"description_map\">Map</string>\n"
                + "    <string\n"
                + "         name=\"description_refresh\">Refresh</string>\n"
                + "    <string \n"
                + "        name=\"description_share\">Share</string>\n"
                + "</resources>";

        Document doc = PositionXmlParser.parse(xml);

        Node string1 = doc.getFirstChild().getFirstChild().getNextSibling();
        XmlUtils.attachSourceFile(string1, new SourceFile("source for first string"));

        Node string2 = string1.getNextSibling().getNextSibling();
        XmlUtils.attachSourceFile(string2, new SourceFile("source for second string"));

        Map<SourcePosition, SourceFilePosition> positions = Maps.newLinkedHashMap();

        String formatted = XmlUtils.toXml(doc, positions);
        assertEquals(""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"description_search\">Search</string>\n"
                        + "    <string name=\"description_map\">Map</string>\n"
                        + "    <string name=\"description_refresh\">Refresh</string>\n"
                        + "    <string name=\"description_share\">Share</string>\n"
                        + "</resources>",
                formatted);

        assertEquals(
                new SourceFilePosition(
                        new SourceFile("source for first string"),
                        new SourcePosition(2, 4, 55, 3, 49, 113)),
                positions.get(new SourcePosition(2, 4, 55, 2, 53, 104)));

        assertEquals(
                new SourceFilePosition(
                        new SourceFile("source for second string"),
                        new SourcePosition(4, 4, 118, 5, 43, 170)),
                positions.get(new SourcePosition(3, 4, 109, 3, 47, 152)));

    }

    @Nullable
    private static Document createEmptyPlainDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;
        builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    @Nullable
    private static Document parse(String xml) throws Exception {
        return XmlUtils.parseDocumentSilently(xml, true);
    }

    public void testFormatFloatValue() throws Exception {
        assertEquals("1", XmlUtils.formatFloatValue(1.0f));
        assertEquals("2", XmlUtils.formatFloatValue(2.0f));
        assertEquals("1.5", XmlUtils.formatFloatValue(1.5f));
        assertEquals("1.5", XmlUtils.formatFloatValue(1.50f));
        assertEquals("1.51", XmlUtils.formatFloatValue(1.51f));
        assertEquals("1.514542", XmlUtils.formatFloatValue(1.514542f));
        assertEquals("1.516542", XmlUtils.formatFloatValue(1.516542f));
        assertEquals("-1.51", XmlUtils.formatFloatValue(-1.51f));
        assertEquals("-1", XmlUtils.formatFloatValue(-1f));
    }

    public void testFormatFloatValueLocale() throws Exception {
        // Ensure that the layout float values aren't affected by
        // locale settings, like using commas instead of of periods
        Locale originalDefaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.FRENCH);

            // Ensure that this is a locale which uses a comma instead of a period:
            assertEquals("1,50", String.format("%.2f", 1.5f));

            // Ensure that the formatFloatAttribute is immune
            assertEquals("1.5", XmlUtils.formatFloatValue(1.5f));
        } finally {
            Locale.setDefault(originalDefaultLocale);
        }
    }

    public void testGetUtfReader() throws IOException {
        File file = File.createTempFile(getName(), SdkConstants.DOT_XML);

        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        try (OutputStreamWriter writer = new OutputStreamWriter(stream, Charsets.UTF_8)) {
            stream.write(0xef);
            stream.write(0xbb);
            stream.write(0xbf);
            writer.write("OK");
        }

        Reader reader = XmlUtils.getUtfReader(file);
        assertEquals('O', reader.read());
        assertEquals('K', reader.read());
        assertEquals(-1, reader.read());

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public void testStripBom() {
        assertEquals("", XmlUtils.stripBom(""));
        assertEquals("Hello", XmlUtils.stripBom("Hello"));
        assertEquals("Hello", XmlUtils.stripBom("\uFEFFHello"));
    }

    public void testParseDocument() throws Exception {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "          some text\n" +
                "\n" +
                "</LinearLayout>\n";

        Document document = XmlUtils.parseDocument(xml, true);
        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
        assertEquals("LinearLayout", document.getDocumentElement().getTagName());

        // Add BOM
        xml = '\uFEFF' + xml;
        document = XmlUtils.parseDocument(xml, true);
        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
        assertEquals("LinearLayout", document.getDocumentElement().getTagName());
    }

    public void testDisallowDocType() throws Exception {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!DOCTYPE module PUBLIC\n" +
                "    \"-//TEST//DTD Check Configuration 1.3//EN\"\n" +
                "    \"http://schemas.android.com/apk/res/android\">\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "          some text\n" +
                "\n" +
                "</LinearLayout>\n";

        Document document = XmlUtils.parseDocument(xml, true);
        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
        assertEquals("LinearLayout", document.getDocumentElement().getTagName());
    }

    public void testParseUtfXmlFile() throws Exception {
        File file = File.createTempFile(getName(), SdkConstants.DOT_XML);
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"wrap_content\"\n" +
                "    android:orientation=\"vertical\" >\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/button1\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Button\" />\n" +
                "          some text\n" +
                "\n" +
                "</LinearLayout>\n";

        BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
        try (OutputStreamWriter writer = new OutputStreamWriter(stream, Charsets.UTF_8)) {
            stream.write(0xef);
            stream.write(0xbb);
            stream.write(0xbf);
            writer.write(xml);
        }

        Document document = XmlUtils.parseUtfXmlFile(file, true);
        assertNotNull(document);
        assertNotNull(document.getDocumentElement());
        assertEquals("LinearLayout", document.getDocumentElement().getTagName());

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public void testGetSubTags() {
        String xml = "<root><child1/><child2/><child3><grancdhild/></child3></root>";
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        assertThat(document).isNotNull();
        Element root = XmlUtils.getFirstSubTag(document);
        assertThat(root).isNotNull();
        assertThat(XmlUtils.getNextTag(root)).isNull();
        Element child1 = XmlUtils.getFirstSubTag(root);
        assertThat(child1).isNotNull();
        Element child2 = XmlUtils.getNextTag(child1);
        assertThat(child2).isNotNull();
        Element child3 = XmlUtils.getNextTag(child2);
        assertThat(child3).isNotNull();
        assertThat(XmlUtils.getPreviousTag(child3)).isSameAs(child2);
        assertThat(XmlUtils.getPreviousTag(child2)).isSameAs(child1);
        Element grandchild = XmlUtils.getFirstSubTag(child3);
        assertThat(grandchild).isNotNull();
        assertThat(XmlUtils.getNextTag(child3)).isNull();
        assertThat(XmlUtils.getNextTag(grandchild)).isNull();

        assertThat(XmlUtils.getSubTags(grandchild).iterator().hasNext()).isFalse();
        assertThat(XmlUtils.getSubTags(root).iterator().hasNext()).isTrue();
        assertThat(XmlUtils.getSubTags(root).iterator().next()).isSameAs(child1);

        Iterator<Element> iterator = XmlUtils.getSubTags(root).iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isSameAs(child1);
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isSameAs(child2);
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isSameAs(child3);
        assertThat(iterator.hasNext()).isFalse();
    }

    public void testGetSubTagsByName() {
        String xml = "<root><child1/><child2/><child1/><child3><grancdhild/></child3></root>";
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        assertThat(document).isNotNull();
        Element root = XmlUtils.getFirstSubTag(document);
        assertThat(root).isNotNull();
        assertThat(XmlUtils.getNextTag(root)).isNull();
        Element child1 = XmlUtils.getFirstSubTagByName(root, "child1");
        assertThat(child1).isNotNull();
        Element child2 = XmlUtils.getNextTagByName(child1, "child2");
        assertThat(child2).isNotNull();
        assertThat(XmlUtils.getPreviousTagByName(child2, "child1")).isSameAs(child1);
        assertThat(XmlUtils.getFirstSubTagByName(root, "child2")).isSameAs(child2);
        Element child3 = XmlUtils.getNextTagByName(child1, "child3");
        assertThat(child3).isNotNull();
        assertThat(XmlUtils.getFirstSubTagByName(root, "child2")).isSameAs(child2);
        Element grandchild = XmlUtils.getFirstSubTag(child3);
        assertThat(grandchild).isNotNull();
        assertThat(XmlUtils.getNextTag(child3)).isNull();

        Iterator<Element> iterator = XmlUtils.getSubTagsByName(root, "child1").iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isSameAs(child1);
        assertThat(iterator.hasNext()).isTrue();
        Element next = iterator.next();
        assertThat(next).isNotSameAs(child1);
        assertThat(next.getTagName()).isEqualTo(child1.getTagName());
    }

    public void testIsProtoXml() {
        byte[] proto = new byte[] {0x0A, (byte) 0x96, 0x04, 0x0A};
        byte[] text = new byte[] {'\n', '\n', '\n', '\t', '<'};
        byte[] textWithBom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '\n'};
        assertThat(XmlUtils.isProtoXml(proto)).isTrue();
        assertThat(XmlUtils.isProtoXml(text)).isFalse();
        assertThat(XmlUtils.isProtoXml(textWithBom)).isFalse();
        assertThat(XmlUtils.isProtoXml(new ByteArrayInputStream(proto))).isTrue();
        assertThat(XmlUtils.isProtoXml(new ByteArrayInputStream(text))).isFalse();
        assertThat(XmlUtils.isProtoXml(new ByteArrayInputStream(textWithBom))).isFalse();
    }
}
