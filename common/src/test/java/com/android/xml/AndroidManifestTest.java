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

package com.android.xml;

import com.android.annotations.NonNull;
import com.android.utils.XmlUtils;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Tests for {@link AndroidManifest}.
 */
public class AndroidManifestTest {
    /**
     * Tests that the split value is correct when the split is set.
     *
     * @throws Exception
     */
    @Test
    public void testGetSplit() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.google.test\"\n"
                + "    split=\"split_name\">\n"
                + "</manifest>";
        Document doc = XmlUtils.parseDocumentSilently(xml, true);
        Assert.assertNotNull(doc);

        Node splitNode = getNode(doc, AndroidManifest.getSplitXPath());
        Assert.assertNotNull(splitNode);
        Assert.assertEquals("split_name", splitNode.getNodeValue());
    }

    /**
     * Tests that the split value is null when the split is not set.
     *
     * @throws Exception
     */
    @Test
    public void testGetNullSplit() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.google.test\">\n"
                + "</manifest>";
        Document doc = XmlUtils.parseDocumentSilently(xml, true);
        Assert.assertNotNull(doc);

        Node splitNode = getNode(doc, AndroidManifest.getSplitXPath());
        Assert.assertNull(splitNode);
    }

    /**
     * Returns the node for <code>nodeName</code> in <code>document</code>.
     *
     * @param document the document to parse.
     * @param nodeName the name of the node to get.
     * @return the requested node.
     * @throws Exception
     */
    private static Node getNode(
            @NonNull Document document,
            @NonNull String nodeName) throws Exception {
        XPath xpath = AndroidXPathFactory.newXPath();

        return (Node) xpath.evaluate(
                nodeName, document, XPathConstants.NODE);
    }
}
