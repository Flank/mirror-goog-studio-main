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
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.Pair;
import com.android.utils.PositionXmlParser;
import com.google.common.base.Preconditions;
import java.awt.geom.AffineTransform;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Represents the SVG file in an internal data structure as a tree.
 */
class SvgTree {
    private static final Logger logger = Logger.getLogger(SvgTree.class.getSimpleName());

    public static final String SVG_WIDTH = "width";
    public static final String SVG_HEIGHT = "height";
    public static final String SVG_VIEW_BOX = "viewBox";

    private float w = -1;
    private float h = -1;
    private final AffineTransform mRootTransform = new AffineTransform();
    private float[] viewBox;
    private float mScaleFactor = 1;

    private SvgGroupNode mRoot;
    private String mFileName;

    private final ArrayList<LogMessage> mLogMessages = new ArrayList<>();

    private boolean mHasLeafNode;

    private boolean mHasGradient;

    public float getWidth() { return w; }
    public float getHeight() { return h; }
    public float getScaleFactor() { return mScaleFactor; }
    public void setHasLeafNode(boolean hasLeafNode) {
        mHasLeafNode = hasLeafNode;
    }

    public void setHasGradient(boolean hasGradient) {
        mHasGradient = hasGradient;
    }

    public float[] getViewBox() { return viewBox; }

    // Map of SvgNode's id to the SvgNode.
    private final HashMap<String, SvgNode> mIdMap = new HashMap<>();

    // Set of SvgGroupNodes that contain use elements.
    private final HashSet<SvgGroupNode> mUseGroupSet = new HashSet<>();

    // Key is SvgNode that references a clipPath. Value is SvgGroupNode that is the parent of that
    // SvgNode.
    private final Map<SvgNode, Pair<SvgGroupNode, String>> mClipPathAffectedNodes = new HashMap<>();

    // Key is String that is the id of a style class.
    // Value is set of SvgNodes referencing that class.
    private final HashMap<String, HashSet<SvgNode>> mStyleAffectedNodes = new HashMap<>();

    // Key is String that is the id of a style class. Value is a String that contains attribute
    // information of that style class.
    private final HashMap<String, String> mStyleClassAttributeMap = new HashMap<>();

    /** From the root, top down, pass the transformation (TODO: attributes) down the children. */
    public void flatten() {
        mRoot.flatten(new AffineTransform());
    }

    public enum SvgLogLevel {
        ERROR,
        WARNING
    }

    private static class LogMessage implements Comparable<LogMessage> {
        final SvgLogLevel level;
        final int line;
        final String message;

        /**
         * Initializes a log message.
         *
         * @param level the severity level
         * @param line the line number of the SVG file the message applies to,
         *     or zero if the message applies to the whole file
         * @param message the text of the message
         */
        LogMessage(@NonNull SvgLogLevel level, int line, @NonNull String message) {
            this.level = level;
            this.line = line;
            this.message = message;
        }

        @NonNull
        String getFormattedMessage() {
            return level.name() + (line == 0 ? "" : " @ line " + line) + ": " + message;
        }

        @Override
        public int compareTo(@NotNull LogMessage other) {
            int cmp = level.compareTo(other.level);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(line, other.line);
            if (cmp != 0) {
                return cmp;
            }
            return message.compareTo(other.message);
        }
    }

    public Document parse(@NonNull File f) throws Exception {
        mFileName = f.getName();
        return PositionXmlParser.parse(new BufferedInputStream(new FileInputStream(f)), false);
    }

    public void normalize() {
        // mRootTransform is always setup, now just need to apply the viewbox info into.
        mRootTransform.preConcatenate(new AffineTransform(1, 0, 0, 1, -viewBox[0], -viewBox[1]));
        transform(mRootTransform);

        logger.log(Level.FINE, "matrix=" + mRootTransform);
    }

    private void transform(@NonNull AffineTransform rootTransform) {
        mRoot.transformIfNeeded(rootTransform);
    }

    public void dump(@NonNull SvgGroupNode root) {
        logger.log(Level.FINE, "current file is :" + mFileName);
        root.dumpNode("");
    }

    public void setRoot(SvgGroupNode root) {
        mRoot = root;
    }

    @Nullable
    public SvgGroupNode getRoot() {
        return mRoot;
    }

    public void logErrorLine(@NonNull String s, @Nullable Node node, @NonNull SvgLogLevel level) {
        Preconditions.checkArgument(!s.isEmpty());
        int line = node == null ? 0 : getPosition(node).getStartLine() + 1;
        mLogMessages.add(new LogMessage(level, line, s));
    }

    /**
     * Returns the error log. Empty string if there are no errors.
     */
    @NonNull
    public String getErrorLog() {
        if (mLogMessages.isEmpty()) {
            return "";
        }
        Collections.sort(mLogMessages); // Sort by severity and line number.
        StringBuilder result = new StringBuilder();
        result.append("In ").append(mFileName).append(':');
        for (LogMessage message : mLogMessages) {
            result.append('\n');
            result.append(message.getFormattedMessage());
        }
        return result.toString();
    }

    /**
     * @return true when there is at least one valid child.
     */
    public boolean getHasLeafNode() {
        return mHasLeafNode;
    }

