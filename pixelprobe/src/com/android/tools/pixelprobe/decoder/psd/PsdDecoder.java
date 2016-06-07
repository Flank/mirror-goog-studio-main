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

package com.android.tools.pixelprobe.decoder.psd;

import com.android.tools.chunkio.ChunkIO;
import com.android.tools.pixelprobe.*;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.decoder.Decoder;
import com.android.tools.pixelprobe.effect.Shadow;
import com.android.tools.pixelprobe.util.Bytes;
import com.android.tools.pixelprobe.util.Images;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.pixelprobe.decoder.psd.PsdFile.*;

/**
 * Decodes PSD (Adobe Photoshop) streams. Accepts the "psd" and "photoshop"
 * format strings. The PSB variant of the Photoshop format, used to store
 * large images (> 30,000 pixels in either dimension), is currently not
 * supported.
 */
@SuppressWarnings("UseJBColor")
public final class PsdDecoder extends Decoder {
    /**
     * Constant to convert centimeters to inches.
     */
    private static final float CENTIMETER_TO_INCH = 2.54f;

    /**
     * Possible text alignments found in text layers. Alignments are encoded as
     * numbers in PSD file. These numbers map to the indices of this array (0 is
     * left, 1 is right, etc.).
     */
    private static final String[] alignments = new String[] {
        "LEFT", "RIGHT", "CENTER", "JUSTIFY"
    };

    /**
     * The PsdDecoder only supports .psd files. There is no support for .psb
     * (large Photoshop documents) at the moment.
     */
    public PsdDecoder() {
        super("psd", "photoshop");
    }

    @Override
    public boolean accept(InputStream in) {
        // Read the header and make sure it is valid before we accept the stream
        return ChunkIO.read(in, Header.class) != null;
    }

    @Override
    public Image decode(InputStream in) throws IOException {
        try {
            Image.Builder image = new Image.Builder();
            image.format("PSD");

            // The PsdFile class represents the entire document
            PsdFile psd = ChunkIO.read(in, PsdFile.class);

            if (psd != null) {
                // Extract and decode raw PSD data
                // The data is transformed into a generic Image API
                extractHeaderData(image, psd.header);
                resolveBlocks(image, psd.resources);
                extractLayers(image, psd.layersInfo);
                decodeImageData(image, psd);
            }

            return image.build();
        } catch (Throwable t) {
            throw new IOException("Error while decoding PSD stream", t);
        }
    }

