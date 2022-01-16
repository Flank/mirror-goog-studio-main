/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint;

import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.tools.lint.client.api.LintClient.CLIENT_UNIT_TESTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.DomExtensions;
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import kotlin.io.FilesKt;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class LintCliXmlParserTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testBasic() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    android:layout_width=\"match_parent\"\n"
                        + "    android:layout_height=\"wrap_content\"\n"
                        + "    android:orientation=\"vertical\" >\n"
                        + "\n"
                        + "    <Button\n"
                        + "        android:id=\"@+id/button1\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Button\" />\n"
                        + "\n"
                        + "    <Button\n"
                        + "        android:id=\"@+id/button2\"\n"
                        + "        android:layout_width=\"wrap_content\"\n"
                        + "        android:layout_height=\"wrap_content\"\n"
                        + "        android:text=\"Button\" />\n"
                        + "\n"
                        + "</LinearLayout>\n";
        LintCliXmlParser parser = new LintCliXmlParser(new LintCliClient(CLIENT_UNIT_TESTS));
        File file = temporaryFolder.newFile("parsertest.xml");
        FilesKt.writeText(file, xml, Charsets.UTF_8);
        LintClient client = new TestClient();
        LintRequest request = new LintRequest(client, Collections.emptyList());
        LintDriver driver = new LintDriver(new TestIssueRegistry(), client, request);
        Project project = Project.create(client, file.getParentFile(), file.getParentFile());
        XmlContext context =
                new XmlContext(driver, project, null, file, null, xml, parser.parseXml(xml, file));
        Document document = parser.parseXml(context);
        assertNotNull(document);

        // Basic parsing heart beat tests
        Element linearLayout = (Element) document.getElementsByTagName("LinearLayout").item(0);
        assertNotNull(linearLayout);
        NodeList buttons = document.getElementsByTagName("Button");
        assertEquals(2, buttons.getLength());
        final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
        assertEquals("wrap_content", linearLayout.getAttributeNS(ANDROID_URI, "layout_height"));

        // Check attribute positions
        Attr attr = linearLayout.getAttributeNodeNS(ANDROID_URI, "layout_width");
        assertNotNull(attr);
        Location location = parser.getLocation(context, attr);
        Position start = location.getStart();
        Position end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        assertEquals(2, start.getLine());
        assertEquals(xml.indexOf("android:layout_width"), start.getOffset());
        assertEquals(2, end.getLine());
        String target = "android:layout_width=\"match_parent\"";
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check attribute name positions
        location = parser.getNameLocation(context, attr);
        start = location.getStart();
        end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        target = "android:layout_width";
        assertEquals(target, xml.substring(start.getOffset(), end.getOffset()));
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check attribute value positions
        location = parser.getValueLocation(context, attr);
        start = location.getStart();
        end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        target = "match_parent";
        assertEquals(target, xml.substring(start.getOffset(), end.getOffset()));
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        // Check element positions
        Element button = (Element) buttons.item(0);
        location = parser.getLocation(context, button);
        start = location.getStart();
        end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        assertEquals(6, start.getLine());
        assertEquals(xml.indexOf("<Button"), start.getOffset());
        assertEquals(xml.indexOf("/>") + 2, end.getOffset());
        assertEquals(10, end.getLine());
        int button1End = end.getOffset();

        // Check element name positions
        location = parser.getNameLocation(context, button);
        start = location.getStart();
        end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        target = "Button";
        assertEquals(target, xml.substring(start.getOffset(), end.getOffset()));
        assertEquals(xml.indexOf(target) + target.length(), end.getOffset());

        Handle handle = parser.createLocationHandle(context, button);
        Location location2 = handle.resolve();
        assertSame(location.getFile(), location.getFile());
        assertNotNull(location2.getStart());
        assertNotNull(location2.getEnd());
        assertEquals(6, location2.getStart().getLine());
        assertEquals(10, location2.getEnd().getLine());

        Element button2 = (Element) buttons.item(1);
        location = parser.getLocation(context, button2);
        start = location.getStart();
        end = location.getEnd();
        assertNotNull(start);
        assertNotNull(end);
        assertEquals(12, start.getLine());
        assertEquals(xml.indexOf("<Button", button1End), start.getOffset());
        assertEquals(xml.indexOf("/>", start.getOffset()) + 2, end.getOffset());
        assertEquals(16, end.getLine());
    }

    @Test
    public void testLineEndings() throws Exception {
        // Test for http://code.google.com/p/android/issues/detail?id=22925
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                        + "<LinearLayout>\r\n"
                        + "\r"
                        + "<LinearLayout></LinearLayout>\r\n"
                        + "</LinearLayout>\r\n";
        LintCliXmlParser parser = new LintCliXmlParser(new LintCliClient(CLIENT_UNIT_TESTS));
        File file = temporaryFolder.newFile("parsertest.xml");
        FilesKt.writeText(file, xml, Charsets.UTF_8);
        LintClient client = new TestClient();
        LintRequest request = new LintRequest(client, Collections.emptyList());
        LintDriver driver = new LintDriver(new TestIssueRegistry(), client, request);
        Project project = Project.create(client, file.getParentFile(), file.getParentFile());
        XmlContext context =
                new XmlContext(driver, project, null, file, null, xml, parser.parseXml(xml, file));
        Document document = parser.parseXml(context);
        assertNotNull(document);
    }

    @Test
    public void testValueLocations() throws IOException {
        // Regression test for https://issuetracker.google.com/211693606
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n"
                        + "    <Button\n"
                        + "        android:hint='my \"hint&quot;'\n"
                        + "        app:visible=\"@{hasValue &amp;&amp; isFeatureOn}\"\n"
                        + "        android:text=\"> &lt; &gt; ' &apos;'\" />\n"
                        + "</LinearLayout>\n";
        XmlContext context = getContext(xml);
        String printed = printLocations(context);

        assertEquals(
                ""
                        + ""
                        + "android:hint range:\n"
                        + "        android:hint='my \"hint&quot;'\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + ":android:hint name range:\n"
                        + "        android:hint='my \"hint&quot;'\n"
                        + "        ~~~~~~~~~~~~\n"
                        + ":android:hint value range for \"my \"hint\"\":\n"
                        + "        android:hint='my \"hint&quot;'\n"
                        + "                      ~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "android:text range:\n"
                        + "        android:text=\"> &lt; &gt; ' &apos;'\" />\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + ":android:text name range:\n"
                        + "        android:text=\"> &lt; &gt; ' &apos;'\" />\n"
                        + "        ~~~~~~~~~~~~\n"
                        + ":android:text value range for \"> < > ' ''\":\n"
                        + "        android:text=\"> &lt; &gt; ' &apos;'\" />\n"
                        + "                      ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "\n"
                        + "app:visible range:\n"
                        + "        app:visible=\"@{hasValue &amp;&amp; isFeatureOn}\"\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + ":app:visible name range:\n"
                        + "        app:visible=\"@{hasValue &amp;&amp; isFeatureOn}\"\n"
                        + "        ~~~~~~~~~~~\n"
                        + ":app:visible value range for \"@{hasValue && isFeatureOn}\":\n"
                        + "        app:visible=\"@{hasValue &amp;&amp; isFeatureOn}\"\n"
                        + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "\n",
                printed);
    }

    @NotNull
    private XmlContext getContext(String xml) throws IOException {
        File file = temporaryFolder.newFile("parsertest.xml");
        FilesKt.writeText(file, xml, Charsets.UTF_8);

        LintCliXmlParser parser = new LintCliXmlParser(new LintCliClient(CLIENT_UNIT_TESTS));
        LintClient client = new TestClient();
        LintRequest request = new LintRequest(client, Collections.emptyList());
        LintDriver driver = new LintDriver(new TestIssueRegistry(), client, request);
        Project project = Project.create(client, file.getParentFile(), file.getParentFile());
        Document document = parser.parseXml(xml, file);
        assertNotNull(document);
        return new XmlContext(driver, project, null, file, null, xml, document);
    }

    @NotNull
    private String printLocations(XmlContext context) {
        CharSequence contents = context.getContents();
        assertNotNull(contents);
        String xml = contents.toString();
        Document document = context.document;
        XmlParser parser = context.getParser();
        StringBuilder sb = new StringBuilder();
        Function1<File, CharSequence> textProvider = file1 -> xml;
        DomExtensions.visitElements(
                document.getDocumentElement(),
                element -> {
                    NamedNodeMap attributes = element.getAttributes();
                    for (int i = 0; i < attributes.getLength(); i++) {
                        Attr attr = (Attr) attributes.item(i);
                        if (attr.getName().startsWith(XMLNS_PREFIX)) {
                            // just to cut down on the size of the golden string; nothing special
                            // here
                            continue;
                        }
                        Location location = parser.getLocation(context, attr);
                        String lines = ReporterKt.getErrorLines(location, textProvider);
                        sb.append(attr.getName()).append(" range:\n");
                        sb.append(lines);
                        Location nameLocation = parser.getNameLocation(context, attr);
                        String nameLines = ReporterKt.getErrorLines(nameLocation, textProvider);
                        sb.append(":").append(attr.getName()).append(" name range:\n");
                        sb.append(nameLines);
                        sb.append(":")
                                .append(attr.getName())
                                .append(" value range for \"")
                                .append(attr.getValue())
                                .append("\":\n");
                        Location valueLocation = parser.getValueLocation(context, attr);
                        String valueLines = ReporterKt.getErrorLines(valueLocation, textProvider);
                        sb.append(valueLines);
                        sb.append("\n");
                    }
                    return false;
                });

        return sb.toString();
    }

    private static class TestClient extends LintCliClient {
        TestClient() {
            super(CLIENT_UNIT_TESTS);
        }

        @Override
        public void report(
                @NonNull Context context, @NonNull Incident incident, @NonNull TextFormat format) {
            System.out.println(incident.getLocation() + ":" + incident.getMessage());
        }
    }
}