    public boolean getHasGradient() {
        return mHasGradient;
    }

    private static SourcePosition getPosition(Node node) {
        return PositionXmlParser.getPosition(node);
    }

    public float getViewportWidth() {
        return (viewBox == null) ? -1 : viewBox[2];
    }

    public float getViewportHeight() { return (viewBox == null) ? -1 : viewBox[3]; }

    private enum SizeType {
        PIXEL,
        PERCENTAGE
    }

    public void parseDimension(@NonNull Node nNode) {
        NamedNodeMap a = nNode.getAttributes();
        int len = a.getLength();
        SizeType widthType = SizeType.PIXEL;
        SizeType heightType = SizeType.PIXEL;
        for (int i = 0; i < len; i++) {
            Node n = a.item(i);
            String name = n.getNodeName().trim();
            String value = n.getNodeValue().trim();
            int subStringSize = value.length();
            SizeType currentType = SizeType.PIXEL;
            String unit = value.substring(Math.max(value.length() - 2, 0));
            if (unit.matches("em|ex|px|in|cm|mm|pt|pc")) {
                subStringSize -= 2;
            } else if (value.endsWith("%")) {
                subStringSize -= 1;
                currentType = SizeType.PERCENTAGE;
            }

            if (SVG_WIDTH.equals(name)) {
                w = Float.parseFloat(value.substring(0, subStringSize));
                widthType = currentType;
            } else if (SVG_HEIGHT.equals(name)) {
                h = Float.parseFloat(value.substring(0, subStringSize));
                heightType = currentType;
            } else if (SVG_VIEW_BOX.equals(name)) {
                viewBox = new float[4];
                String[] strbox = value.split(" ");
                for (int j = 0; j < viewBox.length; j++) {
                    viewBox[j] = Float.parseFloat(strbox[j]);
                }
            }
        }
        // If there is no viewbox, then set it up according to w, h.
        // From now on, viewport should be read from viewBox, and size should be from w and h.
        // w and h can be set to percentage too, in this case, set it to the viewbox size.
        if (viewBox == null && w > 0 && h > 0) {
            viewBox = new float[4];
            viewBox[2] = w;
            viewBox[3] = h;
        } else if ((w < 0 || h < 0) && viewBox != null) {
            w = viewBox[2];
            h = viewBox[3];
        }

        if (widthType == SizeType.PERCENTAGE && w > 0) {
            w = viewBox[2] * w / 100;
        }
        if (heightType == SizeType.PERCENTAGE && h > 0) {
            h = viewBox[3] * h / 100;
        }
    }

    public void addIdToMap(@NonNull String id, @NonNull SvgNode svgNode) {
        mIdMap.put(id, svgNode);
    }

    @Nullable
    public SvgNode getSvgNodeFromId(@NonNull String id) {
        return mIdMap.get(id);
    }

    public void addToUseSet(@NonNull SvgGroupNode useGroup) {
        mUseGroupSet.add(useGroup);
    }

    @NonNull
    public Set<SvgGroupNode> getUseSet() {
        return mUseGroupSet;
    }

    public void addClipPathAffectedNode(
            @NonNull SvgNode child,
            @NonNull SvgGroupNode currentGroup,
            @NonNull String clipPathName) {
        mClipPathAffectedNodes.put(child, Pair.of(currentGroup, clipPathName));
    }

    @NonNull
    public Set<Map.Entry<SvgNode, Pair<SvgGroupNode, String>>> getClipPathAffectedNodesSet() {
        return mClipPathAffectedNodes.entrySet();
    }

    /** Adds child to set of SvgNodes that reference the style class with id className. */
    public void addAffectedNodeToStyleClass(@NonNull String className, @NonNull SvgNode child) {
        if (mStyleAffectedNodes.containsKey(className)) {
            mStyleAffectedNodes.get(className).add(child);
        } else {
            HashSet<SvgNode> styleNodesSet = new HashSet<>();
            styleNodesSet.add(child);
            mStyleAffectedNodes.put(className, styleNodesSet);
        }
    }

    public void addStyleClassToTree(@NonNull String className, @NonNull String attributes) {
        mStyleClassAttributeMap.put(className, attributes);
    }

    public boolean containsStyleClass(@NonNull String classname) {
        return mStyleClassAttributeMap.containsKey(classname);
    }

    public String getStyleClassAttr(@NonNull String classname) {
        return mStyleClassAttributeMap.get(classname);
    }

    @NonNull
    public Set<Map.Entry<String, HashSet<SvgNode>>> getStyleAffectedNodes() {
        return mStyleAffectedNodes.entrySet();
    }

    /**
     * Finds the parent node of the input node.
     *
     * @return the parent node, or null if node is not in the tree.
     */
    @Nullable
    public SvgGroupNode findParent(@NonNull SvgNode node) {
        return mRoot.findParent(node);
    }

    /**
     * Returns a {@link DecimalFormat] of sufficient precision to use for formatting coordinate
     * values within the viewport.
     */
    @NonNull
    public DecimalFormat getCoordinateFormat() {
        float viewportWidth = getViewportWidth();
        float viewportHeight = getViewportHeight();
        return VdUtil.getCoordinateFormat(Math.max(viewportHeight, viewportWidth));
    }
}