    /**
     * Extract layers information from the PSD raw data into the specified image.
     * Not all layers will be extracted.
     *
     * @param image The user image
     * @param layersInfo The document's layers information
     */
    private static void extractLayers(Image.Builder image, LayersInformation layersInfo) {
        LayersList layersList = layersInfo.layers;
        if (layersList == null) return;

        List<RawLayer> rawLayers = layersList.layers;
        Deque<Layer.Builder> stack = new LinkedList<>();

        int[] unnamedCounts = new int[Layer.Type.values().length];
        Arrays.fill(unnamedCounts, 1);

        // The layers count can be negative according to the Photoshop file specification
        // A negative count indicates that the first alpha channel contains the transparency
        // data for the merged (composited) result
        for (int i = Math.abs(layersList.count) - 1; i >= 0; i--) {
            RawLayer rawLayer = rawLayers.get(i);

            Map<String, LayerProperty> properties = rawLayer.extras.properties;
            LayerProperty sectionProperty = properties.get(LayerProperty.KEY_SECTION);

            // Assume we are decoding a bitmap (raster) layer by default
            Layer.Type type = Layer.Type.IMAGE;
            boolean isOpen = true;

            // If the section property is set, the layer is either the beginning
            // or the end of a group of layers
            if (sectionProperty != null) {
                LayerSection.Type groupType = ((LayerSection) sectionProperty.data).type;
                switch (groupType) {
                    case OTHER:
                        continue;
                    case GROUP_CLOSED:
                        isOpen = false;
                        // fall through
                    case GROUP_OPENED:
                        type = Layer.Type.GROUP;
                        break;
                    // A bounding layer is a hidden layer in Photoshop that marks
                    // the end of a group (the name is set to </Layer Group>
                    case BOUNDING:
                        Layer.Builder group = stack.pollFirst();
                        Layer.Builder parent = stack.peekFirst();
                        if (parent == null) {
                            image.addLayer(group.build());
                        } else {
                            parent.addLayer(group.build());
                        }
                        continue;
                }
            } else {
                type = getLayerType(rawLayer);
            }

            String name = getLayerName(rawLayer, type, unnamedCounts);

            // Create the actual layer we return to the user
            Layer.Builder layer = new Layer.Builder(name, type);
            layer.bounds(rawLayer.left, rawLayer.top,
                    rawLayer.right - rawLayer.left, rawLayer.bottom - rawLayer.top)
                    .opacity(rawLayer.opacity / 255.0f)
                    .blendMode(PsdUtils.getBlendMode(rawLayer.blendMode))
                    .open(isOpen)
                    .visible((rawLayer.flags & RawLayer.INVISIBLE) == 0);

            // Get the current parent before we modify the stack
            Layer.Builder parent = stack.peekFirst();

            // Layer-specific decode steps
            switch (type) {
                case ADJUSTMENT:
                    break;
                case IMAGE:
                    decodeLayerImageData(image, layer, rawLayer, layersList.channels.get(i));
                    break;
                case GROUP:
                    stack.offerFirst(layer);
                    break;
                case PATH:
                    decodePathData(image, layer, properties);
                    break;
                case TEXT:
                    decodeTextData(image, layer, properties.get(LayerProperty.KEY_TEXT));
                    break;
            }

            extractLayerEffects(image, layer, rawLayer);

            // Groups are handled when they close
            if (type != Layer.Type.GROUP) {
                if (parent == null) {
                    image.addLayer(layer.build());
                } else {
                    parent.addLayer(layer.build());
                }
            }
        }
    }

    private static Layer.Type getLayerType(RawLayer rawLayer) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;
        Layer.Type type = Layer.Type.IMAGE;

        // The type of layer can only be identified by peeking at the various
        // properties set on that layer
        LayerProperty textProperty = properties.get(LayerProperty.KEY_TEXT);
        if (textProperty != null) {
            type = Layer.Type.TEXT;
        } else {
            LayerProperty vectorMask = properties.get(LayerProperty.KEY_VECTOR_MASK);
            LayerProperty shapeMask = properties.get(LayerProperty.KEY_SHAPE_MASK);
            if (vectorMask != null || shapeMask != null) {
                type = Layer.Type.PATH;
            } else {
                for (String key : PsdUtils.getAdjustmentLayerKeys()) {
                    if (properties.containsKey(key)) {
                        type = Layer.Type.ADJUSTMENT;
                        break;
                    }
                }
            }
        }

