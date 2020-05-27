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
package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_HEIGHT;
import static com.android.SdkConstants.ATTR_WIDTH;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_VECTOR;
import static com.android.SdkConstants.UNIT_DIP;
import static com.android.SdkConstants.UNIT_DP;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelVariant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Looks for issues with converting vector drawables to bitmaps for backward compatibility. */
public class VectorDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                            + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                            + "generated for different screen resolutions for backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, and this "
                            + "lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for those "
                            + "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link VectorDetector} */
    public VectorDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    /**
     * Returns true if the given Gradle project model supports raster image generation for vector
     * drawables.
     *
     * @param project the project to check
     * @return true if the plugin supports raster image generation
     */
    public static boolean isVectorGenerationSupported(@NonNull Project project) {
        GradleVersion modelVersion = project.getGradleModelVersion();
        // Requires 1.4.x or higher.
        return modelVersion != null && modelVersion.isAtLeastIncludingPreviews(1, 4, 0);
    }

    /**
     * Returns true if the given Gradle project model supports raster image generation for vector
     * drawables with gradients.
     *
     * @param project the project to check
     * @return true if the plugin supports raster image generation
     */
    public static boolean isVectorGenerationSupportedForGradient(@NonNull Project project) {
        GradleVersion modelVersion = project.getGradleModelVersion();
        // Requires 3.1.x or higher.
        return modelVersion != null && modelVersion.isAtLeastIncludingPreviews(3, 1, 0);
    }

    /**
     * Returns true if the given Gradle project model supports raster image generation for vector
     * drawables with the android:fillType attribute.
     *
     * @param project the project to check
     * @return true if the plugin supports raster image generation
     */
    public static boolean isVectorGenerationSupportedForFillType(@NonNull Project project) {
        GradleVersion modelVersion = project.getGradleModelVersion();
        // Requires 3.2.x or higher.
        return modelVersion != null && modelVersion.isAtLeastIncludingPreviews(3, 2, 0);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        checkSize(context, document);

        // If minSdkVersion >= 24, we're not generating compatibility bitmap icons.
        int apiThreshold = 24;
        Project project = context.getMainProject();
        if (project.getMinSdkVersion().getFeatureLevel() >= apiThreshold) {
            return;
        }

        // Vector generation is only done for Gradle projects
        if (!project.isGradleProject()) {
            return;
        }

        // Not using a Gradle plugin that supports vector image generation?
        if (!isVectorGenerationSupported(project)) {
            return;
        }

        Element root = document.getDocumentElement();
        // If this is not actually a vector icon, nothing to do in this detector
        if (root == null || !root.getTagName().equals(TAG_VECTOR)) {
            return;
        }

        // If this vector asset is in a -v24 folder, we're not generating bitmap icons.
        if (context.getFolderVersion() >= apiThreshold) {
            return;
        }

        if (usingSupportLibVectors(project)) {
            return;
        }

        // TODO: Check to see if there already is a -?dpi version of the file; if so,
        // we also won't be generating a bitmap image.

        boolean generationDueToGradient =
                containsGradient(document) && isVectorGenerationSupportedForGradient(project);

        if (!generationDueToGradient) {
            boolean generationDueToFillType =
                    containsFillType(document) && isVectorGenerationSupportedForFillType(project);
            if (!generationDueToFillType) {
                apiThreshold = 21;
                // If minSdkVersion >= 21, we're not generating compatibility bitmap icons.
                if (project.getMinSdkVersion().getFeatureLevel() >= apiThreshold) {
                    return;
                }
                // If this vector asset is in a -v21 folder, we're not generating bitmap icons.
                if (context.getFolderVersion() >= apiThreshold) {
                    return;
                }
            }
        }

        checkSupported(context, root, apiThreshold);
    }

    private static void checkSize(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Attr widthAttribute = root.getAttributeNodeNS(ANDROID_URI, ATTR_WIDTH);
        Attr heightAttribute = root.getAttributeNodeNS(ANDROID_URI, ATTR_HEIGHT);
        if (widthAttribute == null || heightAttribute == null) {
            return;
        }
        try {
            int width = getDipSize(widthAttribute);
            int height = getDipSize(heightAttribute);
            Attr wrong;
            if (width > 200) {
                wrong = widthAttribute;
            } else if (height > 200) {
                wrong = heightAttribute;
            } else {
                return;
            }
            context.report(
                    ISSUE,
                    wrong,
                    context.getValueLocation(wrong),
                    "Limit vector icons sizes to 200\u00D7200 to keep icon drawing "
                            + "fast; see https://developer.android.com/studio/write/vector-asset-studio#when for more");
        } catch (NumberFormatException ignore) {
        }
    }

    private static int getDipSize(Attr attribute) {
        String s = attribute.getValue();
        if (s.isEmpty() || !Character.isDigit(s.charAt(0))) {
            return -1;
        }
        if (s.endsWith(UNIT_DP)) {
            s = s.substring(0, s.length() - UNIT_DP.length());
        } else if (s.endsWith(UNIT_DIP)) {
            s = s.substring(0, s.length() - UNIT_DIP.length());
        } else {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
            return -1;
        }
    }

    private static boolean containsGradient(@NonNull Document document) {
        return findElement(document, element -> "gradient".equals(element.getTagName())) != null;
    }

    private static boolean containsFillType(Document document) {
        return findElement(document, element -> element.hasAttributeNS(ANDROID_URI, "fillType"))
                != null;
    }

    @Nullable
    private static Element findElement(
            @NonNull Document document, @NonNull Predicate<Element> predicate) {
        Deque<Element> elements = new ArrayDeque<>();
        elements.add(document.getDocumentElement());

        Element element;
        while ((element = elements.poll()) != null) {
            if (predicate.test(element)) {
                return element;
            }
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    elements.add((Element) child);
                }
            }
        }
        return null;
    }

    static boolean usingSupportLibVectors(@NonNull Project project) {
        GradleVersion version = project.getGradleModelVersion();
        if (version == null || version.getMajor() < 2) {
            return false;
        }

        LintModelVariant variant = project.getBuildVariant();
        return variant != null && variant.getUseSupportLibraryVectorDrawables();
    }

    /** Recursive element check for unsupported attributes and tags */
    private static void checkSupported(
            @NonNull XmlContext context, @NonNull Element element, int apiThreshold) {
        // Unsupported tags
        String tag = element.getTagName();
        if (TAG_CLIP_PATH.equals(tag)) {
            String message =
                    String.format(
                            "This tag is not supported in images generated from this vector icon for "
                                    + "API < %d; check generated icon to make sure it looks acceptable",
                            apiThreshold);
            context.report(ISSUE, element, context.getLocation(element), message);
        } else if ("group".equals(tag)) {
            LintModelModule model = context.getMainProject().getBuildModule();
            if (model != null
                    && model.getGradleVersion() != null
                    && model.getGradleVersion().getMajor() == 1
                    && model.getGradleVersion().getMinor() == 4) {
                String message =
                        "Update Gradle plugin version to 1.5+ to correctly handle "
                                + "`<group>` tags in generated bitmaps";
                context.report(ISSUE, element, context.getElementLocation(element), message);
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (("autoMirrored".equals(name)
                            || "trimPathStart".equals(name)
                            || "trimPathEnd".equals(name)
                            || "trimPathOffset".equals(name))
                    && ANDROID_URI.equals(attr.getNamespaceURI())) {
                String message =
                        String.format(
                                "This attribute is not supported in images generated from this vector icon "
                                        + "for API < %d; check generated icon to make sure it looks acceptable",
                                apiThreshold);
                context.report(ISSUE, attr, context.getNameLocation(attr), message);
            }

            String value = attr.getValue();
            if (ResourceUrl.parse(value) != null) {
                String message =
                        String.format(
                                "Resource references will not work correctly in images generated for this "
                                        + "vector icon for API < %d; check generated icon to make sure it looks "
                                        + "acceptable",
                                apiThreshold);
                context.report(ISSUE, attr, context.getValueLocation(attr), message);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkSupported(context, (Element) child, apiThreshold);
            }
        }
    }
}
