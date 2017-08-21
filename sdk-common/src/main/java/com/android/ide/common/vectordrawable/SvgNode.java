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

import static com.android.ide.common.vectordrawable.Svg2Vector.SVG_STROKE_COLOR;
import static com.android.ide.common.vectordrawable.Svg2Vector.SVG_STROKE_WIDTH;

import com.android.annotations.NonNull;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Parent class for a SVG file's node, can be either group or leaf element. */
abstract class SvgNode {
    private static final Logger logger = Logger.getLogger(SvgNode.class.getSimpleName());

    private static final String TRANSFORM_TAG = "transform";

    private static final String MATRIX_ATTRIBUTE = "matrix";
    private static final String TRANSLATE_ATTRIBUTE = "translate";
    private static final String ROTATE_ATTRIBUTE = "rotate";
    private static final String SCALE_ATTRIBUTE = "scale";
    private static final String SKEWX_ATTRIBUTE = "skewX";
    private static final String SKEWY_ATTRIBUTE = "skewY";

    protected final String mName;
    // Keep a reference to the tree in order to dump the error log.
    private final SvgTree mSvgTree;
    // Use document node to get the line number for error reporting.
    private final Node mDocumentNode;

    // Key is the attributes for vector drawable, and the value is the converted from SVG.
    protected final Map<String, String> mVdAttributesMap = new HashMap<>();
    // If mLocalTransform is identity, it is the same as not having any transformation.
    protected AffineTransform mLocalTransform = new AffineTransform();

    // During the flatten() operation, we need to merge the transformation from top down.
    // This is the stacked transformation. And this will be used for the path data transform().
    protected AffineTransform mStackedTransform = new AffineTransform();

    /**
     * While parsing the translate() rotate() ..., update the <code>mLocalTransform</code>
     */
    public SvgNode(SvgTree svgTree, Node node, String name) {
        mName = name;
        mSvgTree = svgTree;
        mDocumentNode = node;
        // Parse and generate a presentation map.
        NamedNodeMap a = node.getAttributes();
        int len = a.getLength();

        for (int itemIndex = 0; itemIndex < len; itemIndex++) {
            Node n = a.item(itemIndex);
            String nodeName = n.getNodeName();
            String nodeValue = n.getNodeValue();
            // TODO: Handle style here. Refer to Svg2Vector::addStyleToPath().
            if (Svg2Vector.presentationMap.containsKey(nodeName)) {
                fillPresentationAttributes(nodeName, nodeValue, logger);
            }

            if (TRANSFORM_TAG.equals(nodeName)) {
                logger.log(Level.FINE, nodeName + " " + nodeValue);
                parseLocalTransform(nodeValue);
            }
        }
    }

    protected void parseLocalTransform(String nodeValue) {
        // We separate the string into multiple parts and look like this:
        // "translate" "30" "rotate" "4.5e1  5e1  50"
        nodeValue = nodeValue.replaceAll(",", " ");
        String[] matrices = nodeValue.split("\\(|\\)");
        AffineTransform parsedTransform;
        for (int i = 0; i < matrices.length -1; i += 2) {
            parsedTransform = parseOneTransform(matrices[i].trim(), matrices[i+1].trim());
            if (parsedTransform != null) {
                mLocalTransform.concatenate(parsedTransform);
            }
        }
    }

