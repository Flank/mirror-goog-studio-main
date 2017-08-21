/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.vectordrawable;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Converts SVG to VectorDrawable's XML
 *
 * There are 2 major functions:
 * 1. parse(file)
 *   This include parse the .svg file and build an internal tree. The optimize this tree.
 *
 * 2. writeFile()
 *   This is traversing the whole tree, and write the group / path info into the XML.
 */
public class Svg2Vector {
    private static final Logger logger = Logger.getLogger(Svg2Vector.class.getSimpleName());

    public static final String SVG_POLYGON = "polygon";
    public static final String SVG_POLYLINE = "polyline";
    public static final String SVG_RECT = "rect";
    public static final String SVG_CIRCLE = "circle";
    public static final String SVG_LINE = "line";
    public static final String SVG_PATH = "path";
    public static final String SVG_ELLIPSE = "ellipse";
    public static final String SVG_GROUP = "g";
    public static final String SVG_TRANSFORM = "transform";
    public static final String SVG_STYLE = "style";
    public static final String SVG_DISPLAY = "display";

    public static final String SVG_D = "d";
    public static final String SVG_STROKE_COLOR = "stroke";
    public static final String SVG_STROKE_OPACITY = "stroke-opacity";
    public static final String SVG_STROKE_LINEJOINE = "stroke-linejoin";
    public static final String SVG_STROKE_LINECAP = "stroke-linecap";
    public static final String SVG_STROKE_WIDTH = "stroke-width";
    public static final String SVG_FILL_COLOR = "fill";
    public static final String SVG_FILL_OPACITY = "fill-opacity";
    public static final String SVG_FILL_TYPE = "fill-rule";
    public static final String SVG_OPACITY = "opacity";
    public static final String SVG_CLIP = "clip";
    public static final String SVG_POINTS = "points";

    public static final ImmutableMap<String, String> presentationMap =
            ImmutableMap.<String, String>builder()
                    .put(SVG_STROKE_COLOR, "android:strokeColor")
                    .put(SVG_STROKE_OPACITY, "android:strokeAlpha")
                    .put(SVG_STROKE_LINEJOINE, "android:strokeLineJoin")
                    .put(SVG_STROKE_LINECAP, "android:strokeLineCap")
                    .put(SVG_STROKE_WIDTH, "android:strokeWidth")
                    .put(SVG_FILL_COLOR, "android:fillColor")
                    .put(SVG_FILL_OPACITY, "android:fillAlpha")
                    .put(SVG_CLIP, "android:clip")
                    .put(SVG_OPACITY, "android:fillAlpha")
                    .put(SVG_FILL_TYPE, "android:fillType")
                    .build();

    public static final ImmutableMap<String, String> gradientMap =
            ImmutableMap.<String, String>builder()
                    .put("x1", "android:startX")
                    .put("y1", "android:startY")
                    .put("x2", "android:endX")
                    .put("y2", "android:endY")
                    .put("cx", "android:centerX")
                    .put("cy", "android:centerY")
                    .put("r", "android:gradientRadius")
                    .put("spreadMethod", "android:tileMode")
                    .put("gradientUnits", "")
                    .put("gradientTransform", "")
                    .put("gradientType", "android:type")
                    .build();

    // List all the Svg nodes that we don't support. Categorized by the types.
    private static final HashSet<String> unsupportedSvgNodes =
            Sets.newHashSet(
                    // Animation elements
                    "animate",
                    "animateColor",
                    "animateMotion",
                    "animateTransform",
                    "mpath",
                    "set",
                    // Container elements
                    "a",
                    "glyph",
                    "marker",
                    "mask",
                    "missing-glyph",
                    "pattern",
                    "switch",
                    "symbol",
                    // Filter primitive elements
                    "feBlend",
                    "feColorMatrix",
                    "feComponentTransfer",
                    "feComposite",
                    "feConvolveMatrix",
                    "feDiffuseLighting",
                    "feDisplacementMap",
                    "feFlood",
                    "feFuncA",
                    "feFuncB",
                    "feFuncG",
                    "feFuncR",
                    "feGaussianBlur",
                    "feImage",
                    "feMerge",
                    "feMergeNode",
                    "feMorphology",
                    "feOffset",
                    "feSpecularLighting",
                    "feTile",
                    "feTurbulence",
                    // Font elements
                    "font",
                    "font-face",
                    "font-face-format",
                    "font-face-name",
                    "font-face-src",
                    "font-face-uri",
                    "hkern",
                    "vkern",
                    // Gradient elements
                    "radialGradient",
                    "stop",
                    // Graphics elements
                    "ellipse",
                    "text",
                    // Light source elements
                    "feDistantLight",
                    "fePointLight",
                    "feSpotLight",
                    // Structural elements
                    "symbol",
                    // Text content elements
                    "altGlyph",
                    "altGlyphDef",
                    "altGlyphItem",
                    "glyph",
                    "glyphRef",
                    "textPath",
                    "text",
                    "tref",
                    "tspan",
                    // Text content child elements
                    "altGlyph",
                    "textPath",
                    "tref",
                    "tspan",
                    // Uncategorized elements
                    "color-profile",
                    "cursor",
                    "filter",
                    "foreignObject",
                    "script",
                    "view");

