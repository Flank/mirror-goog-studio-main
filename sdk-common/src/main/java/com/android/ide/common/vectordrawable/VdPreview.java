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

import static com.google.common.math.DoubleMath.roundToInt;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.util.AssetUtil;
import com.android.utils.XmlUtils;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Generates an image based on the VectorDrawable's XML content.
 */
public class VdPreview {
    private static final String ANDROID_ALPHA = "android:alpha";
    private static final String ANDROID_TINT = "android:tint";
    private static final String ANDROID_AUTO_MIRRORED = "android:autoMirrored";
    private static final String ANDROID_HEIGHT = "android:height";
    private static final String ANDROID_WIDTH = "android:width";
    public static final int MAX_PREVIEW_IMAGE_SIZE = 4096;
    public static final int MIN_PREVIEW_IMAGE_SIZE = 1;

    /**
     * Parses a vector drawable XML file into a {@link Document} object.
     *
     * @param xmlFileContent the content of the VectorDrawable's XML file.
     * @param errorLog when errors were found, log them in this builder if it is not null.
     * @return parsed document or null if errors happened.
     */
    @Nullable
    public static Document parseVdStringIntoDocument(
            @NonNull String xmlFileContent, @Nullable StringBuilder errorLog) {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db;
      Document document;
      try {
        db = dbf.newDocumentBuilder();
        document = db.parse(new InputSource(new StringReader(xmlFileContent)));
      }
      catch (Exception e) {
        if (errorLog != null) {
          errorLog.append("Exception while parsing XML file:\n").append(e.getMessage());
        }
        return null;
      }
      return document;
    }

    /**
     * Encapsulates the information used to determine the preview image size. The reason we have
     * different ways here is that both Studio UI and build process need to use this common code
     * path to generate images for vector drawables. When {@code maxDimension} is not zero, use
     * {@code maxDimension} as the maximum dimension value while keeping the aspect ratio.
     * Otherwise, use {@code imageScale} to scale the image based on the XML's size information.
     */
    public static class TargetSize {
        private int imageMaxDimension;
        private double imageScale;

        private TargetSize(int maxDimension, double imageScale) {
            this.imageMaxDimension = maxDimension;
            this.imageScale = imageScale;
        }

        public static TargetSize createFromMaxDimension(int maxDimension) {
            return new TargetSize(maxDimension, 0);
        }

        public static TargetSize createFromScale(float imageScale) {
            return new TargetSize(0, imageScale);
        }
    }

    /**
     * Since we allow overriding the vector drawable's size, we also need to keep
     * the original size and aspect ratio.
     */
    public static class SourceSize {
        public float getHeight() {
            return sourceHeight;
        }

        public float getWidth() {
            return sourceWidth;
        }

        private float sourceWidth;
        private float sourceHeight;
    }

    /**
     * Returns a format object for XML formatting.
     */
    @NonNull
    private static OutputFormat getPrettyPrintFormat() {
        OutputFormat format = new OutputFormat();
        format.setLineWidth(120);
        format.setIndenting(true);
        format.setIndent(4);
        format.setEncoding("UTF-8");
        format.setOmitComments(true);
        format.setOmitXMLDeclaration(true);
        return format;
    }

    /**
     * Returns the vector drawable's original size.
     */
    public static SourceSize getVdOriginalSize(@NonNull Document document) {
        Element root = document.getDocumentElement();
        SourceSize srcSize = new SourceSize();
        // Update attributes, note that attributes as width and height are required,
        // while others are optional.
        NamedNodeMap attr = root.getAttributes();
        Node nodeAttr = attr.getNamedItem(ANDROID_WIDTH);
        assert nodeAttr != null;
        srcSize.sourceWidth = parseDimension(0, nodeAttr, false);

        nodeAttr = attr.getNamedItem(ANDROID_HEIGHT);
        assert nodeAttr != null;
        srcSize.sourceHeight = parseDimension(0, nodeAttr, false);
        return srcSize;
    }

