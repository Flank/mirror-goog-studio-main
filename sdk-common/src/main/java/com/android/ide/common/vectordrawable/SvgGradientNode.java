/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.utils.XmlUtils.formatFloatAttribute;

import com.google.common.collect.ImmutableMap;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Node;

/** Represents a SVG gradient that is referenced by a SvgLeafNode. */
public class SvgGradientNode extends SvgNode {

    private static final Logger logger = Logger.getLogger(SvgGroupNode.class.getSimpleName());

    private final ArrayList<GradientStop> myGradientStops = new ArrayList<>();

    private SvgLeafNode mSvgLeafNode;

    // Bounding box of mSvgLeafNode.
    private Rectangle2D boundingBox;

    private GradientUsage mGradientUsage;

    private static class GradientCoordResult {
        private final double mValue;
        // When the gradientUnits is set to "userSpaceOnUse", we usually use the coordinate values
        // as it is. But if the coordinate value is a percentage, we still need to multiply this
        // percentage with the viewport's bounding box, in a similar way as gradientUnits is set
        // to "objectBoundingBox".
        private final boolean mIsPercentage;

        public GradientCoordResult(double value, boolean isPercentage) {
            mValue = value;
            mIsPercentage = isPercentage;
        }

        public double getValue() {
            return mValue;
        }

        public boolean isPercentage() {
            return mIsPercentage;
        }
    }

    protected enum GradientUsage {
        FILL,
        STROKE
    }

    // Maps the gradient vector's coordinate names to an int for easier array lookup.
    private static final ImmutableMap<String, Integer> vectorCoordinateMap =
            ImmutableMap.<String, Integer>builder()
                    .put("x1", 0)
                    .put("y1", 1)
                    .put("x2", 2)
                    .put("y2", 3)
                    .build();

    public SvgGradientNode(SvgTree svgTree, Node node, String nodeName) {
        super(svgTree, node, nodeName);
    }

    @Override
    public SvgGradientNode deepCopy() {
        SvgGradientNode newInstance = new SvgGradientNode(getTree(), getDocumentNode(), getName());
        copyTo(newInstance);
        return newInstance;
    }

    @Override
    public boolean isGroupNode() {
        return false;
    }

    /**
     * We do not copy mSvgLeafNode, boundingBox, or mGradientUsage because they will be set after
     * copying the SvgGradientNode. We always call deepCopy of SvgGradientNodes within a SvgLeafNode
     * and then call setSvgLeafNode for that leaf. We calculate the boundingBox and determine the
     * mGradientUsage based on the leaf node's attributes and reference to the gradient being
     * copied.
     */
    protected void copyTo(SvgGradientNode newInstance) {
        super.copyTo(newInstance);
        for (GradientStop g : myGradientStops) {
            newInstance.addGradientStop(g.getColor(), g.getOffset(), g.getOpacity());
        }
    }

    @Override
    public void dumpNode(String indent) {
        // Print the current node.
        logger.log(Level.FINE, indent + "current gradient is :" + getName());
    }

    @Override
    public void transformIfNeeded(AffineTransform rootTransform) {
        AffineTransform finalTransform = new AffineTransform(rootTransform);
        finalTransform.concatenate(mStackedTransform);
    }

    @Override
    public void flatten(AffineTransform transform) {
        mStackedTransform.setTransform(transform);
        mStackedTransform.concatenate(mLocalTransform);
    }

    /** Parses the gradient coordinate value given as a percentage or a length. Returns a double. */
    private GradientCoordResult getGradientCoordinate(String x, double defaultValue) {
        boolean isPercentage = false;
        if (!mVdAttributesMap.containsKey(x)) {
            return new GradientCoordResult(defaultValue, isPercentage);
        }
        double val = defaultValue;
        String vdValue = mVdAttributesMap.get(x).trim();
        if (x.equals("r") && vdValue.startsWith("-")) {
            return new GradientCoordResult(defaultValue, isPercentage);
        }
        if (vdValue.endsWith("%")) {
            isPercentage = true;
            try {
                val = Double.parseDouble(vdValue.substring(0, vdValue.length() - 1));
            } catch (NumberFormatException e) {
                getTree()
                        .logErrorLine(
                                "Unsupported coordinate percentage value",
                                getDocumentNode(),
                                SvgTree.SvgLogLevel.ERROR);
            }
            val /= 100;
        } else {
            try {
                val = Double.parseDouble(vdValue);
            } catch (NumberFormatException e) {
                getTree()
                        .logErrorLine(
                                "Unsupported coordinate value",
                                getDocumentNode(),
                                SvgTree.SvgLogLevel.ERROR);
            }
        }
        return new GradientCoordResult(val, isPercentage);
    }

