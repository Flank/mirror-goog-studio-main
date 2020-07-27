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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.google.common.annotations.Beta;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Specialized visitor for running detectors on resources: typically XML documents, but also binary
 * resources.
 *
 * <p>It operates in two phases:
 *
 * <ol>
 *   <li>First, it computes a set of maps where it generates a map from each significant element
 *       name, and each significant attribute name, to a list of detectors to consult for that
 *       element or attribute name. The set of element names or attribute names (or both) that a
 *       detector is interested in is provided by the detectors themselves.
 *   <li>Second, it iterates over the document a single time. For each element and attribute it
 *       looks up the list of interested detectors, and runs them.
 * </ol>
 *
 * It also notifies all the detectors before and after the document is processed such that they can
 * do pre- and post-processing.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
class ResourceVisitor {
    private final Map<String, List<XmlScanner>> elementToCheck = new HashMap<>();
    private final Map<String, List<XmlScanner>> attributeToCheck = new HashMap<>();
    private final List<XmlScanner> allElementDetectors = new ArrayList<>();
    private final List<XmlScanner> allAttributeDetectors = new ArrayList<>();
    private final List<XmlScanner> allDetectors;
    private final List<? extends Detector> binaryDetectors;
    private final XmlParser parser;

    // Really want this:
    // <T extends List<Detector> & XmlScanner> XmlVisitor(IDomParser parser,
    //    T xmlDetectors) {
    // but it makes client code tricky and ugly.
    ResourceVisitor(
            @NonNull XmlParser parser,
            @NonNull List<XmlScanner> allDetectors,
            @Nullable List<Detector> binaryDetectors) {
        this.parser = parser;
        this.binaryDetectors = binaryDetectors;
        this.allDetectors = allDetectors;

        // TODO: Check appliesTo() for files, and find a quick way to enable/disable
        // rules when running through a full project!
        for (XmlScanner detector : allDetectors) {
            XmlScanner xmlDetector = (XmlScanner) detector;
            Collection<String> attributes = xmlDetector.getApplicableAttributes();
            if (attributes == XmlScanner.ALL) {
                allAttributeDetectors.add(xmlDetector);
            } else if (attributes != null) {
                for (String attribute : attributes) {
                    List<XmlScanner> list = attributeToCheck.get(attribute);
                    if (list == null) {
                        list = new ArrayList<>();
                        attributeToCheck.put(attribute, list);
                    }
                    list.add(xmlDetector);
                }
            }
            Collection<String> elements = xmlDetector.getApplicableElements();
            if (elements == XmlScanner.ALL) {
                allElementDetectors.add(xmlDetector);
            } else if (elements != null) {
                for (String element : elements) {
                    List<XmlScanner> list = elementToCheck.get(element);
                    if (list == null) {
                        list = new ArrayList<>();
                        elementToCheck.put(element, list);
                    }
                    list.add(xmlDetector);
                }
            }
        }
    }

    void visitFile(@NonNull XmlContext context) {
        try {
            for (XmlScanner check : allDetectors) {
                check.beforeCheckFile(context);
                check.visitDocument(context, context.document);
            }

            if (!elementToCheck.isEmpty()
                    || !attributeToCheck.isEmpty()
                    || !allAttributeDetectors.isEmpty()
                    || !allElementDetectors.isEmpty()) {
                visitElement(context, context.document.getDocumentElement());
            }

            for (XmlScanner check : allDetectors) {
                check.afterCheckFile(context);
            }
        } catch (RuntimeException e) {
            LintDriver.handleDetectorError(context, context.getDriver(), e);
        }
    }

    private void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        List<XmlScanner> elementChecks = elementToCheck.get(element.getLocalName());
        if (elementChecks != null) {
            assert elementChecks instanceof RandomAccess;
            for (XmlScanner check : elementChecks) {
                check.visitElement(context, element);
            }
        }
        if (!allElementDetectors.isEmpty()) {
            for (XmlScanner check : allElementDetectors) {
                check.visitElement(context, element);
            }
        }

        if (!attributeToCheck.isEmpty() || !allAttributeDetectors.isEmpty()) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attribute = (Attr) attributes.item(i);
                String name = attribute.getLocalName();
                if (name == null) {
                    name = attribute.getName();
                }
                List<XmlScanner> list = attributeToCheck.get(name);
                if (list != null) {
                    for (XmlScanner check : list) {
                        check.visitAttribute(context, attribute);
                    }
                }
                if (!allAttributeDetectors.isEmpty()) {
                    for (XmlScanner check : allAttributeDetectors) {
                        check.visitAttribute(context, attribute);
                    }
                }
            }
        }

        // Visit children
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }

        // Post hooks
        if (elementChecks != null) {
            for (XmlScanner check : elementChecks) {
                check.visitElementAfter(context, element);
            }
        }
        if (!allElementDetectors.isEmpty()) {
            for (XmlScanner check : allElementDetectors) {
                check.visitElementAfter(context, element);
            }
        }
    }

    @NonNull
    public XmlParser getParser() {
        return parser;
    }

    public void visitBinaryResource(@NonNull ResourceContext context) {
        if (binaryDetectors == null) {
            return;
        }
        for (Detector check : binaryDetectors) {
            check.beforeCheckFile(context);
            check.checkBinaryResource(context);
            check.afterCheckFile(context);
        }
    }
}