    /**
     * The UI can override some properties of the Vector drawable.
     * In order to override in an uniform way, we re-parse the XML file
     * and pick the appropriate attributes to override.
     *
     * @param document the parsed document of original VectorDrawable's XML file.
     * @param info incoming override information for VectorDrawable.
     * @param errorLog log for the parsing errors and warnings.
     * @return the overridden XML file in one string. If exception happens
     *     or no attributes needs to be overridden, return null.
     */
    @Nullable
    public static String overrideXmlContent(@NonNull Document document,
                                            @NonNull VdOverrideInfo info,
                                            @Nullable StringBuilder errorLog) {
        boolean isXmlFileContentChanged = false;
        Element root = document.getDocumentElement();

        // Update attributes, note that attributes as width and height are required,
        // while others are optional.
        NamedNodeMap attr = root.getAttributes();
        if (info.needsOverrideWidth()) {
            Node nodeAttr = attr.getNamedItem(ANDROID_WIDTH);
            float overrideValue = info.getWidth();
            float originalValue = parseDimension(overrideValue, nodeAttr, true);
            if (originalValue != overrideValue) {
                isXmlFileContentChanged = true;
            }
        }
        if (info.needsOverrideHeight()) {
            Node nodeAttr = attr.getNamedItem(ANDROID_HEIGHT);
            float overrideValue = info.getHeight();
            float originalValue = parseDimension(overrideValue, nodeAttr, true);
            if (originalValue != overrideValue) {
                isXmlFileContentChanged = true;
            }
        }
        if (info.needsOverrideAlpha()) {
            String alphaValue = XmlUtils.formatFloatAttribute(info.getAlpha());
            Node nodeAttr = attr.getNamedItem(ANDROID_ALPHA);
            if (nodeAttr != null) {
                nodeAttr.setTextContent(alphaValue);
            }
            else {
                root.setAttribute(ANDROID_ALPHA, alphaValue);
            }
            isXmlFileContentChanged = true;
        }

        if (info.needsOverrideTint()) {
            String tintValue = String.format("#%06X", info.tintRgb());
            Node nodeAttr = attr.getNamedItem(ANDROID_TINT);
            if (nodeAttr != null) {
                nodeAttr.setTextContent(tintValue);
            }
            else {
                root.setAttribute(ANDROID_TINT, tintValue);
            }
            isXmlFileContentChanged = true;
        }

        if (info.getAutoMirrored()) {
            Node nodeAttr = attr.getNamedItem(ANDROID_AUTO_MIRRORED);
            if (nodeAttr != null) {
                nodeAttr.setTextContent("true");
            }
            else {
                root.setAttribute(ANDROID_AUTO_MIRRORED, "true");
            }
            isXmlFileContentChanged = true;
        }

        if (isXmlFileContentChanged) {
            // Prettify the XML string from the document.
            StringWriter stringOut = new StringWriter();
            XMLSerializer serial = new XMLSerializer(stringOut, getPrettyPrintFormat());
            try {
                serial.serialize(document);
            }
            catch (IOException e) {
                if (errorLog != null) {
                    errorLog.append("Exception while parsing XML file:\n").append(e.getMessage());
                }
            }
            return stringOut.toString();
        }

        return null;
    }

    /**
     * Queries the dimension info and overrides it if needed.
     *
     * @param overrideValue the dimension value to override with.
     * @param nodeAttr the node who contains dimension info.
     * @param override if true then override the dimension.
     * @return the original dimension value.
     */
    private static float parseDimension(float overrideValue, Node nodeAttr, boolean override) {
        assert nodeAttr != null;
        String content = nodeAttr.getTextContent();
        assert content.endsWith("dp");
        double originalValue = Double.parseDouble(content.substring(0, content.length() - 2));

        if (override) {
            nodeAttr.setTextContent(XmlUtils.formatFloatAttribute(overrideValue) + "dp");
        }
        return (float) originalValue;
    }