        return type;
    }

    private static String getLayerName(RawLayer rawLayer, Layer.Type type, int[] unnamedCounts) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;
        LayerProperty nameProperty = properties.get(LayerProperty.KEY_NAME);

        // The layer's name appears twice in PSD files: first as a legacy
        // Pascal string, then as a Unicode string. We care a lot more about
        // the second one
        String name;
        if (nameProperty != null) {
            name = ((UnicodeString) nameProperty.data).value;
        } else {
            name = rawLayer.extras.name;
        }

        // Generate a name if the layer doesn't have one
        if (name.trim().isEmpty()) {
            name = "<" + getNameForType(type) + " " + unnamedCounts[type.ordinal()]++ + ">";
        }
        return name;
    }

    private static String getNameForType(Layer.Type type) {
        switch (type) {
            case ADJUSTMENT:
                return "Adjustment";
            case IMAGE:
                return "Layer";
            case GROUP:
                return "Group";
            case PATH:
                return "Shape";
            case TEXT:
                return "Text";
        }
        return "Unnamed";
    }

    private enum LayerShadow {
        INNER(LayerEffects.KEY_INNER_SHADOW, LayerEffects.KEY_INNER_SHADOW_MULTI),
        OUTER(LayerEffects.KEY_DROP_SHADOW, LayerEffects.KEY_DROP_SHADOW_MULTI);

        private final String mMultiName;
        private final String mName;

        LayerShadow(String name, String multiName) {
            mName = name;
            mMultiName = multiName;
        }

        String getMultiName() {
            return mMultiName;
        }

        String getName() {
            return mName;
        }
    }

    private static void extractLayerEffects(Image.Builder image, Layer.Builder layer, RawLayer rawLayer) {
        Map<String, LayerProperty> properties = rawLayer.extras.properties;

        LayerProperty property = properties.get(LayerProperty.KEY_MULTI_EFFECTS);
        if (property == null) {
            property = properties.get(LayerProperty.KEY_EFFECTS);
            if (property == null) return;
        }

        LayerEffects layerEffects = (LayerEffects) property.data;
        Effects.Builder effects = new Effects.Builder();

        boolean effectsEnabled = PsdUtils.get(layerEffects.effects, LayerEffects.KEY_MASTER_SWITCH);
        if (effectsEnabled) {
            extractShadowEffects(image, effects, layerEffects, LayerShadow.INNER);
            extractShadowEffects(image, effects, layerEffects, LayerShadow.OUTER);
        }

        layer.effects(effects.build());
    }

    private static void extractShadowEffects(Image.Builder image, Effects.Builder effects,
            LayerEffects layerEffects, LayerShadow shadowType) {

        Descriptor descriptor = PsdUtils.get(layerEffects.effects, shadowType.getName());
        if (descriptor != null) {
            addShadowEffect(image, effects, descriptor, shadowType);
        }

        DescriptorItem.ValueList list = PsdUtils.get(layerEffects.effects, shadowType.getMultiName());
        if (list != null) {
            for (int i = 0; i < list.count; i++) {
                descriptor = (Descriptor) list.items.get(i).data;
                if (shadowType.getName().equals(descriptor.classId.toString())) {
                    addShadowEffect(image, effects, descriptor, shadowType);
                }
            }
        }
    }

    private static void addShadowEffect(Image.Builder image, Effects.Builder effects,
            Descriptor descriptor, LayerShadow type) {

        if (!PsdUtils.getBoolean(descriptor, LayerEffects.KEY_PRESENT) ||
            !PsdUtils.getBoolean(descriptor, LayerEffects.KEY_ENABLED)) {
            return;
        }

        Shadow.Type shadowType = Shadow.Type.INNER;
        if (type == LayerShadow.OUTER) shadowType = Shadow.Type.OUTER;

        float scale = image.verticalResolution() / 72.0f;

        Shadow shadow = new Shadow.Builder(shadowType)
                .blur(PsdUtils.getUnitFloat(descriptor, "blur", scale))
                .angle(PsdUtils.getUnitFloat(descriptor, "lagl", scale))
                .distance(PsdUtils.getUnitFloat(descriptor, "Dstn", scale))
                .opacity(PsdUtils.getUnitFloat(descriptor, "Opct", scale))
                .blendMode(PsdUtils.getBlendMode(PsdUtils.get(descriptor, "Md  ")))
                .color(PsdUtils.getColor(descriptor))
                .build();

        effects.addShadow(shadow);
    }

    /**
     * Decodes the text data of a text layer.
     */
    private static void decodeTextData(Image.Builder image, Layer.Builder layer, LayerProperty property) {
        TypeToolObject typeTool = (TypeToolObject) property.data;

        TextInfo.Builder info = new TextInfo.Builder();

        // The text data uses \r to indicate new lines
        // Replace them with \n instead
        info.text(PsdUtils.<String>get(typeTool.text, TypeToolObject.KEY_TEXT).replace('\r', '\n'));

        // Compute the text layer's transform
        AffineTransform transform = new AffineTransform(
                typeTool.xx, typeTool.yx,  // scaleX, shearY
                typeTool.xy, typeTool.yy,  // shearX, scaleY
                typeTool.tx, typeTool.ty); // translateX, translateY
        info.transform(transform);

        float resolutionScale = image.verticalResolution() / 72.0f;

        // Retrieves the text's bounding box. The bounding box is required
        // to properly apply alignment properties. The translation found
        // in the affine transform above gives us the origin for text
        // alignment and the bounding box gives us the actual position of
        // the text box from that origin
        DescriptorItem.UnitDouble left = PsdUtils.get(typeTool.text, "boundingBox.Left");
        DescriptorItem.UnitDouble top = PsdUtils.get(typeTool.text, "boundingBox.Top ");
        DescriptorItem.UnitDouble right = PsdUtils.get(typeTool.text, "boundingBox.Rght");
        DescriptorItem.UnitDouble bottom = PsdUtils.get(typeTool.text, "boundingBox.Btom");

        if (left != null && top != null && right != null && bottom != null) {
            info.bounds(
                    left.toPixel(resolutionScale), top.toPixel(resolutionScale),
                    right.toPixel(resolutionScale), bottom.toPixel(resolutionScale));
        }

        // Retrieves styles from the structured text data
        byte[] data = PsdUtils.get(typeTool.text, TypeToolObject.KEY_ENGINE_DATA);

        TextEngine parser = new TextEngine();
        TextEngine.MapProperty properties = parser.parse(data);

        // Find the list of fonts
        List<TextEngine.Property<?>> fontProperties = ((TextEngine.ListProperty)
                                                               properties.get("ResourceDict.FontSet")).getValue();
        List<String> fonts = new ArrayList<>(fontProperties.size());
        fonts.addAll(fontProperties.stream()
                .map(element -> ((TextEngine.MapProperty) element).get("Name").toString())
                .collect(Collectors.toList()));

        // By default, Photoshop creates unstyled runs that rely on the
        // default stylesheet. Look it up.
        int defaultSheetIndex = ((TextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalStyleSheet")).getValue();
        TextEngine.MapProperty defaultSheet = (TextEngine.MapProperty) properties.get(
                "ResourceDict.StyleSheetSet[" + defaultSheetIndex + "].StyleSheetData");

        // List of style runs
        int pos = 0;
        int[] runs = ((TextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunLengthArray")).toIntArray();
        List<TextEngine.Property<?>> styles = ((TextEngine.ListProperty) properties.get(
                "EngineDict.StyleRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            TextEngine.MapProperty style = (TextEngine.MapProperty) styles.get(i);
            TextEngine.MapProperty sheet = (TextEngine.MapProperty) style.get("StyleSheet.StyleSheetData");

            // Get the typeface, font size and color from each style run
            // If the run does not have a style, fall back to the default stylesheet
            int index = ((TextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Font")).getValue();
            float size = ((TextEngine.FloatProperty) getFromMap(sheet, defaultSheet, "FontSize")).getValue() / resolutionScale;
            float[] rgb = ((TextEngine.ListProperty)
                    getFromMap(sheet, defaultSheet, "FillColor.Values")).toFloatArray();
            int tracking = ((TextEngine.IntProperty) getFromMap(sheet, defaultSheet, "Tracking")).getValue();

            TextInfo.StyleRun run = new TextInfo.StyleRun.Builder(pos, pos += runs[i])
                    .font(fonts.get(index))
                    .fontSize(size)
                    .color(new Color(rgb[1], rgb[2], rgb[3], rgb[0]))
                    .tracking(tracking / 1000.0f)
                    .build();
            info.addStyleRun(run);
        }

        // Thankfully there's always a default paragraph stylesheet
        defaultSheetIndex = ((TextEngine.IntProperty) properties.get(
                "ResourceDict.TheNormalParagraphSheet")).getValue();
        defaultSheet = (TextEngine.MapProperty) properties.get(
                "ResourceDict.ParagraphSheetSet[" + defaultSheetIndex + "].Properties");

        // List of paragraph runs
        pos = 0;
        runs = ((TextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunLengthArray")).toIntArray();
        styles = ((TextEngine.ListProperty) properties.get(
                "EngineDict.ParagraphRun.RunArray")).getValue();

        for (int i = 0; i < runs.length; i++) {
            TextEngine.MapProperty style = (TextEngine.MapProperty) styles.get(i);
            TextEngine.MapProperty sheet = (TextEngine.MapProperty) style.get("ParagraphSheet.Properties");

            int justification = ((TextEngine.IntProperty)
                    getFromMap(sheet, defaultSheet, "Justification")).getValue();

            TextInfo.ParagraphRun run = new TextInfo.ParagraphRun.Builder(pos, pos += runs[i])
                    .alignment(TextInfo.ParagraphRun.Alignment.valueOf(alignments[justification]))
                    .build();
            info.addParagraphRun(run);
        }

        layer.textInfo(info.build());
    }

    /**
     * Attempts retrieve a value from "map", then from "defaultMap"
     * if the value is null.
     */
    private static TextEngine.Property<?> getFromMap(TextEngine.MapProperty map,
            TextEngine.MapProperty defaultMap, String name) {
        TextEngine.Property<?> property = map.get(name);
        if (property == null) {
            property = defaultMap.get(name);
        }
        return property;
    }

    /**
     * Decodes the path data for a given layer. The path data is encoded as a
     * series of path records that are fairly straightforward to interpret.
     */
    private static void decodePathData(Image.Builder image, Layer.Builder layer,
            Map<String, LayerProperty> properties) {

        // Older versions of Photoshop use the vector mask property. This
        // property is also used by newer versions of Photoshop if the shape
        // does not use specific features (stroke, etc.)
        LayerProperty property = properties.get(LayerProperty.KEY_VECTOR_MASK);
        if (property == null) {
            property = properties.get(LayerProperty.KEY_SHAPE_MASK);
        }

        ShapeMask mask = (ShapeMask) property.data;

        GeneralPath path = PsdUtils.createPath(mask);

        // Vector data is stored in relative coordinates in PSD files
        // For instance, a point at 0.5,0.5 is in the center of the document
        // We apply a transform to convert these relative coordinates into
        // absolute pixel coordinates
        Rectangle2D bounds = layer.bounds();

        AffineTransform transform = new AffineTransform();
        transform.translate(-bounds.getX(), -bounds.getY());
        transform.scale(image.width(), image.height());

        path.transform(transform);
        layer.path(path);

        int alpha = 255;
        LayerProperty fillOpacity = properties.get(LayerProperty.KEY_FILL_OPACITY);
        if (fillOpacity != null) {
            alpha = ((byte) fillOpacity.data) & 0xff;
        }

        // TODO: use Paint, instead of color, to handle gradients and patterns
        Color color;
        Descriptor descriptor = null;

        if (LayerProperty.KEY_SHAPE_MASK.equals(property.key)) {
            LayerProperty shapeGraphics = properties.get(LayerProperty.KEY_SHAPE_GRAPHICS);
            if (shapeGraphics != null) {
                ShapeGraphics graphics = (ShapeGraphics) shapeGraphics.data;
                if (LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR.equals(graphics.key)) {
                    descriptor = graphics.graphics;
                }
            }
        } else {
            LayerProperty solidColor = properties.get(LayerProperty.KEY_ADJUSTMENT_SOLID_COLOR);
            if (solidColor != null) {
                descriptor = ((SolidColorAdjustment) solidColor.data).solidColor;
            }
        }

        if (descriptor != null) {
            color = PsdUtils.getColor(descriptor, alpha / 255.0f);
        } else {
            color = new Color(0, 0, 0, alpha);
        }

        layer.pathColor(color);
    }

    /**
     * Decodes the image data of a specific layer. The image data is encoded
     * separately for each channel. Each channel could theoretically have its
     * own encoding so let's pretend this can happen.
     * There are 4 encoding formats: RAW, RLE, ZIP and ZIP without prediction.
     * Since we have yet to encounter the ZIP case, we only support RAW and RLE.
     */
    private static void decodeLayerImageData(Image.Builder image, Layer.Builder layer,
            RawLayer rawLayer, ChannelsContainer channelsList) {

        Rectangle2D bounds = layer.bounds();
        if (bounds.isEmpty()) return;

        int channels = rawLayer.channels;
        switch (image.colorMode()) {
            case BITMAP:
            case INDEXED:
                // Bitmap and indexed color modes do not support layers
                break;
            case GRAYSCALE:
                channels = Math.min(channels, 2);
                break;
            case RGB:
                channels = Math.min(channels, 4);
                break;
            case CMYK:
                channels = Math.min(channels, 5);
                break;
            case UNKNOWN:
            case NONE:
            case MULTI_CHANNEL:
                // Unsupported
                break;
            case DUOTONE:
                channels = Math.min(channels, 2);
                break;
            case LAB:
                channels = Math.min(channels, 4);
                break;
        }

        ColorSpace colorSpace = image.colorSpace();
        BufferedImage bitmap = Images.create((int) bounds.getWidth(), (int) bounds.getHeight(),
                image.colorMode(), channels, colorSpace, image.depth());

        for (int i = 0; i < rawLayer.channelsInfo.size(); i++) {
            ChannelInformation info = rawLayer.channelsInfo.get(i);
            if (info.id < -1) continue; // skip mask channel

            ChannelImageData imageData = channelsList.imageData.get(i);
            switch (imageData.compression) {
                case RAW:
                    // TODO: don't assume 8 bit depth
                    Images.decodeChannelRaw(imageData.data, 0, info.id, bitmap, 8);
                    break;
                case RLE:
                    int offset = (int) (bounds.getHeight() * 2);
                    Images.decodeChannelRLE(imageData.data, offset, info.id, bitmap);
                    break;
                case ZIP:
                case ZIP_NO_PREDICTION:
                    break;
            }
        }

        layer.image(bitmap);
    }

    private static void extractHeaderData(Image.Builder image, Header header) {
        image
            .dimensions(header.width, header.height)
            .colorMode(header.colorMode)
            .depth(header.depth);
    }

    /**
     * Image resource blocks contain a lot of information in PSD files but not
     * all of it is interesting to us. Here we only look for the few blocks that
     * we actually care about.
     */
    private static void resolveBlocks(Image.Builder image, ImageResources resources) {
        extractGuides(image, PsdUtils.get(resources, GuidesResourceBlock.ID));
        extractThumbnail(image, PsdUtils.get(resources, ThumbnailResourceBlock.ID));
        extractResolution(image, PsdUtils.get(resources, ResolutionInfoBlock.ID));
        extractColorProfile(image, PsdUtils.get(resources, ColorProfileBlock.ID));
    }

    /**
     * Extracts the ICC color profile embedded in the file, if any.
     */
    private static void extractColorProfile(Image.Builder image, ColorProfileBlock colorProfileBlock) {
        image.colorSpace(PsdUtils.createColorSpace(colorProfileBlock));
    }

    /**
     * Extracts the horizontal and vertical dpi information from the PSD
     * file. This information is important to properly handle the point
     * unit used in text layers.
     */
    private static void extractResolution(Image.Builder image, ResolutionInfoBlock resolutionBlock) {
        if (resolutionBlock == null) return;

        float hRes = Bytes.fixed16_16ToFloat(resolutionBlock.horizontalResolution);
        ResolutionUnit unit = resolutionBlock.horizontalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            hRes *= CENTIMETER_TO_INCH;
        }

        float vRes = Bytes.fixed16_16ToFloat(resolutionBlock.verticalResolution);
        unit = resolutionBlock.verticalUnit;
        if (unit == ResolutionUnit.UNKNOWN) unit = ResolutionUnit.PIXEL_PER_INCH;
        if (unit == ResolutionUnit.PIXEL_PER_CM) {
            vRes *= CENTIMETER_TO_INCH;
        }

        image.resolution(hRes, vRes);
    }

    private static void extractThumbnail(Image.Builder image, ThumbnailResourceBlock thumbnailBlock) {
        if (thumbnailBlock == null) return;

        try {
            image.thumbnail(ImageIO.read(new ByteArrayInputStream(thumbnailBlock.thumbnail)));
        } catch (IOException e) {
            // Ignore
        }
    }

    private static void extractGuides(Image.Builder image, GuidesResourceBlock guidesBlock) {
        if (guidesBlock == null) return;

        // Guides are stored in a 27.5 fixed-point format, kind of weird
        for (GuideBlock block : guidesBlock.guides) {
            Guide guide = new Guide.Builder()
                    .orientation(Guide.Orientation.values()[block.orientation.ordinal()])
                    .position(block.location / 32.0f)
                    .build();
            image.addGuide(guide);
        }
    }

    /**
     * Decodes the flattened image data. Just like layers, the data is stored as
     * separate planes for each channel. The difference is that the encoding is
     * the same for all channels. The ZIP formats are not supported.
     */
    private static void decodeImageData(Image.Builder image, PsdFile psd) {
        int channels = psd.header.channels;
        int alphaChannel = 0;
        // When the layer count is negative, the first alpha channel is the
        // merged result's alpha mask
        LayersList layers = psd.layersInfo.layers;
        if (layers != null && layers.count < 0) {
            alphaChannel = 1;
        }

        switch (image.colorMode()) {
            case BITMAP:
            case INDEXED:
                decodeIndexedImageData(image, psd);
                return;
            case GRAYSCALE:
                channels = Math.min(channels, 1 + alphaChannel);
                break;
            case RGB:
                channels = Math.min(channels, 3 + alphaChannel);
                break;
            case CMYK:
                channels = Math.min(channels, 4 + alphaChannel);
                break;
            case UNKNOWN:
            case NONE:
            case MULTI_CHANNEL:
                // Unsupported
                break;
            case DUOTONE:
                channels = Math.min(channels, 1 + alphaChannel);
                break;
            case LAB:
                channels = Math.min(channels, 3 + alphaChannel);
                break;
        }

        ColorSpace colorSpace = image.colorSpace();

        BufferedImage bitmap = null;
        switch (psd.imageData.compression) {
            case RAW:
                bitmap = Images.decodeRaw(psd.imageData.data, 0, image.width(), image.height(),
                        image.colorMode(), channels, colorSpace, psd.header.depth);
                break;
            case RLE:
                int offset = image.height() * psd.header.channels * 2;
                bitmap = Images.decodeRLE(psd.imageData.data, offset, image.width(), image.height(),
                        image.colorMode(), channels, colorSpace, psd.header.depth);
                break;
            case ZIP:
            case ZIP_NO_PREDICTION:
                break;
        }

        image.mergedImage(fixBitmap(image, bitmap));
    }

    private static void decodeIndexedImageData(Image.Builder image, PsdFile psd) {
        ColorSpace colorSpace = image.colorSpace();

        UnsignedShortBlock block;
        block = PsdUtils.get(psd.resources, UnsignedShortBlock.ID_INDEX_TABLE_COUNT);
        int size = block == null ? 256 : block.data;

        block = PsdUtils.get(psd.resources, UnsignedShortBlock.ID_INDEX_TRANSPARENCY);
        int transparency = block == null ? -1 : block.data;

        BufferedImage bitmap = null;
        switch (psd.imageData.compression) {
            case RAW:
                bitmap = Images.decodeIndexedRaw(psd.imageData.data, 0, image.width(), image.height(),
                        image.colorMode(), colorSpace, size, psd.colorData.data, transparency);
                break;
            case RLE:
                int offset = image.height() * psd.header.channels * 2;
                bitmap = Images.decodeIndexedRLE(psd.imageData.data, offset, image.width(), image.height(),
                        image.colorMode(), colorSpace, size, psd.colorData.data, transparency);
                break;
            case ZIP:
            case ZIP_NO_PREDICTION:
                break;
        }

        image.mergedImage(fixBitmap(image, bitmap));
    }

    private static BufferedImage fixBitmap(Image.Builder image, BufferedImage bitmap) {
        // Fun fact: CMYK colors are stored reversed...
        // Cyan 100% is stored as 0x0 and Cyan 0% is stored as 0xff
        if (image.colorMode() == ColorMode.CMYK) {
            bitmap = Images.invert(bitmap);
        }
        return bitmap;
    }
}