    @NonNull
    private static AffineTransform parseOneTransform(String type, String data) {
        float[] numbers = getNumbers(data);
        int numLength = numbers.length;
        AffineTransform parsedTransform = new AffineTransform();

        if (MATRIX_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 6) {
                return null;
            }
            parsedTransform.setTransform(
                    numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5]);
        } else if (TRANSLATE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 2) {
                return null;
            }
            // Default translateY is 0
            parsedTransform.translate(numbers[0], numLength == 2 ? numbers[1] : 0);
        } else if (SCALE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 2) {
                return null;
            }
            // Default scaleY == scaleX
            parsedTransform.scale(numbers[0], numbers[numLength == 2 ? 1 : 0]);
        } else if (ROTATE_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1 && numLength != 3) {
                return null;
            }
            parsedTransform.rotate(
                    Math.toRadians(numbers[0]),
                    numLength == 3 ? numbers[1] : 0,
                    numLength == 3 ? numbers[2] : 0);
        } else if (SKEWX_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1) {
                return null;
            }
            // Note that Swing is pass the shear value directly to the matrix as m01 or m10,
            // while SVG is using tan(a) in the matrix and a is in radians.
            parsedTransform.shear(Math.tan(Math.toRadians(numbers[0])), 0);
        } else if (SKEWY_ATTRIBUTE.equalsIgnoreCase(type)) {
            if (numLength != 1) {
                return null;
            }
            parsedTransform.shear(0, Math.tan(Math.toRadians(numbers[0])));
        }
        return parsedTransform;
    }

    private static float[] getNumbers(String data) {
        String[] numbers = data.split("\\s+");
        int len = numbers.length;
        if (len == 0) {
            return null;
        }

        float[] results = new float[len];
        for (int i = 0; i < len; i ++) {
            results[i] = Float.parseFloat(numbers[i]);
        }
        return results;
    }

    protected SvgTree getTree() {
        return mSvgTree;
    }

    public String getName() {
        return mName;
    }

    public Node getDocumentNode() {
        return mDocumentNode;
    }

    /**
     * dump the current node's debug info.
     */
    public abstract void dumpNode(String indent);

    /** Write the Node content into the VectorDrawable's XML file. */
    public abstract void writeXML(OutputStreamWriter writer, boolean inClipPath) throws IOException;

    /**
     * @return true the node is a group node.
     */
    public abstract boolean isGroupNode();

    /**
     * Transform the current Node with the transformation matrix.
     */
    public abstract void transformIfNeeded(AffineTransform finalTransform);

    protected void fillPresentationAttributes(String name, String value, Logger logger) {
        if (name.equals("fill-rule")) {
            if (value.equals("nonzero")) {
                value = "nonZero";
            } else if (value.equals("evenodd")) {
                value = "evenOdd";
            }
        }
        logger.log(Level.FINE, ">>>> PROP " + name + " = " + value);
        if (value.startsWith("url("))  {
            if (!name.equals("fill") && !name.equals("stroke")) {
                getTree()
                        .logErrorLine(
                                "Unsupported URL value: " + value,
                                getDocumentNode(),
                                SvgTree.SvgLogLevel.ERROR);
                return;
            }
        }
        if (name.equals(SVG_STROKE_WIDTH) && value.equals("0")) {
            mVdAttributesMap.remove(SVG_STROKE_COLOR);
        }
        mVdAttributesMap.put(name, value);
    }

    protected void fillPresentationAttributes(String name, String value) {
        fillPresentationAttributes(name, value, logger);
    }

    public void fillEmptyAttributes(Map<String, String> parentAttributesMap) {
        // Go through the parents' attributes, if the child misses any, then fill it.
        for (Map.Entry<String, String> entry : parentAttributesMap.entrySet()) {
            String key = entry.getKey();
            if (!mVdAttributesMap.containsKey(key)) {
                mVdAttributesMap.put(key, entry.getValue());
            }
        }
    }

    public abstract void flatten(AffineTransform transform);

    protected String getDecimalFormatString() {
        float viewportWidth = getTree().getViewportWidth();
        float viewportHeight = getTree().getViewportHeight();
        float minSize = Math.min(viewportHeight, viewportWidth);
        float exponent = Math.round(Math.log10(minSize));
        int decimalPlace = (int) Math.floor(exponent - 4);
        StringBuilder decimalFormatStringBuilder = new StringBuilder("#");
        if (decimalPlace < 0) {
            // Build a string with decimal places for "#.##...", and cap on 6 digits.
            if (decimalPlace < -6) {
                decimalPlace = -6;
            }
            decimalFormatStringBuilder.append('.');
            for (int i = 0; i < -decimalPlace; i++) {
                decimalFormatStringBuilder.append('#');
            }
        }
        return decimalFormatStringBuilder.toString();
    }

    /**
     * Returns a String containing the value of the given attribute. Returns an empty string if the
     * attribute does not exist.
     */
    public String getAttributeValue(String attribute) {
        NamedNodeMap a = mDocumentNode.getAttributes();
        String value = "";
        int len = a.getLength();
        for (int j = 0; j < len; j++) {
            Node n = a.item(j);
            String name = n.getNodeName();
            if (name.equals(attribute)) {
                value = n.getNodeValue();
            }
        }
        return value;
    }

    public abstract SvgNode deepCopy();

    protected void copyTo(SvgNode newInstance) {
        newInstance.fillEmptyAttributes(mVdAttributesMap);
        newInstance.mLocalTransform = (AffineTransform) mLocalTransform.clone();
    }

}