    /**
     * Generates an image according to the VectorDrawable's content {@code xmlFileContent}.
     * At the same time, {@code vdErrorLog} captures all the errors found during parsing.
     * The size of image is determined by the {@code size}.
     *
     * @param targetSize the size of result image.
     * @param xmlFileContent  VectorDrawable's XML file's content.
     * @param errorLog      log for the parsing errors and warnings.
     * @return an preview image according to the VectorDrawable's XML
     */
    @Nullable
    public static BufferedImage getPreviewFromVectorXml(@NonNull TargetSize targetSize,
                                                        @Nullable String xmlFileContent,
                                                        @Nullable StringBuilder errorLog) {
        if (xmlFileContent == null || xmlFileContent.isEmpty()) {
            return null;
        }

        InputStream inputStream =
                new ByteArrayInputStream(xmlFileContent.getBytes(StandardCharsets.UTF_8));
        VdTree vdTree = VdParser.parse(inputStream, errorLog);

        return getPreviewFromVectorTree(targetSize, vdTree, errorLog);
    }

    /**
     * This generates an image from a vector tree.
     * The size of image is determined by the {@code size}.
     *
     * @param targetSize the size of result image.
     * @param xml        The vector drawable XML document
     * @param vdErrorLog log for the errors and warnings.
     * @return an preview image according to the VectorDrawable's XML
     */
    @NonNull
    public static BufferedImage getPreviewFromVectorDocument(@NonNull TargetSize targetSize,
                                                             @NonNull Document xml,
                                                             @Nullable StringBuilder vdErrorLog) {
        VdTree vdTree = new VdTree();
        vdTree.parse(xml);
        return getPreviewFromVectorTree(targetSize, vdTree, vdErrorLog);
    }

    /**
     * Generates an image from a vector tree. The size of image is determined by the {@code size}.
     *
     * @param targetSize the size of result image.
     * @param vdTree     The vector drawable
     * @param errorLog log for the errors and warnings.
     * @return an preview image according to the VectorDrawable's XML
     */
    @NonNull
    public static BufferedImage getPreviewFromVectorTree(@NonNull TargetSize targetSize,
                                                         @NonNull VdTree vdTree,
                                                         @Nullable StringBuilder errorLog) {
        // If the forceImageSize is set (>0), then we honor that.
        // Otherwise, we will ask the vector drawable for the prefer size, then apply the imageScale.
        double vdWidth = vdTree.getBaseWidth();
        double vdHeight = vdTree.getBaseHeight();
        double imageWidth;
        double imageHeight;
        int forceImageSize = targetSize.imageMaxDimension;
        double imageScale = targetSize.imageScale;

        if (forceImageSize > 0) {
            // The goal here is to generate an image within certain size, while preserving
            // the aspect ratio as accurately as we can. If it is scaling too much to fit in,
            // we log an error.
            double maxVdSize = Math.max(vdWidth, vdHeight);
            double ratioToForceImageSize = forceImageSize / maxVdSize;
            double scaledWidth = ratioToForceImageSize * vdWidth;
            double scaledHeight = ratioToForceImageSize * vdHeight;
            imageWidth =
                    limitToInterval(scaledWidth, MIN_PREVIEW_IMAGE_SIZE, MAX_PREVIEW_IMAGE_SIZE);
            imageHeight =
                    limitToInterval(scaledHeight, MIN_PREVIEW_IMAGE_SIZE, MAX_PREVIEW_IMAGE_SIZE);
            if (errorLog != null && (scaledWidth != imageWidth || scaledHeight != imageHeight)) {
                errorLog.append("Invalid image size, can't fit in a square whose size is ")
                        .append(forceImageSize);
            }
        } else {
            imageWidth = vdWidth * imageScale;
            imageHeight = vdHeight * imageScale;
        }

        // Create the image according to the vector drawable's aspect ratio.
        BufferedImage image =
                AssetUtil.newArgbBufferedImage(
                        roundToInt(imageWidth, RoundingMode.HALF_UP),
                        roundToInt(imageHeight, RoundingMode.HALF_UP));
        vdTree.drawIntoImage(image);
        return image;
    }

    private static double limitToInterval(double value, double begin, double end) {
        return Math.max(begin, Math.min(end, value));
    }
}