    /** Writes the XML defining the gradient within a path. */
    @Override
    public void writeXML(OutputStreamWriter writer, boolean inClipPath) throws IOException {
        if (myGradientStops.isEmpty()) {
            getTree()
                    .logErrorLine(
                            "Gradient has no stop info.",
                            getDocumentNode(),
                            SvgTree.SvgLogLevel.ERROR);
            return;
        }
        if (mGradientUsage == GradientUsage.FILL) {
            writer.write("        <aapt:attr name=\"android:fillColor\">\n");
        } else {
            writer.write("        <aapt:attr name=\"android:strokeColor\">\n");
        }
        writer.write("<gradient \n");

        // By default, the dimensions of the gradient is the bounding box of the path.
        setBoundingBox();
        double height = boundingBox.getHeight();
        double width = boundingBox.getWidth();
        double startX = boundingBox.getX();
        double startY = boundingBox.getY();

        String gradientUnit = mVdAttributesMap.get("gradientUnits");
        boolean isUserSpaceOnUse = "userSpaceOnUse".equals(gradientUnit);
        // If gradientUnits is specified to be "userSpaceOnUse", we use the image's dimensions.
        if (isUserSpaceOnUse) {
            startX = 0;
            startY = 0;
            height = getTree().getHeight();
            width = getTree().getWidth();
        }

        // TODO: Fix matrix transformations that include skew element and SVGs that define scale before rotate.
        // Additionally skew transformations have not been tested.
        // If there is a gradientTransform parse and store in mLocalTransform.
        AffineTransform identity = new AffineTransform();
        if (mVdAttributesMap.containsKey("gradientTransform")) {
            String transformValue = mVdAttributesMap.get("gradientTransform");
            parseLocalTransform(transformValue);
            if (!isUserSpaceOnUse) {
                identity.scale(1.0 / width, 1.0 / height);
                mLocalTransform.concatenate(identity);
                identity.setToIdentity();
                identity.scale(width, height);
                mLocalTransform.preConcatenate(identity);
            }
        }

        // Apply the path's transformations to the gradient.
        mLocalTransform.preConcatenate(mSvgLeafNode.mStackedTransform);

        // Source and target arrays to which we apply the local transform.
        double[] gradientBounds;
        double[] transformedBounds;

        String gradientType = "linear";

        if (mVdAttributesMap.containsKey("gradientType")) {
            gradientType = mVdAttributesMap.get("gradientType");
        }

        if (gradientType.equals("linear")) {
            gradientBounds = new double[4];
            transformedBounds = new double[4];
            // Retrieves x1, y1, x2, y2 and calculates their coordinate in the viewport.
            // Stores the coordinates in the gradientBounds and transformedBounds arrays to apply
            // the proper transformation.
            for (Map.Entry<String, Integer> entry : vectorCoordinateMap.entrySet()) {
                // Gets the index corresponding to x1, y1, x2 and y2.
                // x1 and x2 are indexed as 0 and 2
                // y1 and y2 are indexed as 1 and 3
                String s = entry.getKey();
                int index = entry.getValue();

                // According to SVG spec, the default coordinate value for x1, and y1 and y2 is 0.
                // The default for x2 is 1.
                double defaultValue = 0;
                if (index == 2) {
                    defaultValue = 1;
                }
                GradientCoordResult result = getGradientCoordinate(s, defaultValue);

                double coordValue = result.getValue();
                if (!isUserSpaceOnUse || result.isPercentage()) {
                    if (index % 2 == 0) {
                        coordValue = coordValue * width + startX;
                    } else {
                        coordValue = coordValue * height + startY;
                    }
                }
                // In case no transforms are applied, original coordinates are also stored in
                // transformedBounds.
                gradientBounds[index] = coordValue;
                transformedBounds[index] = coordValue;

                // We need mVdAttributesMap to contain all coordinates regardless if they are
                // specified in the SVG in order to write the default value to the VD XML.
                if (!mVdAttributesMap.containsKey(s)) {
                    mVdAttributesMap.put(s, "");
                }
            }
            // transformedBounds will hold the new coordinates of the gradient.
            // This applies it to the linearGradient
            mLocalTransform.transform(gradientBounds, 0, transformedBounds, 0, 2);
        } else {
            gradientBounds = new double[2];
            transformedBounds = new double[2];
            GradientCoordResult cxResult = getGradientCoordinate("cx", .5);
            double cx = cxResult.getValue();
            if (!isUserSpaceOnUse || cxResult.isPercentage()) {
                cx = width * cx + startX;
            }
            GradientCoordResult cyResult = getGradientCoordinate("cy", .5);
            double cy = cyResult.getValue();
            if (!isUserSpaceOnUse || cyResult.isPercentage()) {
                cy = height * cy + startY;
            }
            GradientCoordResult rResult = getGradientCoordinate("r", .5);
            double r = rResult.getValue();
            if (!isUserSpaceOnUse || rResult.isPercentage()) {
                r *= Math.max(height, width);
            }

            gradientBounds[0] = cx;
            transformedBounds[0] = cx;
            gradientBounds[1] = cy;
            transformedBounds[1] = cy;

            // Transform radius, center point here.
            mLocalTransform.transform(gradientBounds, 0, transformedBounds, 0, 1);
            Point2D radius = new Point2D.Double(r, 0);
            Point2D transformedRadius = new Point2D.Double(r, 0);
            mLocalTransform.deltaTransform(radius, transformedRadius);

            mVdAttributesMap.put("cx", formatFloatAttribute(transformedBounds[0]));
            mVdAttributesMap.put("cy", formatFloatAttribute(transformedBounds[1]));
            mVdAttributesMap.put("r", formatFloatAttribute(transformedRadius.distance(0, 0)));
        }

        for (String key : mVdAttributesMap.keySet()) {
            String gradientAttr = Svg2Vector.gradientMap.get(key);
            String svgValue = mVdAttributesMap.get(key);
            String vdValue = svgValue.trim();
            if (vdValue.startsWith("rgb")) {
                vdValue = vdValue.substring(3, vdValue.length());
                String vdValueRGB = vdValue;
                vdValue = SvgLeafNode.convertRGBToHex(vdValue.substring(3, vdValue.length()));
                if (vdValue == null) {
                    getTree()
                            .logErrorLine(
                                    "Unsupported Color format " + vdValueRGB,
                                    getDocumentNode(),
                                    SvgTree.SvgLogLevel.ERROR);
                }
            } else if (SvgLeafNode.colorMap.containsKey(vdValue.toLowerCase(Locale.ENGLISH))) {
                vdValue = SvgLeafNode.colorMap.get(vdValue.toLowerCase(Locale.ENGLISH));
            } else if (vectorCoordinateMap.containsKey(key)) {
                double x = transformedBounds[vectorCoordinateMap.get(key)];
                vdValue = formatFloatAttribute(x);
            } else if (key.equals("spreadMethod")) {
                if (vdValue.equals("pad")) {
                    vdValue = "clamp";
                } else if (vdValue.equals("reflect")) {
                    vdValue = "mirror";
                }
            } else if (vdValue.endsWith("%")) {
                vdValue = formatFloatAttribute(getGradientCoordinate(key, 0).getValue());
            }
            if (!gradientAttr.isEmpty()) {
                writer.write("\n        " + gradientAttr + "=\"" + vdValue + "\"");
            }
        }
        writer.write(">\n");

        writeGradientStops(writer);
        writer.write("            </gradient>");
        writer.write("</aapt:attr>");
    }

