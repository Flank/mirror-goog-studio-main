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

import static com.android.ide.common.vectordrawable.Svg2Vector.SVG_FILL_OPACITY;
import static com.android.ide.common.vectordrawable.Svg2Vector.SVG_OPACITY;
import static com.android.ide.common.vectordrawable.Svg2Vector.SVG_STROKE_OPACITY;
import static com.android.ide.common.vectordrawable.Svg2Vector.presentationMap;
import static com.android.ide.common.vectordrawable.SvgColor.colorSvg2Vd;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.XmlUtils;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Node;

/** Represents an SVG file's leaf element. */
class SvgLeafNode extends SvgNode {
    private static final Logger logger = Logger.getLogger(SvgLeafNode.class.getSimpleName());

    @Nullable private String mPathData;
    @Nullable private SvgGradientNode mFillGradientNode;
    @Nullable private SvgGradientNode mStrokeGradientNode;

    public SvgLeafNode(@NonNull SvgTree svgTree, @NonNull Node node, @Nullable String nodeName) {
        super(svgTree, node, nodeName);
    }

    @Override
    @NonNull
    public SvgLeafNode deepCopy() {
        SvgLeafNode newInstance = new SvgLeafNode(getTree(), getDocumentNode(), getName());
        newInstance.copyFrom(this);
        return newInstance;
    }

    protected void copyFrom(@NonNull SvgLeafNode from) {
        super.copyFrom(from);
        mPathData = from.mPathData;
    }

    /** Writes attributes of this node. */
    private void writeAttributeValues(@NonNull OutputStreamWriter writer,  @NonNull String indent)
            throws IOException {
        // There could be some redundant opacity information in the attributes' map,
        // like opacity vs fill-opacity / stroke-opacity.
        parsePathOpacity();

        for (Map.Entry<String, String> entry : mVdAttributesMap.entrySet()) {
            String key = entry.getKey();
            String attribute = presentationMap.get(key);
            String svgValue = entry.getValue().trim();
            String vdValue;
            vdValue = colorSvg2Vd(svgValue, "#000000", this);

            if (vdValue == null) {
                if (svgValue.endsWith("px")) {
                    vdValue = svgValue.substring(0, svgValue.length() - 2).trim();
                } else if (svgValue.startsWith("url(#") && svgValue.endsWith(")")) {
                    // Copies gradient from tree
                    vdValue = svgValue.substring(5, svgValue.length() - 1);
                    if (key.equals("fill")) {
                        SvgNode node = getTree().getSvgNodeFromId(vdValue);
                        if (node == null) {
                            continue;
                        }
                        mFillGradientNode = (SvgGradientNode)node.deepCopy();
                        mFillGradientNode.setSvgLeafNode(this);
                        mFillGradientNode.setGradientUsage(SvgGradientNode.GradientUsage.FILL);
                    } else if (key.equals("stroke")) {
                        SvgNode node = getTree().getSvgNodeFromId(vdValue);
                        if (node == null) {
                            continue;
                        }
                        mStrokeGradientNode = (SvgGradientNode)node.deepCopy();
                        mStrokeGradientNode.setSvgLeafNode(this);
                        mStrokeGradientNode.setGradientUsage(SvgGradientNode.GradientUsage.STROKE);
                    }
                    continue;
                } else {
                    vdValue = svgValue;
                }
            }
            writer.write(System.lineSeparator());
            writer.write(indent);
            writer.write(CONTINUATION_INDENT);
            writer.write(attribute);
            writer.write("=\"");
            writer.write(vdValue);
            writer.write("\"");
        }
    }

    /**
     * A utility function to get the opacity value as a floating point number.
     *
     * @param key The key of the opacity
     * @return the clamped opacity value, return 1 if not found.
     */
    private float getOpacityValueFromMap(String key) {
        // Default opacity is 1.
        float result = 1;
        String opacity = mVdAttributesMap.get(key);
        if (opacity != null) {
            try {
                result = Float.parseFloat(opacity);
            } catch (NumberFormatException e) {
                // Ignore here, invalid value is replaced as default value 1.
            }
        }
        return Math.min(Math.max(result, 0), 1);
    }

    /**
     * Parses the SVG path's opacity attribute into fill and stroke.
     */
    private void parsePathOpacity() {
        float opacity = getOpacityValueFromMap(SVG_OPACITY);
        // If opacity is 1, then nothing need to change.
        if (opacity < 1) {
            float fillOpacity = getOpacityValueFromMap(SVG_FILL_OPACITY);
            float strokeOpacity = getOpacityValueFromMap(SVG_STROKE_OPACITY);
            mVdAttributesMap.put(
                    SVG_FILL_OPACITY, XmlUtils.formatFloatAttribute(fillOpacity * opacity));
            mVdAttributesMap.put(
                    SVG_STROKE_OPACITY, XmlUtils.formatFloatAttribute(strokeOpacity * opacity));
        }
        mVdAttributesMap.remove(SVG_OPACITY);
    }

