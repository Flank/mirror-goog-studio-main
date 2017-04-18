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

package com.android.assetstudiolib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** A {@link GraphicGenerator} that generates Android adaptive icons. */
public class AdaptiveIconGenerator extends GraphicGenerator {

    private static final Rectangle IMAGE_SIZE_WEB = new Rectangle(0, 0, 512, 512);
    private static final Rectangle IMAGE_SIZE_FULL_BLEED = new Rectangle(0, 0, 108, 108);
    private static final Rectangle IMAGE_SIZE_VIEWPORT = new Rectangle(0, 0, 72, 72);
    private static final Rectangle IMAGE_SIZE_LEGACY = new Rectangle(0, 0, 48, 48);
    private static final Rectangle IMAGE_SIZE_SAFE_ZONE = new Rectangle(0, 0, 66, 66);
    // The offset of the target rect in side the safe zone rect
    private static final int SAFE_ZONE_TARGET_RECT_OFFSET = 2;

    // The target zone inside the safe zone rectangle inside the full bleed layer
    private static final Rectangle IMAGE_SIZE_FULL_BLEED_TARGET_RECT =
            new Rectangle(
                    SAFE_ZONE_TARGET_RECT_OFFSET
                            + (IMAGE_SIZE_FULL_BLEED.width - IMAGE_SIZE_SAFE_ZONE.width) / 2,
                    SAFE_ZONE_TARGET_RECT_OFFSET
                            + (IMAGE_SIZE_FULL_BLEED.height - IMAGE_SIZE_SAFE_ZONE.height) / 2,
                    IMAGE_SIZE_SAFE_ZONE.width - 2 * SAFE_ZONE_TARGET_RECT_OFFSET,
                    IMAGE_SIZE_SAFE_ZONE.height - 2 * SAFE_ZONE_TARGET_RECT_OFFSET);

    /**
     * Scaling images with {@link AssetUtil#scaledImage(BufferedImage, int, int)} is time consuming
     * (a few milliseconds per operation on a fast Desktop CPU). Since we end up scaling the same
     * images (foreground and background layers) many times during a call to {@link
     * #generateIcons(GraphicGeneratorContext, Options, String)}, this cache is used to decrease the
     * total number of calls to {@link AssetUtil#scaledImage(BufferedImage, int, int)}
     */
    private static class ImageCache {
        @NonNull private final Object lock = new Object();
        @NonNull private final GraphicGeneratorContext context;

        @GuardedBy("lock")
        @NonNull
        private final Map<Key, BufferedImage> map = new HashMap<>();

        public ImageCache(@NonNull GraphicGeneratorContext context) {
            this.context = context;
        }

        @NonNull
        public GraphicGeneratorContext getContext() {
            return context;
        }

        private static class Key {
            @NonNull private final BufferedImage image;
            @NonNull private final Dimension imageRectSize;
            @NonNull private final Dimension targetSize;
            private final boolean crop;
            private final boolean useFillColor;
            private final int fillColor;

            public Key(
                    @NonNull BufferedImage image,
                    @NonNull Dimension imageRectSize,
                    @NonNull Dimension targetSize,
                    boolean crop,
                    boolean useFillColor,
                    int fillColor) {

                this.image = image;
                this.imageRectSize = imageRectSize;
                this.targetSize = targetSize;
                this.crop = crop;
                this.useFillColor = useFillColor;
                this.fillColor = fillColor;
            }