    private void writeGradientStops(OutputStreamWriter writer) throws IOException {
        for (GradientStop g : myGradientStops) {
            g.formatStopAttributes();
            String color = g.getColor();
            float opacity = 1;
            try {
                opacity = Float.parseFloat(g.getOpacity());
            } catch (NumberFormatException e) {
                getTree()
                        .logErrorLine(
                                "Unsupported opacity value",
                                getDocumentNode(),
                                SvgTree.SvgLogLevel.WARNING);
            }
            int color1 = VdPath.applyAlpha(VdPath.calculateColor(color), opacity);
            StringBuilder hex =
                    new StringBuilder(Integer.toHexString(color1).toUpperCase(Locale.ENGLISH));
            while (hex.length() < 8) {
                hex.insert(0, "0");
            }
            color = "#" + hex;

            writer.write("<item android:offset=\"" + g.getOffset() + "\"");
            writer.write(" android:color=\"" + color + "\" />\n");

            if (myGradientStops.size() == 1) {
                getTree()
                        .logErrorLine(
                                "Gradient only have one color stops",
                                getDocumentNode(),
                                SvgTree.SvgLogLevel.WARNING);
                writer.write("<item android:offset=\"1.0\"");
                writer.write(" android:color=\"" + color + "\" />\n");
            }
        }
    }

    public void addGradientStop(String color, String offset, String opacity) {
        GradientStop stop = new GradientStop(color, offset);
        stop.setOpacity(opacity);
        myGradientStops.add(stop);
    }

    public void setGradientUsage(GradientUsage gradientUsage) {
        mGradientUsage = gradientUsage;
    }

    public void setSvgLeafNode(SvgLeafNode mSvgLeafNode) {
        this.mSvgLeafNode = mSvgLeafNode;
    }

    private void setBoundingBox() {
        Path2D svgPath = new Path2D.Double();
        VdPath.Node[] nodes = PathParser.parsePath(mSvgLeafNode.getPathData());
        VdNodeRender.createPath(nodes, svgPath);
        boundingBox = svgPath.getBounds2D();
    }
}