    @Override
    public void dumpNode(@NonNull String indent) {
        logger.log(Level.FINE, indent + (mPathData != null ? mPathData : " null pathData ") +
                               (mName != null ? mName : " null name "));
    }

    public void setPathData(@NonNull String pathData) {
        mPathData = pathData;
    }

    @Nullable
    public String getPathData() {
        return mPathData;
    }

    @Override
    public boolean isGroupNode() {
        return false;
    }

    public boolean hasGradient() {
        return mFillGradientNode != null || mStrokeGradientNode != null;
    }

    @Override
    public void transformIfNeeded(@NonNull AffineTransform rootTransform) {
        if (mPathData == null || mPathData.isEmpty()) {
            // Nothing to draw and transform, early return.
            return;
        }
        VdPath.Node[] n = PathParser.parsePath(mPathData);
        AffineTransform finalTransform = new AffineTransform(rootTransform);
        finalTransform.concatenate(mStackedTransform);
        boolean needsConvertRelativeMoveAfterClose = VdPath.Node.hasRelMoveAfterClose(n);
        if (!finalTransform.isIdentity() || needsConvertRelativeMoveAfterClose) {
            VdPath.Node.transform(finalTransform, n);
        }
        mPathData = VdPath.Node.nodeListToString(n, mSvgTree.getCoordinateFormat());
    }

    @Override
    public void flatten(@NonNull AffineTransform transform) {
        mStackedTransform.setTransform(transform);
        mStackedTransform.concatenate(mLocalTransform);

        if (mVdAttributesMap.containsKey(Svg2Vector.SVG_STROKE_WIDTH)
                && ((mStackedTransform.getType() & AffineTransform.TYPE_MASK_SCALE) != 0)) {
            getTree().logErrorLine("Scaling of the stroke width is ignored",
                                   getDocumentNode(), SvgTree.SvgLogLevel.WARNING);
        }
    }

    @Override
    public void writeXml(
            @NonNull OutputStreamWriter writer, boolean inClipPath, @NonNull String indent)
            throws IOException {
        if (inClipPath) {
            // Write data that is part of the clip-path data.
            writer.write(mPathData);
            // Need to write M 0,0 after each path. Resets pen to the origin since subsequent
            // paths might be relative.
            writer.write(" M 0,0");
            return;
        }

        // First, decide whether or not we can skip this path, since it has no visible effect.
        if (mPathData == null || mPathData.isEmpty()) {
            return; // No path to draw.
        }
        String fillColor = mVdAttributesMap.get(Svg2Vector.SVG_FILL_COLOR);
        String strokeColor = mVdAttributesMap.get(Svg2Vector.SVG_STROKE_COLOR);
        logger.log(Level.FINE, "fill color " + fillColor);
        boolean emptyFill = "none".equals(fillColor) || "#00000000".equals(fillColor);
        boolean emptyStroke = strokeColor == null || "none".equals(strokeColor);
        if (emptyFill && emptyStroke) {
            return; // Nothing to draw.
        }

        // Second, write the color info handling the default values.
        writer.write(indent);
        writer.write("<path");
        writer.write(System.lineSeparator());
        if (fillColor == null && mFillGradientNode == null) {
            logger.log(Level.FINE, "Adding default fill color");
            writer.write(indent);
            writer.write(CONTINUATION_INDENT);
            writer.write("android:fillColor=\"#FF000000\"");
            writer.write(System.lineSeparator());
        }
        if (!emptyStroke
                && !mVdAttributesMap.containsKey(Svg2Vector.SVG_STROKE_WIDTH)
                && mStrokeGradientNode == null) {
            logger.log(Level.FINE, "Adding default stroke width");
            writer.write(indent);
            writer.write(CONTINUATION_INDENT);
            writer.write("android:strokeWidth=\"1\"");
            writer.write(System.lineSeparator());
        }

        // Last, write the path data and all associated attributes.
        writer.write(indent);
        writer.write(CONTINUATION_INDENT);
        writer.write("android:pathData=\"" + mPathData + "\"");
        writeAttributeValues(writer, indent);
        if (!hasGradient()) {
            writer.write('/');
        }
        writer.write('>');
        writer.write(System.lineSeparator());

        if (mFillGradientNode != null) {
            mFillGradientNode.writeXml(writer, false, indent + INDENT_UNIT);
        }
        if (mStrokeGradientNode != null) {
            mStrokeGradientNode.writeXml(writer, false, indent + INDENT_UNIT);
        }
        if (hasGradient()) {
            writer.write(indent);
            writer.write("</path>");
            writer.write(System.lineSeparator());
        }
    }
}