    @NonNull
    private static SvgTree parse(File f) throws Exception {
        SvgTree svgTree = new SvgTree();
        Document doc = svgTree.parse(f);
        NodeList nSvgNode;

        // Parse svg elements
        nSvgNode = doc.getElementsByTagName("svg");
        if (nSvgNode.getLength() != 1) {
            throw new IllegalStateException("Not a proper SVG file");
        }
        Node rootNode = nSvgNode.item(0);
        for (int i = 0; i < nSvgNode.getLength(); i++) {
            Node nNode = nSvgNode.item(i);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                svgTree.parseDimension(nNode);
            }
        }

        if (svgTree.getViewBox() == null) {
            svgTree.logErrorLine(
                    "Missing \"viewBox\" in <svg> element", rootNode, SvgTree.SvgLogLevel.ERROR);
            return svgTree;
        }

        SvgGroupNode root = new SvgGroupNode(svgTree, rootNode, "root");
        svgTree.setRoot(root);

        // Parse all the group and path nodes recursively.
        traverseSVGAndExtract(svgTree, root, rootNode);

        // TODO: Handle "use" elements defined inside "defs"

        // Fill in all the use nodes in the svgTree.
        for (SvgGroupNode n : svgTree.getUseSet()) {
            extractUseNode(svgTree, n, n.getDocumentNode());
        }

        // Replaces elements that reference clipPaths and replaces them with clipPathNodes
        for (Map.Entry<SvgNode, SvgGroupNode> entry : svgTree.getClipPathAffectedNodesSet()) {
            handleClipPath(svgTree, entry.getKey(), entry.getValue());
        }

        // TODO: Handle clipPath elements that reference another clipPath

        // Add attributes for all the style elements.
        for (Map.Entry<String, HashSet<SvgNode>> entry : svgTree.getStyleAffectedNodes()) {
            for (SvgNode n : entry.getValue()) {
                addStyleToPath(n, svgTree.getStyleClassAttr(entry.getKey()));
            }
        }

        svgTree.flatten();
        svgTree.dump(root);