            @Override
            public int hashCode() {
                return Objects.hash(
                        this.image,
                        this.imageRectSize,
                        this.targetSize,
                        this.crop,
                        this.useFillColor,
                        this.fillColor);
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof Key)) {
                    return false;
                }
                Key other = (Key) obj;
                return Objects.equals(this.image, other.image)
                        && Objects.equals(this.imageRectSize, other.imageRectSize)
                        && Objects.equals(this.targetSize, other.targetSize)
                        && this.crop == other.crop
                        && this.useFillColor == other.useFillColor
                        && this.fillColor == other.fillColor;
            }
        }

        @NonNull
        public BufferedImage getOrCreate(
                @NonNull BufferedImage image,
                @NonNull Dimension imageRectSize,
                @NonNull Dimension targetSize,
                boolean crop,
                boolean useFillColor,
                int fillColor,
                Callable<BufferedImage> generator) {
            Key key = new Key(image, imageRectSize, targetSize, crop, useFillColor, fillColor);

            // Initial lookup attempt
            synchronized (lock) {
                BufferedImage value = map.get(key);
                if (value != null) {
                    return value;
                }
            }

            // Value not present, create it
            BufferedImage value;
            try {
                value = generator.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            assert value != null;

            // Store new value
            synchronized (lock) {
                if (!map.containsKey(key)) {
                    map.put(key, value);
                }
            }
            return value;
        }
    }

    @Override
    @NonNull
    public GeneratedIcons generateIcons(
            @NonNull GraphicGeneratorContext context,
            @NonNull Options options,
            @NonNull String name) {
        AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;
        ImageCache cache = new ImageCache(context);

        List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

        // Generate tasks for icons (background, foreground, legacy) in all densities
        createOutputIconsTasks(cache, name, adaptiveIconOptions, tasks);

        // Generate tasks for drawable xml resource
        createXmlDrawableResourcesTasks(name, adaptiveIconOptions, tasks);

        // Generate tasks for preview images
        createPreviewImagesTasks(cache, adaptiveIconOptions, tasks);

        // Execute tasks in parallel and wait for results
        WaitableExecutor<GeneratedIcon> executor = WaitableExecutor.useGlobalSharedThreadPool();
        tasks.forEach(executor::execute);

        List<GeneratedIcon> results;
        try {
            results = executor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Add task results to the returned list
        GeneratedIcons icons = new GeneratedIcons();
        results.forEach(icons::add);
        return icons;
    }

    private void createOutputIconsTasks(
            @NonNull ImageCache imageCache,
            @NonNull String name,
            @NonNull AdaptiveIconOptions options,
            @NonNull List<Callable<GeneratedIcon>> tasks) {
        for (Density density :
                new Density[] {
                    Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH
                }) {
            AdaptiveIconOptions localOptions = cloneOptions(options);
            localOptions.density = density;
            localOptions.isWebGraphic = false;
            localOptions.showGrid = false;
            localOptions.showSafeZone = false;

            createOutputIconsTasks(imageCache, name, localOptions, density, tasks);
        }
    }

    private void createOutputIconsTasks(
            @NonNull ImageCache imageCache,
            @NonNull String name,
            @NonNull AdaptiveIconOptions options,
            @NonNull Density density,
            @NonNull List<Callable<GeneratedIcon>> tasks) {
        tasks.add(
                () -> {
                    BufferedImage foregroundImage =
                            generateAdaptiveIconForegroundLayer(imageCache, options);
                    return new GeneratedImageIcon(
                            options.foregroundLayerName,
                            Paths.get(getIconPath(options, options.foregroundLayerName)),
                            IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                            density,
                            foregroundImage);
                });

        // Generate background mipmap only if we got a background image
        if (backgroundIsImage(options)) {
            tasks.add(
                    () -> {
                        BufferedImage backgroundImage =
                                generateAdaptiveIconBackgroundLayer(imageCache, options);
                        return new GeneratedImageIcon(
                                options.backgroundLayerName,
                                Paths.get(getIconPath(options, options.backgroundLayerName)),
                                IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                density,
                                backgroundImage);
                    });
        }

        tasks.add(
                () -> {
                    AdaptiveIconOptions legacyOptions = cloneOptions(options);
                    legacyOptions.previewShape = PreviewShape.LEGACY;
                    BufferedImage legacy = generateLegacyPreviewImage(imageCache, legacyOptions);
                    return new GeneratedImageIcon(
                            name,
                            Paths.get(getIconPath(legacyOptions, name)),
                            IconCategory.LEGACY,
                            density,
                            legacy);
                });

        tasks.add(
                () -> {
                    AdaptiveIconOptions legacyOptions = cloneOptions(options);
                    legacyOptions.previewShape = PreviewShape.LEGACY_ROUND;
                    BufferedImage legacyRound =
                            generateLegacyPreviewImage(imageCache, legacyOptions);
                    return new GeneratedImageIcon(
                            name + "_round",
                            Paths.get(getIconPath(legacyOptions, name + "_round")),
                            IconCategory.ROUND_API_25,
                            density,
                            legacyRound);
                });
    }

    private void createXmlDrawableResourcesTasks(
            @NonNull String name,
            @NonNull AdaptiveIconOptions options,
            @NonNull List<Callable<GeneratedIcon>> tasks) {
        AdaptiveIconOptions xmlOptions = cloneOptions(options);
        xmlOptions.density = Density.ANYDPI;
        xmlOptions.isWebGraphic = false;

        tasks.add(
                () -> {
                    String xmlAdaptiveIcon = getAdaptiveIconXml(xmlOptions);
                    return new GeneratedXmlResource(
                            name,
                            Paths.get(getIconPath(xmlOptions, name)),
                            IconCategory.XML_RESOURCE,
                            xmlAdaptiveIcon);
                });

        tasks.add(
                () -> {
                    String xmlAdaptiveIcon = getAdaptiveIconXml(xmlOptions);
                    return new GeneratedXmlResource(
                            name + "_round",
                            Paths.get(getIconPath(xmlOptions, name + "_round")),
                            IconCategory.XML_RESOURCE,
                            xmlAdaptiveIcon);
                });

        // Generate color value
        if (xmlOptions.backgroundImage == null) {
            tasks.add(
                    () -> {
                        AdaptiveIconGenerator.AdaptiveIconOptions iconPathOptions =
                                cloneOptions(xmlOptions);
                        iconPathOptions.isWebGraphic = false;
                        iconPathOptions.density = Density.ANYDPI;
                        iconPathOptions.iconFolderKind = IconFolderKind.VALUES;

                        String xmlColor =
                                String.format(
                                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<resources>\n"
                                                + "  <color name=\"%s\">#%06X</color>\n"
                                                + "</resources>",
                                        xmlOptions.backgroundLayerName,
                                        (xmlOptions.backgroundColor & 0xFF_FF_FF));
                        return new GeneratedXmlResource(
                                name,
                                Paths.get(
                                        getIconPath(
                                                iconPathOptions, xmlOptions.backgroundLayerName)),
                                IconCategory.XML_RESOURCE,
                                xmlColor);
                    });
        }
    }

    @NonNull
    private static String getAdaptiveIconXml(@NonNull AdaptiveIconOptions options) {
        String xmlAdaptiveIcon;
        if (backgroundIsImage(options)) {
            String xmlFormat =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <background android:drawable=\"@mipmap/%s\"/>\n"
                            + "    <foreground android:drawable=\"@mipmap/%s\"/>\n"
                            + "</adaptive-icon>";
            xmlAdaptiveIcon =
                    String.format(
                            xmlFormat, options.backgroundLayerName, options.foregroundLayerName);
        } else {
            String xmlFormat =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                            + "    <background android:drawable=\"@color/%s\"/>\n"
                            + "    <foreground android:drawable=\"@mipmap/%s\"/>\n"
                            + "</adaptive-icon>";
            xmlAdaptiveIcon =
                    String.format(
                            xmlFormat, options.backgroundLayerName, options.foregroundLayerName);
        }
        return xmlAdaptiveIcon;
    }

    private static boolean backgroundIsImage(@NonNull AdaptiveIconOptions adaptiveIconOptions) {
        return adaptiveIconOptions.backgroundImage != null;
    }

    private static void createPreviewImagesTasks(
            @NonNull ImageCache imageCache,
            @NonNull AdaptiveIconOptions adaptiveIconOptions,
            @NonNull List<Callable<GeneratedIcon>> tasks) {
        for (PreviewShape shape :
                new PreviewShape[] {
                    PreviewShape.FULL_BLEED,
                    PreviewShape.SQUIRCLE,
                    PreviewShape.CIRCLE,
                    PreviewShape.SQUARE,
                    PreviewShape.ROUNDED_SQUARE,
                    PreviewShape.LEGACY_ROUND,
                    PreviewShape.LEGACY
                }) {
            tasks.add(
                    () -> {
                        AdaptiveIconOptions localOptions = cloneOptions(adaptiveIconOptions);
                        localOptions.density = adaptiveIconOptions.previewDensity;
                        localOptions.previewShape = shape;
                        localOptions.isWebGraphic = false;

                        BufferedImage image = generatePreviewImage(imageCache, localOptions);
                        return new GeneratedImageIcon(
                                shape.id,
                                null, // no path
                                IconCategory.PREVIEW,
                                localOptions.density,
                                image);
                    });
        }
    }

    @Override
    public void generate(
            String category,
            Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context,
            Options options,
            String name) {
        AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;
        AdaptiveIconOptions localOptions = cloneOptions(adaptiveIconOptions);
        localOptions.isWebGraphic = false;

        GeneratedIcons icons = generateIcons(context, options, name);
        icons.getList()
                .stream()
                .filter(x -> x instanceof GeneratedImageIcon)
                .map(x -> (GeneratedImageIcon) x)
                .filter(x -> x.getOutputPath() != null)
                .forEach(
                        x -> {
                            assert x.getOutputPath() != null;

                            Map<String, BufferedImage> imageMap =
                                    categoryMap.computeIfAbsent(
                                            x.getCategory().toString(), k -> new LinkedHashMap<>());

                            // Store image in map, where the key is the relative path to the image
                            AdaptiveIconOptions iconOptions = cloneOptions(localOptions);
                            iconOptions.density = x.getDensity();
                            iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
                            iconOptions.isWebGraphic = (x.getCategory() == IconCategory.WEB);
                            imageMap.put(x.getOutputPath().toString(), x.getImage());
                        });
    }

    @NonNull
    @Override
    public BufferedImage generate(
            @NonNull GraphicGeneratorContext context, @NonNull Options options) {
        AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;
        ImageCache imageCache = new ImageCache(context);

        if (adaptiveIconOptions.isWebGraphic) {
            return generateWebGraphicPreviewImage(imageCache, adaptiveIconOptions);
        } else if (adaptiveIconOptions.previewShape == PreviewShape.FULL_BLEED) {
            return generateFullBleedPreviewImage(imageCache, adaptiveIconOptions);
        } else if (adaptiveIconOptions.previewShape == PreviewShape.LEGACY
                || adaptiveIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
            return generateLegacyPreviewImage(imageCache, adaptiveIconOptions);
        } else {
            return generateViewportPreviewImage(imageCache, adaptiveIconOptions);
        }
    }

    @NonNull
    public static BufferedImage generatePreviewImage(
            @NonNull ImageCache imageCache, @NonNull Options options) {
        AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;

        if (adaptiveIconOptions.isWebGraphic) {
            return generateWebGraphicPreviewImage(imageCache, adaptiveIconOptions);
        } else if (adaptiveIconOptions.previewShape == PreviewShape.FULL_BLEED) {
            return generateFullBleedPreviewImage(imageCache, adaptiveIconOptions);
        } else if (adaptiveIconOptions.previewShape == PreviewShape.LEGACY
                || adaptiveIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
            return generateLegacyPreviewImage(imageCache, adaptiveIconOptions);
        } else {
            return generateViewportPreviewImage(imageCache, adaptiveIconOptions);
        }
    }

    @NonNull
    private static AdaptiveIconOptions cloneOptions(@NonNull AdaptiveIconOptions options) {
        AdaptiveIconOptions localOptions = new AdaptiveIconOptions();

        localOptions.minSdk = options.minSdk;
        localOptions.sourceImage = options.sourceImage;
        localOptions.backgroundImage = options.backgroundImage;
        localOptions.density = options.density;
        localOptions.previewDensity = options.previewDensity;
        localOptions.iconFolderKind = options.iconFolderKind;

        localOptions.useForegroundColor = options.useForegroundColor;
        localOptions.foregroundColor = options.foregroundColor;
        localOptions.backgroundColor = options.backgroundColor;
        localOptions.cropForeground = options.cropForeground;
        localOptions.cropBackground = options.cropBackground;
        localOptions.previewShape = options.previewShape;
        localOptions.legacyShape = options.legacyShape;
        localOptions.showGrid = options.showGrid;
        localOptions.showSafeZone = options.showSafeZone;
        localOptions.isWebGraphic = options.isWebGraphic;
        localOptions.foregroundLayerName = options.foregroundLayerName;
        localOptions.backgroundLayerName = options.backgroundLayerName;

        return localOptions;
    }

    @NonNull
    public static BufferedImage generateWebGraphicPreviewImage(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
        // Scale the image to 521x512 (for now)
        Density tempDensity = options.density;
        options.density = Density.XXXHIGH;
        BufferedImage largeImage = generateViewportPreviewImage(imageCache, options);
        options.density = tempDensity;
        return AssetUtil.scaledImage(largeImage, IMAGE_SIZE_WEB.width, IMAGE_SIZE_WEB.height);
    }

    @SuppressWarnings("UseJBColor")
    @NonNull
    private static BufferedImage generateFullBleedPreviewImage(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
        Layers layers = generateAdaptiveIconPreviewLayers(imageCache, options);
        BufferedImage result = mergeLayers(layers, Color.BLACK);
        drawGrid(options, result);
        return result;
    }

    @NonNull
    private static BufferedImage generateLegacyPreviewImage(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
        if (options.backgroundImage == null) {
            // If we don't have a background image, generate a regular the legacy icon
            BufferedImage legacyIcon = generateLegacyIconLayer(imageCache, options);
            drawGrid(options, legacyIcon);
            return legacyIcon;
        } else {
            // If we have a background image, it is more complicated:
            // 1. Generate the foreground image as the legacy, but without background shape
            // 2. Generate the background image as the adaptive icon, then scale it to
            //    the legacy size
            // 3. Merge both images
            // 4. Apply the mask corresponding to the legacy shape
            // 5. Draw the grid
            BufferedImage legacyForegroundIcon =
                    generateLegacyIconLayer(
                            imageCache,
                            options,
                            options.sourceImage,
                            options.useForegroundColor,
                            options.foregroundColor,
                            false);
            BufferedImage adaptiveBackgroundLayer =
                    generateAdaptiveIconBackgroundLayer(imageCache, options);
            BufferedImage legacyBackgroundLayer =
                    cropImageToViewport(options, adaptiveBackgroundLayer);

            // Scale image to the legacy icon size
            Rectangle imageRect =
                    AssetUtil.scaleRectangle(
                            IMAGE_SIZE_LEGACY,
                            GraphicGenerator.getMdpiScaleFactor(options.density));
            BufferedImage legacyBackgroundIcon =
                    AssetUtil.scaledImage(legacyBackgroundLayer, imageRect.width, imageRect.height);

            BufferedImage legacyIcon =
                    mergeLayers(new Layers(legacyBackgroundIcon, legacyForegroundIcon));

            // Apply mask corresponding to the legacy shape
            Shape legacyShape =
                    options.previewShape == PreviewShape.LEGACY_ROUND
                            ? Shape.CIRCLE
                            : options.legacyShape;

            if (legacyShape != Shape.NONE) {
                BufferedImage mask =
                        LauncherIconGenerator.loadMaskImage(
                                imageCache.getContext(),
                                legacyShape,
                                options.density.getResourceValue());
                legacyIcon = applyMask(legacyIcon, mask);
            }

            // Draw grid on top of everything if needed
            drawGrid(options, legacyIcon);

            return legacyIcon;
        }
    }

    @NonNull
    private static BufferedImage generateViewportPreviewImage(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
        Layers layers = generateAdaptiveIconPreviewLayers(imageCache, options);
        BufferedImage result = mergeLayers(layers);
        BufferedImage mask = generateMaskLayer(imageCache, options, options.previewShape);
        result = cropImageToViewport(options, result);
        result = applyMask(result, mask);
        drawGrid(options, result);

        return result;
    }

    private static BufferedImage cropImageToViewport(
            @NonNull AdaptiveIconOptions options, @NonNull BufferedImage image) {

        Rectangle viewportRect =
                AssetUtil.scaleRectangle(
                        IMAGE_SIZE_VIEWPORT, GraphicGenerator.getMdpiScaleFactor(options.density));

        return cropImage(image, viewportRect);
    }

    private static BufferedImage cropImage(
            @NonNull BufferedImage image, @NonNull Rectangle targetRect) {

        Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());

        BufferedImage subImage =
                image.getSubimage(
                        (imageRect.width - targetRect.width) / 2,
                        (imageRect.height - targetRect.height) / 2,
                        targetRect.width,
                        targetRect.height);

        BufferedImage viewportImage =
                AssetUtil.newArgbBufferedImage(targetRect.width, targetRect.height);

        Graphics2D gViewport = (Graphics2D) viewportImage.getGraphics();
        gViewport.drawImage(subImage, 0, 0, null);
        gViewport.dispose();

        return viewportImage;
    }

    @NonNull
    private static BufferedImage mergeLayers(@NonNull Layers layers) {
        return mergeLayers(layers, null);
    }

    @NonNull
    private static BufferedImage mergeLayers(@NonNull Layers layers, @Nullable Color fillColor) {

        int width = Math.max(layers.background.getWidth(), layers.foreground.getWidth());
        int height = Math.max(layers.background.getHeight(), layers.foreground.getHeight());

        BufferedImage outImage = AssetUtil.newArgbBufferedImage(width, height);
        Graphics2D gOut = (Graphics2D) outImage.getGraphics();
        if (fillColor != null) {
            gOut.setPaint(fillColor);
            gOut.fillRect(0, 0, width, height);
        }
        gOut.drawImage(layers.background, 0, 0, null);
        gOut.drawImage(layers.foreground, 0, 0, null);
        gOut.dispose();

        return outImage;
    }

    private static class Layers {
        public BufferedImage background;
        public BufferedImage foreground;

        public Layers(@NonNull BufferedImage background, @NonNull BufferedImage foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    @NonNull
    private static Layers generateAdaptiveIconPreviewLayers(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {

        BufferedImage backgroundImage = generateAdaptiveIconBackgroundLayer(imageCache, options);
        BufferedImage foregroundImage = generateAdaptiveIconForegroundLayer(imageCache, options);

        return new Layers(backgroundImage, foregroundImage);
    }

    @NonNull
    private static BufferedImage generateLegacyIconLayer(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
        return generateLegacyIconLayer(
                imageCache,
                options,
                options.sourceImage,
                options.useForegroundColor,
                options.foregroundColor,
                true);
    }

    @NonNull
    private static BufferedImage generateLegacyIconLayer(
            @NonNull ImageCache imageCache,
            @NonNull AdaptiveIconOptions options,
            @NonNull BufferedImage sourceImage,
            boolean useForegroundColor,
            int foregroundColor,
            boolean renderShape) {

        Shape legacyShape =
                options.previewShape == PreviewShape.LEGACY_ROUND
                        ? Shape.CIRCLE
                        : options.legacyShape;

        LauncherIconGenerator generator = new LauncherIconGenerator();
        LauncherIconGenerator.LauncherOptions localOptions =
                new LauncherIconGenerator.LauncherOptions();
        localOptions.minSdk = options.minSdk;
        localOptions.sourceImage = sourceImage;
        localOptions.density = options.density;
        localOptions.iconFolderKind = options.iconFolderKind;

        localOptions.useForegroundColor = useForegroundColor;
        localOptions.foregroundColor = foregroundColor;
        localOptions.renderShape = renderShape;
        localOptions.backgroundColor = options.backgroundColor;
        localOptions.crop = options.cropForeground;
        localOptions.shape = legacyShape;
        localOptions.isWebGraphic = false;

        return generator.generate(imageCache.getContext(), localOptions);
    }

    @Nullable
    private static BufferedImage generateMaskLayer(
            @NonNull ImageCache imageCache,
            @NonNull AdaptiveIconOptions options,
            @NonNull PreviewShape shape) {
        String maskName;
        switch (shape) {
            case CIRCLE:
                maskName = "circle";
                break;
            case SQUARE:
                maskName = "square";
                break;
            case ROUNDED_SQUARE:
                maskName = "rounded_corner";
                break;
            case SQUIRCLE:
                //noinspection SpellCheckingInspection
                maskName = "squircle";
                break;
            default:
                maskName = null;
        }
        if (maskName == null) {
            return null;
        }

        String resourceName =
                String.format(
                        "/images/adaptive_icons_masks/adaptive_%s-%s.png",
                        maskName, options.density.getResourceValue());

        return imageCache.getContext().loadImageResource(resourceName);
    }

    @NonNull
    private static BufferedImage generateAdaptiveIconBackgroundLayer(
            @NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {

        Rectangle imageRect =
                AssetUtil.scaleRectangle(
                        IMAGE_SIZE_FULL_BLEED,
                        GraphicGenerator.getMdpiScaleFactor(options.density));

        if (backgroundIsImage(options)) {
            return generateAdaptiveIconLayer(
                    imageCache,
                    options.backgroundImage,
                    imageRect,
                    imageRect,
                    options.cropBackground,
                    false,
                    0);
        } else {
            return generateAdaptiveIconBackgroundLayerFlatColor(options, imageRect);
        }
    }

    @NonNull
    private static BufferedImage generateAdaptiveIconForegroundLayer(
            ImageCache imageCache, @NonNull AdaptiveIconOptions options) {

        Rectangle imageRect =
                AssetUtil.scaleRectangle(
                        IMAGE_SIZE_FULL_BLEED,
                        GraphicGenerator.getMdpiScaleFactor(options.density));

        Rectangle targetRect =
                AssetUtil.scaleRectangle(
                        IMAGE_SIZE_FULL_BLEED_TARGET_RECT,
                        GraphicGenerator.getMdpiScaleFactor(options.density));

        return generateAdaptiveIconLayer(
                imageCache,
                options.sourceImage,
                imageRect,
                targetRect,
                options.cropForeground,
                options.useForegroundColor,
                options.foregroundColor);
    }

    @NonNull
    private static BufferedImage generateAdaptiveIconBackgroundLayerFlatColor(
            @NonNull AdaptiveIconOptions options, @NonNull Rectangle imageRect) {
        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
        //noinspection UseJBColor
        gTemp.setPaint(new Color(options.backgroundColor));
        gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
        gTemp.dispose();
        return tempImage;
    }

    @NonNull
    private static BufferedImage applyMask(
            @NonNull BufferedImage image, @Nullable BufferedImage mask) {
        if (mask == null) {
            return image;
        }

        Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);

        Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
        AssetUtil.drawCenterInside(gTemp, mask, imageRect);
        gTemp.setComposite(AlphaComposite.SrcIn);
        AssetUtil.drawCenterInside(gTemp, image, imageRect);
        gTemp.dispose();

        return tempImage;
    }

    @NonNull
    private static BufferedImage generateAdaptiveIconLayer(
            @NonNull ImageCache imageCache,
            @NonNull BufferedImage sourceImage,
            @NonNull Rectangle imageRect,
            @NonNull Rectangle targetRect,
            boolean crop,
            boolean useFillColor,
            int fillColor) {

        return imageCache.getOrCreate(
                sourceImage,
                imageRect.getSize(),
                targetRect.getSize(),
                crop,
                useFillColor,
                fillColor,
                () -> {
                    // Draw the source image with effect
                    BufferedImage iconImage =
                            AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
                    Graphics2D gIcon = (Graphics2D) iconImage.getGraphics();
                    if (crop) {
                        AssetUtil.drawCenterScaled(
                                gIcon, sourceImage, imageRect, targetRect.width, targetRect.height);
                    } else {
                        AssetUtil.drawCenterInside(gIcon, sourceImage, targetRect);
                    }
                    AssetUtil.Effect[] effects;
                    if (useFillColor) {
                        //noinspection UseJBColor
                        effects =
                                new AssetUtil.Effect[] {
                                    new AssetUtil.FillEffect(new Color(fillColor), 1.0)
                                };
                    } else {
                        effects = new AssetUtil.Effect[0];
                    }

                    BufferedImage effectImage =
                            AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
                    Graphics2D gEffect = (Graphics2D) effectImage.getGraphics();
                    AssetUtil.drawEffects(gEffect, iconImage, 0, 0, effects);
                    gIcon.dispose();
                    gEffect.dispose();

                    return effectImage;
                });
    }

    private static void drawGrid(
            @NonNull AdaptiveIconOptions adaptiveIconOptions, @NonNull BufferedImage image) {
        Graphics2D gOut = (Graphics2D) image.getGraphics();
        drawGrid(adaptiveIconOptions, gOut);
        gOut.dispose();
    }

    private static void drawGrid(
            @NonNull AdaptiveIconOptions adaptiveIconOptions, @NonNull Graphics2D gOut) {
        if (adaptiveIconOptions.isWebGraphic) {
            return;
        }

        if (adaptiveIconOptions.previewShape == PreviewShape.FULL_BLEED) {
            if (adaptiveIconOptions.showGrid || adaptiveIconOptions.showSafeZone) {
                drawFullBleedIconGrid(adaptiveIconOptions, gOut);
            }
            return;
        }

        if (adaptiveIconOptions.previewShape == PreviewShape.LEGACY
                || adaptiveIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
            if (adaptiveIconOptions.showGrid) {
                drawLegacyIconGrid(adaptiveIconOptions, gOut);
            }
            return;
        }

        if (adaptiveIconOptions.showGrid || adaptiveIconOptions.showSafeZone) {
            drawAdaptiveIconGrid(adaptiveIconOptions, gOut);
        }
    }

    private static void drawAdaptiveIconGrid(
            @NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

        // 72x72
        int size = IMAGE_SIZE_VIEWPORT.width;
        int center = size / 2;

        //noinspection UseJBColor
        Color c = new Color(0f, 0f, 0f, 0.20f);
        out.setColor(c);
        out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
        if (options.showGrid) {
            g.drawRect(0, 0, size, size);

            // "+" and "x" cross
            g.drawLine(0, 0, size, size);
            g.drawLine(size, 0, 0, size);
            g.drawLine(0, center, size, center);
            g.drawLine(center, 0, center, size);

            // 3 keyline rectangles (36x52, 44x44, 52x36)
            int arcSize = 4;
            int rect1 = 36;
            int rect2 = 44;
            int rect3 = 52;
            g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
            g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
            g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

            // 2 keyline circles: 36dp and 52dp
            g.drawCenteredCircle(center, center, 18);
            g.drawCenteredCircle(center, center, 26);
        }

        if (options.showSafeZone) {
            // Safe zone: 66dp
            g.drawCenteredCircle(center, center, 33);
        }
    }

    private static void drawFullBleedIconGrid(
            @NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

        // 108x108
        int size = IMAGE_SIZE_FULL_BLEED.width;
        int center = size / 2;

        //noinspection UseJBColor
        Color c = new Color(0f, 0f, 0f, 0.20f);
        out.setColor(c);
        out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
        if (options.showGrid) {
            g.drawRect(0, 0, size, size);

            // Viewport
            g.drawRect(18, 18, IMAGE_SIZE_VIEWPORT.width, IMAGE_SIZE_VIEWPORT.height);

            // "+" and "x" cross
            g.drawLine(0, 0, size, size);
            g.drawLine(size, 0, 0, size);
            g.drawLine(0, center, size, center);
            g.drawLine(center, 0, center, size);
        }

        if (options.showSafeZone) {
            // Safe zone: 66dp
            g.drawCenteredCircle(center, center, IMAGE_SIZE_SAFE_ZONE.width / 2);
        }
    }

    private static void drawLegacyIconGrid(
            @NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

        // 48x48
        int size = IMAGE_SIZE_LEGACY.width;
        int center = size / 2;

        //noinspection UseJBColor
        Color c = new Color(0f, 0f, 0f, 0.20f);
        out.setColor(c);
        out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
        g.drawRect(0, 0, size, size);

        // "+" and "x" cross
        g.drawLine(0, 0, size, size);
        g.drawLine(size, 0, 0, size);
        g.drawLine(0, center, size, center);
        g.drawLine(center, 0, center, size);

        // 2 keyline rectangles (32x44, 38x38, 44x32)
        int arcSize = 3;
        int rect1 = 32;
        //int rect2 = 38;
        int rect3 = 44;
        g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
        //g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
        g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

        // 2 keyline circles: 20dp and 44dp
        g.drawCenteredCircle(center, center, 10);
        g.drawCenteredCircle(center, center, 22);
    }

    @Override
    protected boolean includeDensity(@NonNull Density density) {
        // Launcher icons should include xxxhdpi as well
        return super.includeDensity(density) || density == Density.XXXHIGH;
    }

    @NonNull
    @Override
    protected String getIconPath(@NonNull Options options, @NonNull String name) {
        if (((AdaptiveIconOptions) options).isWebGraphic) {
            return name + "-web.png"; // Store at the root of the project
        }

        return super.getIconPath(options, name);
    }

    /** Options specific to generating launcher icons */
    public static class AdaptiveIconOptions extends Options {

        public AdaptiveIconOptions() {
            iconFolderKind = IconFolderKind.MIPMAP;
        }

        /**
         * Whether to use the foreground color. If we are using images as the source asset for our
         * icons, you shouldn't apply the foreground color, which would paint over it and obscure
         * the image.
         */
        public boolean useForegroundColor = true;

        /** Foreground color, as an RRGGBB packed integer */
        public int foregroundColor = 0;

        /** Background color, as an RRGGBB packed integer. The background color is used only */
        public int backgroundColor = 0;

        /** Source image to use as a basis for the icon background layer (optional) */
        public BufferedImage backgroundImage;

        /** Whether the image should be cropped or not */
        public boolean cropForeground = true;

        /** Whether the background image should be cropped or not */
        public boolean cropBackground = true;

        /** If set, generate a preview image */
        public PreviewShape previewShape = PreviewShape.NONE;

        /** The shape to use for the legacy icon */
        public Shape legacyShape = Shape.SQUARE;

        /**
         * Whether a web graphic should be generated (will ignore normal density setting). The
         * {@link #generate(GraphicGeneratorContext, Options)} method will use this to decide
         * whether to generate a normal density icon or a high res web image. The {@link
         * GraphicGenerator#generate(String, Map, GraphicGeneratorContext, Options, String)} method
         * will use this flag to determine whether it should include a web graphic in its iteration.
         */
        public boolean isWebGraphic;

        /** Whether to draw the keyline shapes */
        public boolean showGrid;

        /** Whether to draw the safe zone circle */
        public boolean showSafeZone;

        /** The density of the preview images */
        public Density previewDensity;

        /** The foreground layer name, used to generate resource paths */
        public String foregroundLayerName;

        /** The background layer name, used to generate resource paths */
        public String backgroundLayerName;
    }

    public enum PreviewShape {
        NONE("none", "none"),
        CIRCLE("circle", "Circle"),
        SQUIRCLE("squircle", "Squircle"),
        ROUNDED_SQUARE("rounded-square", "Rounded Square"),
        SQUARE("square", "Square"),
        FULL_BLEED("full-bleed-layers", "Full Bleed Layers"),
        LEGACY("legacy", "Legacy (API <= 24)"),
        LEGACY_ROUND("legacy-round", "Round (API 25)");

        /** Id, used when shape is converted to a string */
        public final String id;
        /** Display name, when shape is displayed to the end-user */
        public final String displayName;

        PreviewShape(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }
}