        return svgTree;
    }

    /** Traverse the tree in pre-order. */
    private static void traverseSVGAndExtract(
            SvgTree svgTree, SvgGroupNode currentGroup, Node item) {
        // Recursively traverse all the group and path nodes
        NodeList allChildren = item.getChildNodes();

        for (int i = 0; i < allChildren.getLength(); i++) {
            Node currentNode = allChildren.item(i);
            String nodeName = currentNode.getNodeName();

            if (!currentNode.hasChildNodes() && !currentNode.hasAttributes()) {
                // If there is nothing in this node, just ignore it.
                continue;
            }

            if (SVG_PATH.equals(nodeName) ||
                SVG_RECT.equals(nodeName) ||
                SVG_CIRCLE.equals(nodeName) ||
                SVG_ELLIPSE.equals(nodeName) ||
                SVG_POLYGON.equals(nodeName) ||
                SVG_POLYLINE.equals(nodeName) ||
                SVG_LINE.equals(nodeName)) {
                SvgLeafNode child = new SvgLeafNode(svgTree, currentNode, nodeName + i);
                processIdName(svgTree, child);
                currentGroup.addChild(child);
                extractAllItemsAs(svgTree, child, currentNode, currentGroup);
                svgTree.setHasLeafNode(true);
            } else if (SVG_GROUP.equals(nodeName)) {
                SvgGroupNode childGroup = new SvgGroupNode(svgTree, currentNode, "child" + i);
                currentGroup.addChild(childGroup);
                processIdName(svgTree, childGroup);
                extractGroupNode(svgTree, childGroup, currentGroup);
                traverseSVGAndExtract(svgTree, childGroup, currentNode);
            } else if ("use".equals(nodeName)) {
                SvgGroupNode childGroup = new SvgGroupNode(svgTree, currentNode, "child" + i);
                currentGroup.addChild(childGroup);
                svgTree.addToUseSet(childGroup);
            } else if ("defs".equals(nodeName)) {
                SvgGroupNode childGroup = new SvgGroupNode(svgTree, currentNode, "child" + i);
                traverseSVGAndExtract(svgTree, childGroup, currentNode);
            } else if ("clipPath".equals(nodeName)) {
                SvgClipPathNode clipPath = new SvgClipPathNode(svgTree, currentNode, nodeName + i);
                processIdName(svgTree, clipPath);
                traverseSVGAndExtract(svgTree, clipPath, currentNode);
            } else if (SVG_STYLE.equals(nodeName)) {
                extractStyleNode(svgTree, currentNode);
            } else if ("linearGradient".equals(nodeName)) {
                SvgGradientNode gradientNode =
                        new SvgGradientNode(svgTree, currentNode, nodeName + i);
                processIdName(svgTree, gradientNode);
                extractGradientNode(svgTree, gradientNode);
                gradientNode.fillPresentationAttributes("gradientType", "linear");
                svgTree.setHasGradient(true);
            } else {
                // For other fancy tags, like <switch>, they can contain children too.
                // Report the unsupported nodes.
                if (unsupportedSvgNodes.contains(nodeName)) {
                    svgTree.logErrorLine("<" + nodeName + "> is not supported", currentNode,
                                         SvgTree.SvgLogLevel.ERROR);
                }
                // This is a workaround for the cases using defs to define a full icon size clip
                // path, which is redundant information anyway.
                if (!"defs".equals(nodeName)) {
                    traverseSVGAndExtract(svgTree, currentGroup, currentNode);
                }
            }
        }

    }

    /**
     * Reads content from a gradient element's documentNode and fills in attributes for the
     * SvgGradientNode.
     */
    private static void extractGradientNode(SvgTree svgTree, SvgGradientNode gradientNode) {
        Node currentNode = gradientNode.getDocumentNode();
        NamedNodeMap a = currentNode.getAttributes();
        int len = a.getLength();
        for (int j = 0; j < len; j++) {
            Node n = a.item(j);
            String name = n.getNodeName();
            String value = n.getNodeValue();
            if (gradientMap.containsKey(name)) {
                gradientNode.fillPresentationAttributes(name, value);
            }
        }
        NodeList gradientChildren = currentNode.getChildNodes();

        // Default SVG gradient offset is the previous largest offset.
        float greatestOffset = 0;
        for (int i = 0; i < gradientChildren.getLength(); i++) {
            Node node = gradientChildren.item(i);
            String nodeName = node.getNodeName();
            if (nodeName.equals("stop")) {
                NamedNodeMap stopAttr = node.getAttributes();
                // Default SVG gradient stop color is black.
                String color = "rgb(0,0,0)";
                // Default SVG gradient stop opacity is 1.
                String opacity = "1";
                for (int k = 0; k < stopAttr.getLength(); k++) {
                    Node stopItem = stopAttr.item(k);
                    String attrName = stopItem.getNodeName();
                    String attrValue = stopItem.getNodeValue();
                    switch (attrName) {
                        case "offset":
                            // If a gradient's value is not greater than all pervious offset values,
                            // then the offset value is adjusted to be equal to the largest of all
                            // previous offset values.
                            greatestOffset = extractOffset(attrValue, greatestOffset);
                            break;
                        case "stop-color":
                            color = attrValue;
                            break;
                        case "stop-opacity":
                            opacity = attrValue;
                            break;
                        case "style":
                            String[] parts = attrValue.split(";");
                            for (String attr : parts) {
                                String[] splitAttribute = attr.split(":");
                                if (splitAttribute.length == 2) {
                                    if (attr.startsWith("stop-color")) {
                                        color = splitAttribute[1];
                                    } else if (attr.startsWith("stop-opacity")) {
                                        opacity = splitAttribute[1];
                                    }
                                }
                            }
                            break;
                    }
                }
                String offset = String.valueOf(greatestOffset);
                gradientNode.addGradientStop(color, offset, opacity);
            }
        }
    }

    /**
     * Finds the gradient offset value given a String containing the value and greatest previous
     * offset value.
     *
     * @param offset is a String that can be a value or a percentage.
     * @param greatestOffset is the greatest offset value seen in the gradient so far.
     * @return float that is final value of the offset between 0 and 1.
     */
    private static float extractOffset(String offset, float greatestOffset) {
        float x = greatestOffset;
        if (offset.endsWith("%")) {
            try {
                x = Float.parseFloat(offset.substring(0, offset.length() - 1));
                x /= 100;
            } catch (NumberFormatException e) {
                logger.log(Level.FINE, "Unsupported gradient offset percentage");
            }
        } else {
            try {
                x = Float.parseFloat(offset);
            } catch (NumberFormatException e) {
                logger.log(Level.FINE, "Unsupported gradient offset value");
            }
        }
        // Gradient offset values must be between 0 and 1 or 0% and 100%.
        x = Math.min(1, Math.max(x, 0));
        if (x >= greatestOffset) {
            return x;
        }
        return greatestOffset;
    }

    /**
     * Checks to see if the childGroup references any clipPath or style elements. Saves the
     * reference in the svgTree to add the information to an SvgNode later.
     */
    private static void extractGroupNode(
            SvgTree svgTree, SvgGroupNode childGroup, SvgGroupNode currentGroup) {
        NamedNodeMap a = childGroup.getDocumentNode().getAttributes();
        int len = a.getLength();
        for (int j = 0; j < len; j++) {
            Node n = a.item(j);
            String name = n.getNodeName();
            String value = n.getNodeValue();
            if (name.equals("clip-path")) {
                if (!value.isEmpty()) {
                    svgTree.addClipPathAffectedNode(childGroup, currentGroup);
                }
            } else if (name.equals("class")) {
                if (!value.isEmpty()) {
                    svgTree.addAffectedNodeToStyleClass("." + value, childGroup);
                }
            }
        }
    }

    /**
     * Extracts the attribute information from a style element and adds to the
     * styleClassAttributeMap of the SvgTree. SvgNodes reference style elements using a 'class'
     * attribute. The style attribute will be filled into the tree after the svgTree calls
     * traverseSVGAndExtract().
     *
     * @param svgTree
     * @param currentNode
     */
    private static void extractStyleNode(SvgTree svgTree, Node currentNode) {
        NodeList a = currentNode.getChildNodes();
        int len = a.getLength();
        String styleData = "";
        for (int j = 0; j < len; j++) {
            Node n = a.item(j);
            if (n.getNodeType() == Node.CDATA_SECTION_NODE || len == 1) {
                styleData = n.getNodeValue();
            }
        }
        if (!styleData.isEmpty()) {
            // Separate each of the classes.
            String[] classData = styleData.split("}");
            for (int i = 0; i < classData.length - 1; i++) {
                // Separate the class name from the attribute values.
                String[] splitClassData = classData[i].split("\\{");
                String className = splitClassData[0].trim();
                String styleAttr = splitClassData[1].trim();
                // Separate multiple classes if necessary.
                String[] splitClassNames = className.split(",");
                for (String splitClassName : splitClassNames) {
                    String styleAttrTemp = styleAttr;
                    className = splitClassName.trim();
                    // Concatenate the attributes to existing attributes.
                    if (svgTree.containsStyleClass(className)) {
                        styleAttrTemp += svgTree.getStyleClassAttr(className);
                    }
                    svgTree.addStyleClassToTree(className, styleAttrTemp);
                }
            }
        }
    }


    /**
     * Checks if the id of a node exists and adds the id and SvgNode to the svgTree's idMap if it
     * exists.
     */
    private static void processIdName(SvgTree svgTree, SvgNode child) {
        String idName = child.getAttributeValue("id");
        if (!idName.isEmpty()) {
            svgTree.addIdToMap(idName, child);
        }
    }

    /**
     * Reads the contents of the currentNode and fills them into useGroupNode. Propagates any
     * attributes of the useGroupNode to its children.
     */
    private static void extractUseNode(
            SvgTree svgTree, SvgGroupNode useGroupNode, Node currentNode) {
        NamedNodeMap a = currentNode.getAttributes();
        int len = a.getLength();
        float x = 0;
        float y = 0;
        String id = "";
        for (int j = 0; j < len; j++) {
            Node n = a.item(j);
            String name = n.getNodeName();
            String value = n.getNodeValue();
            if (name.equals("xlink:href")) {
                id = value.substring(1);
            } else if (name.equals("x")) {
                x = Float.parseFloat(value);
            } else if (name.equals("y")) {
                y = Float.parseFloat(value);
            } else if (presentationMap.containsKey(name)) {
                useGroupNode.fillPresentationAttributes(name, value);
            }
        }
        AffineTransform useTransform = new AffineTransform(1, 0, 0, 1, x, y);
        SvgNode definedNode = svgTree.getSvgNodeFromId(id);
        if (definedNode == null) {
            svgTree.logErrorLine(
                    "Referenced id is missing", currentNode, SvgTree.SvgLogLevel.ERROR);
        } else {
            SvgNode copiedNode = definedNode.deepCopy();
            useGroupNode.addChild(copiedNode);
            for (Map.Entry<String, String> entry : useGroupNode.mVdAttributesMap.entrySet()) {
                String key = entry.getKey();
                copiedNode.fillPresentationAttributes(key, entry.getValue());
            }
            useGroupNode.fillEmptyAttributes(useGroupNode.mVdAttributesMap);
            useGroupNode.transformIfNeeded(useTransform);
        }
    }

    /**
     * Replaces an SvgNode in the SvgTree that references a clipPath element with the
     * SvgClipPathNode that corresponds to the referenced clip-path id. Adds the SvgNode as an
     * affected node of the SvgClipPathNode.
     */
    private static void handleClipPath(SvgTree svg, SvgNode child, SvgGroupNode currentGroup) {
        String value = child.getAttributeValue("clip-path");
        String clipName = value.split("#")[1].split("\\)")[0];
        currentGroup.removeChild(child);
        SvgClipPathNode clip = ((SvgClipPathNode) svg.getSvgNodeFromId(clipName)).deepCopy();
        currentGroup.addChild(clip);
        clip.addAffectedNode(child);
        clip.setClipPathNodeAttributes();
    }

    /** Reads the content from currentItem and fills into the SvgLeafNode "child". */
    private static void extractAllItemsAs(
            SvgTree avg, SvgLeafNode child, Node currentItem, SvgGroupNode currentG) {
        Node currentGroup = currentItem.getParentNode();

        boolean hasNodeAttr = false;
        String styleContent = "";
        StringBuilder styleContentBuilder = new StringBuilder();
        boolean nothingToDisplay = false;

        while (currentGroup != null && currentGroup.getNodeName().equals("g")) {
            // Parse the group's attributes.
            logger.log(Level.FINE, "Printing current parent");
            printlnCommon(currentGroup);

            NamedNodeMap attr = currentGroup.getAttributes();
            Node nodeAttr = attr.getNamedItem(SVG_STYLE);
            // Search for the "display:none", if existed, then skip this item.
            if (nodeAttr != null) {
                styleContentBuilder.append(nodeAttr.getTextContent());
                styleContentBuilder.append(';');
                styleContent = styleContentBuilder.toString();
                logger.log(Level.FINE, "styleContent is :" + styleContent + "at number group ");
                if (styleContent.contains("display:none")) {
                    logger.log(Level.FINE, "Found none style, skip the whole group");
                    nothingToDisplay = true;
                    break;
                } else {
                    hasNodeAttr = true;
                }
            }

            Node displayAttr = attr.getNamedItem(SVG_DISPLAY);
            if (displayAttr != null && "none".equals(displayAttr.getNodeValue())) {
                logger.log(Level.FINE, "Found display:none style, skip the whole group");
                nothingToDisplay = true;
                break;
            }
            currentGroup = currentGroup.getParentNode();
        }

        if (nothingToDisplay) {
            // Skip this current whole item.
            return;
        }

        logger.log(Level.FINE, "Print current item");
        printlnCommon(currentItem);

        if (hasNodeAttr && !styleContent.isEmpty()) {
            addStyleToPath(child, styleContent);
        }

        if (SVG_PATH.equals(currentItem.getNodeName())) {
            extractPathItem(avg, child, currentItem, currentG);
        }

        if (SVG_RECT.equals(currentItem.getNodeName())) {
            extractRectItem(avg, child, currentItem, currentG);
        }

        if (SVG_CIRCLE.equals(currentItem.getNodeName())) {
            extractCircleItem(avg, child, currentItem, currentG);
        }

        if (SVG_POLYGON.equals(currentItem.getNodeName())
                || SVG_POLYLINE.equals(currentItem.getNodeName())) {
            extractPolyItem(avg, child, currentItem, currentG);
        }

        if (SVG_LINE.equals(currentItem.getNodeName())) {
            extractLineItem(avg, child, currentItem, currentG);
        }

        if (SVG_ELLIPSE.equals(currentItem.getNodeName())) {
            extractEllipseItem(avg, child, currentItem, currentG);
        }

        // Add the type of node as a style class name for child.
        avg.addAffectedNodeToStyleClass(currentItem.getNodeName(), child);
    }

    private static void printlnCommon(Node n) {
        logger.log(Level.FINE, " nodeName=\"" + n.getNodeName() + "\"");

        String val = n.getNamespaceURI();
        if (val != null) {
            logger.log(Level.FINE, " uri=\"" + val + "\"");
        }

        val = n.getPrefix();

        if (val != null) {
            logger.log(Level.FINE, " pre=\"" + val + "\"");
        }

        val = n.getLocalName();
        if (val != null) {
            logger.log(Level.FINE, " local=\"" + val + "\"");
        }

        val = n.getNodeValue();
        if (val != null) {
            logger.log(Level.FINE, " nodeValue=");
            if (val.trim().isEmpty()) {
                // Whitespace
                logger.log(Level.FINE, "[WS]");
            } else {
                logger.log(Level.FINE, "\"" + n.getNodeValue() + "\"");
            }
        }
    }

    /** Convert polygon element into a path. */
    private static void extractPolyItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "Polyline or Polygon found" + currentGroupNode.getTextContent());
        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();

            for (int itemIndex = 0; itemIndex < len; itemIndex++) {
                Node n = a.item(itemIndex);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if (name.equals("clip-path")) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals(SVG_POINTS)) {
                    PathBuilder builder = new PathBuilder();
                    Pattern p = Pattern.compile("[\\s,]+");
                    String[] split = p.split(value);
                    float baseX = Float.parseFloat(split[0]);
                    float baseY = Float.parseFloat(split[1]);
                    builder.absoluteMoveTo(baseX, baseY);
                    for (int j = 2; j < split.length; j += 2) {
                        float x = Float.parseFloat(split[j]);
                        float y = Float.parseFloat(split[j + 1]);
                        builder.relativeLineTo(x - baseX, y - baseY);
                        baseX = x;
                        baseY = y;
                    }
                    if (SVG_POLYGON.equals(currentGroupNode.getNodeName())) {
                        builder.relativeClose();
                    }
                    child.setPathData(builder.toString());
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("." + value, child);
                    avg.addAffectedNodeToStyleClass(
                            currentGroupNode.getNodeName() + "." + value, child);
                }
            }
        }
    }

    /** Convert rectangle element into a path. */
    private static void extractRectItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "Rect found" + currentGroupNode.getTextContent());

        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {
            float x = 0;
            float y = 0;
            float width = Float.NaN;
            float height = Float.NaN;
            float rx = 0;
            float ry = 0;

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();
            boolean pureTransparent = false;
            for (int j = 0; j < len; j++) {
                Node n = a.item(j);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                    if (value.contains("opacity:0;")) {
                        pureTransparent = true;
                    }
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if (name.equals("clip-path")) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals("x")) {
                    x = Float.parseFloat(value);
                } else if (name.equals("y")) {
                    y = Float.parseFloat(value);
                } else if (name.equals("rx")) {
                    rx = Float.parseFloat(value);
                } else if (name.equals("ry")) {
                    ry = Float.parseFloat(value);
                } else if (name.equals("width")) {
                    width = Float.parseFloat(value);
                } else if (name.equals("height")) {
                    height = Float.parseFloat(value);
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("rect." + value, child);
                    avg.addAffectedNodeToStyleClass("." + value, child);
                }
            }

            if (!pureTransparent && avg != null && !Float.isNaN(x) && !Float.isNaN(y)
                    && !Float.isNaN(width)
                    && !Float.isNaN(height)) {
                PathBuilder builder = new PathBuilder();
                if (rx <= 0 && ry <= 0) {
                    // "M x, y h width v height h -width z"
                    builder.absoluteMoveTo(x, y);
                    builder.relativeHorizontalTo(width);
                    builder.relativeVerticalTo(height);
                    builder.relativeHorizontalTo(-width);
                } else {
                    // Refer to http://www.w3.org/TR/SVG/shapes.html#RectElement
                    assert rx > 0 || ry > 0;
                    if (ry == 0) {
                        ry = rx;
                    } else if (rx == 0) {
                        rx = ry;
                    }
                    if (rx > width / 2) rx = width / 2;
                    if (ry > height / 2) ry = height / 2;

                    builder.absoluteMoveTo(x + rx, y);
                    builder.absoluteLineTo(x + width - rx, y);
                    builder.absoluteArcTo(rx, ry, false, false, true, x + width, y + ry);
                    builder.absoluteLineTo(x + width, y + height - ry);

                    builder.absoluteArcTo(rx, ry, false, false, true, x + width - rx, y + height);
                    builder.absoluteLineTo(x + rx,  y + height);

                    builder.absoluteArcTo(rx, ry, false, false, true, x, y + height - ry);
                    builder.absoluteLineTo(x,  y + ry);
                    builder.absoluteArcTo(rx, ry, false, false, true, x + rx, y);
                }
                builder.relativeClose();
                child.setPathData(builder.toString());
            }
        }
    }

    /** Convert circle element into a path. */
    private static void extractCircleItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "circle found" + currentGroupNode.getTextContent());

        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {
            float cx = 0;
            float cy = 0;
            float radius = 0;

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();
            boolean pureTransparent = false;
            for (int j = 0; j < len; j++) {
                Node n = a.item(j);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                    if (value.contains("opacity:0;")) {
                        pureTransparent = true;
                    }
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if (name.equals("clip-path")) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals("cx")) {
                    cx = Float.parseFloat(value);
                } else if (name.equals("cy")) {
                    cy = Float.parseFloat(value);
                } else if (name.equals("r")) {
                    radius = Float.parseFloat(value);
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("circle." + value, child);
                    avg.addAffectedNodeToStyleClass("." + value, child);
                }

            }

            if (!pureTransparent && avg != null && !Float.isNaN(cx) && !Float.isNaN(cy)) {
                // "M cx cy m -r, 0 a r,r 0 1,1 (r * 2),0 a r,r 0 1,1 -(r * 2),0"
                PathBuilder builder = new PathBuilder();
                builder.absoluteMoveTo(cx, cy);
                builder.relativeMoveTo(-radius, 0);
                builder.relativeArcTo(radius, radius, false, true, true, 2 * radius, 0);
                builder.relativeArcTo(radius, radius, false, true, true, -2 * radius, 0);
                child.setPathData(builder.toString());
            }
        }
    }

    /** Convert ellipse element into a path. */
    private static void extractEllipseItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "ellipse found" + currentGroupNode.getTextContent());

        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {
            float cx = 0;
            float cy = 0;
            float rx = 0;
            float ry = 0;

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();
            boolean pureTransparent = false;
            for (int j = 0; j < len; j++) {
                Node n = a.item(j);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                    if (value.contains("opacity:0;")) {
                        pureTransparent = true;
                    }
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if (name.equals("clip-path")) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals("cx")) {
                    cx = Float.parseFloat(value);
                } else if (name.equals("cy")) {
                    cy = Float.parseFloat(value);
                } else if (name.equals("rx")) {
                    rx = Float.parseFloat(value);
                } else if (name.equals("ry")) {
                    ry = Float.parseFloat(value);
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("ellipse." + value, child);
                    avg.addAffectedNodeToStyleClass("." + value, child);
                }
            }

            if (!pureTransparent && avg != null
                    && !Float.isNaN(cx) && !Float.isNaN(cy)
                    && rx > 0 && ry > 0) {
                // "M cx -rx, cy a rx,ry 0 1,0 (rx * 2),0 a rx,ry 0 1,0 -(rx * 2),0"
                PathBuilder builder = new PathBuilder();
                builder.absoluteMoveTo(cx - rx, cy);
                builder.relativeArcTo(rx, ry, false, true, false, 2 * rx, 0);
                builder.relativeArcTo(rx, ry, false, true, false, -2 * rx, 0);
                builder.relativeClose();
                child.setPathData(builder.toString());
            }
        }
    }

    /** Convert line element into a path. */
    private static void extractLineItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "line found" + currentGroupNode.getTextContent());

        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {
            float x1 = 0;
            float y1 = 0;
            float x2 = 0;
            float y2 = 0;

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();
            boolean pureTransparent = false;
            for (int j = 0; j < len; j++) {
                Node n = a.item(j);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                    if (value.contains("opacity:0;")) {
                        pureTransparent = true;
                    }
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if (name.equals("clip-path")) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals("x1")) {
                    x1 = Float.parseFloat(value);
                } else if (name.equals("y1")) {
                    y1 = Float.parseFloat(value);
                } else if (name.equals("x2")) {
                    x2 = Float.parseFloat(value);
                } else if (name.equals("y2")) {
                    y2 = Float.parseFloat(value);
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("line." + value, child);
                    avg.addAffectedNodeToStyleClass("." + value, child);
                }
            }

            if (!pureTransparent && avg != null && !Float.isNaN(x1) && !Float.isNaN(y1)
                    && !Float.isNaN(x2) && !Float.isNaN(y2)) {
                // "M x1, y1 L x2, y2"
                PathBuilder builder = new PathBuilder();
                builder.absoluteMoveTo(x1, y1);
                builder.absoluteLineTo(x2, y2);
                child.setPathData(builder.toString());
            }
        }

    }

    private static void extractPathItem(
            SvgTree avg, SvgLeafNode child, Node currentGroupNode, SvgGroupNode currentGroup) {
        logger.log(Level.FINE, "Path found " + currentGroupNode.getTextContent());

        if (currentGroupNode.getNodeType() == Node.ELEMENT_NODE) {

            NamedNodeMap a = currentGroupNode.getAttributes();
            int len = a.getLength();

            for (int j = 0; j < len; j++) {
                Node n = a.item(j);
                String name = n.getNodeName();
                String value = n.getNodeValue();
                if (name.equals(SVG_STYLE)) {
                    addStyleToPath(child, value);
                } else if (presentationMap.containsKey(name)) {
                    child.fillPresentationAttributes(name, value);
                } else if ("clip-path".equals(name)) {
                    avg.addClipPathAffectedNode(child, currentGroup);
                } else if (name.equals(SVG_D)) {
                    String pathData = Pattern.compile("(\\d)-").matcher(value).replaceAll("$1,-");
                    child.setPathData(pathData);
                } else if (name.equals("class")) {
                    avg.addAffectedNodeToStyleClass("path." + value, child);
                    avg.addAffectedNodeToStyleClass("." + value, child);
                }

            }
        }
    }

    private static void addStyleToPath(SvgNode path, String value) {
        logger.log(Level.FINE, "Style found is " + value);
        if (value != null) {
            String[] parts = value.split(";");
            for (int k = parts.length - 1; k >= 0; k--) {
                String subStyle = parts[k];
                String[] nameValue = subStyle.split(":");
                if (nameValue.length == 2 && nameValue[0] != null && nameValue[1] != null) {
                    String attr = nameValue[0].trim();
                    String val = nameValue[1].trim();
                    if (presentationMap.containsKey(attr)) {
                        path.fillPresentationAttributes(attr, val);
                    } else if (attr.equals(SVG_OPACITY)) {
                        // TODO: This is hacky, since we don't have a group level
                        // android:opacity. This only works when the path didn't overlap.
                        path.fillPresentationAttributes(SVG_FILL_OPACITY, nameValue[1]);
                    }
                }
            }
        }
    }

    private static final String head = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n";

    private static final String aaptBound = "xmlns:aapt=\"http://schemas.android.com/aapt\"\n";

    private static String getSizeString(float w, float h, float scaleFactor) {
        String size = "        android:width=\"" + (int) (w * scaleFactor) + "dp\"\n" +
                      "        android:height=\"" + (int) (h * scaleFactor) + "dp\"\n";
        return size;
    }

    private static void writeFile(OutputStream outStream, SvgTree svgTree) throws IOException {

        OutputStreamWriter fw = new OutputStreamWriter(outStream);
        fw.write(head);
        if (svgTree.getHasGradient()) {
            fw.write(aaptBound);
        }
        float viewportWidth = svgTree.getViewportWidth();
        float viewportHeight = svgTree.getViewportHeight();

        fw.write(getSizeString(svgTree.getWidth(), svgTree.getHeight(), svgTree.getScaleFactor()));

        fw.write("        android:viewportWidth=\"" + viewportWidth + "\"\n");
        fw.write("        android:viewportHeight=\"" + viewportHeight + "\">\n");

        svgTree.normalize();
        // TODO: this has to happen in the tree mode!!!
        writeXML(svgTree, fw);
        fw.write("</vector>\n");

        fw.close();
    }

    private static void writeXML(SvgTree svgTree, OutputStreamWriter fw) throws IOException {
        if (svgTree.getRoot() == null) {
            throw new NullPointerException("SvgTree root is null.");
        }
        svgTree.getRoot().writeXML(fw, false);
    }

    /**
     * Convert a SVG file into VectorDrawable's XML content, if no error is found.
     *
     * @param inputSVG the input SVG file
     * @param outStream the converted VectorDrawable's content. This can be empty if there is any
     *     error found during parsing
     * @return the error messages, which contain things like all the tags VectorDrawable don't
     *     support or exception message.
     */
    public static String parseSvgToXml(File inputSVG, OutputStream outStream) {
        // Write all the error message during parsing into SvgTree. and return here as getErrorLog().
        // We will also log the exceptions here.
        String errorLog;
        try {
            SvgTree svgTree = parse(inputSVG);
            errorLog = svgTree.getErrorLog();
            if (svgTree.getHasLeafNode()) {
                writeFile(outStream, svgTree);
            }
        } catch (Exception e) {
            errorLog = "EXCEPTION in parsing " + inputSVG.getName() + ":\n" + e.getMessage();
        }
        return errorLog;
    }
}
