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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_VECTOR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.QuickfixData;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Looks for issues with long vector paths
 */
public class VectorPathDetector extends ResourceXmlDetector {
    /** Paths that are too long */
    public static final Issue PATH_LENGTH = Issue.create(
            "VectorPath",
            "Long vector paths",
            "Using long vector paths is bad for performance. There are several ways to " +
            "make the `pathData` shorter:\n" +
            "* Using less precision\n" +
            "* Removing some minor details\n" +
            "* Using the Android Studio vector conversion tool\n" +
            "* Rasterizing the image (converting to PNG)",

            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorPathDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Path validation */
    public static final Issue PATH_VALID = Issue.create(
            "InvalidVectorPath",
            "Invalid vector paths",
            "This check ensures that vector paths are valid. For example, it makes "
                    + "sure that the numbers are not using scientific notation (such as 1.0e3) "
                    + "which can lead to runtime crashes on older devices.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    VectorPathDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://code.google.com/p/android/issues/detail?id=78162");

    // Arbitrary limit suggested in https://code.google.com/p/android/issues/detail?id=235219
    private static final int MAX_PATH_DATA_LENGTH = 800;

    /** Constructs a new {@link VectorPathDetector} */
    public VectorPathDetector() {
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("pathData");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith(PREFIX_RESOURCE_REF)) {
            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.framework) {
                return;
            }
            AbstractResourceRepository repository = context.getClient()
                    .getResourceRepository(context.getProject(), true, true);
            if (repository == null) {
                return;
            }
            List<ResourceItem> item = repository.getResourceItem(url.type, url.name);
            if (item == null || item.isEmpty()) {
                return;
            }
            value = item.get(0).getValueText();
            if (value == null) {
                return;
            }
        }

        // Make sure this is in a vector; pathData can occur in transition files too!
        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null || !root.getTagName().equals(TAG_VECTOR)) {
            return;
        }

        validatePath(context, attribute, value);

        if (value.length() < MAX_PATH_DATA_LENGTH) {
            return;
        }

        // Only warn if we're not already rasterizing
        if (isRasterizingVector(context)) {
            return;
        }

        String message = String.format("Very long vector path (%1$d characters), which is bad for "
                + "performance. Considering reducing precision, removing minor details or "
                + "rasterizing vector.", value.length());
        context.report(PATH_LENGTH, attribute, context.getValueLocation(attribute), message);
    }

    private static boolean isRasterizingVector(XmlContext context) {
        // If minSdkVersion >= 21, we're not generating compatibility vector icons
        Project project = context.getMainProject();
        if (project.getMinSdkVersion().getFeatureLevel() >= 21) {
            return false;
        }

        // If this vector asset is in a -v21 folder, we're not generating vector assets
        if (context.getFolderVersion() >= 21) {
            return false;
        }

        // Not using a plugin that supports vector image generation?
        if (!VectorDetector.isVectorGenerationSupported(context.getMainProject())) {
            return false;
        }

        if (VectorDetector.usingSupportLibVectors(project)) {
            return false;
        }

        // Vector generation is only done for Gradle projects
        return project.isGradleProject();
    }

    private static void validatePath(XmlContext context, Attr attribute, String value) {
        if (context.getMainProject().getMinSdkVersion().getFeatureLevel() >= 21) {
            // Fixed in lollipop
            return;
        }
        try {
            checkPathData(value);
        } catch (NumberFormatException t) {
            String s = t.getMessage();
            Location location = context.getValueLocation(attribute);
            Position start = location.getStart();
            assert start != null;
            int index = value.indexOf(s);
            if (index != -1 && attribute.getValue().contains(s)) {
                location = Location.create(context.file, context.getContents(),
                        start.getOffset() + index,
                        start.getOffset() + index + s.length());
            }

            QuickfixData quickfixData = QuickfixData.create(s);
            if (attribute.getValue().startsWith(PREFIX_RESOURCE_REF)) {
                quickfixData.put(ResourceUrl.parse(attribute.getValue()));
            }
            context.report(PATH_VALID, attribute, location,
                    String.format("Avoid scientific notation (`%1$s`) in vector paths because "
                            + "it can lead to crashes on some devices", s), quickfixData);
        }
    }

    // This code is based on the path parser in the platform. However, it focuses
    // on just validating the numbers - and since it doesn't have to build up actual
    // path nodes etc the code was simplified significantly such that it doesn't really
    // resemble the original code.
    // (frameworks/support/compat/java/android/support/v4/graphics/PathParser.java)

    /**
     * Check the given path data and throw a number format exception (containing the
     * exact invalid string) if it finds a problem
     */
    public static void checkPathData(@NonNull String path) throws NumberFormatException {
        int start = 0;
        int end = 1;
        while (end < path.length()) {
            end = findNextStart(path, end);
            int trimStart = start;
            while (trimStart < end && Character.isWhitespace(path.charAt(trimStart))) {
                trimStart++;
            }
            int trimEnd = end;
            while (trimEnd > trimStart + 1 && Character.isWhitespace(path.charAt(trimEnd - 1))) {
                trimEnd--;
            }
            if (trimEnd > trimStart) {
                checkFloats(path, trimStart, trimEnd);
            }
            start = end;
            end++;
        }
    }

    private static void checkFloats(String s, int start, int end) throws NumberFormatException {
        if (s.charAt(0) == 'z' || s.charAt(0) == 'Z') {
            return;
        }
        int startPosition = start + 1;
        while (startPosition < end) {
            int currentIndex = startPosition;
            boolean foundSeparator = false;
            boolean endWithNegOrDot = false;
            boolean hasExponential = false;
            boolean secondDot = false;
            boolean isExponential = false;
            for (; currentIndex < end; currentIndex++) {
                boolean isPrevExponential = isExponential;
                isExponential = false;
                char currentChar = s.charAt(currentIndex);
                switch (currentChar) {
                    case ' ':
                    case ',':
                        foundSeparator = true;
                        break;
                    case '-':
                        if (currentIndex != startPosition && !isPrevExponential) {
                            foundSeparator = true;
                            endWithNegOrDot = true;
                        }
                        break;
                    case '.':
                        if (!secondDot) {
                            secondDot = true;
                        } else {
                            foundSeparator = true;
                            endWithNegOrDot = true;
                        }
                        break;
                    case 'e':
                    case 'E':
                        hasExponential = isExponential = true;
                        break;
                }
                if (foundSeparator) {
                    break;
                }
            }

            if (hasExponential && startPosition < currentIndex) {
                String expNumber = s.substring(startPosition, currentIndex);
                throw new NumberFormatException(expNumber);
            }

            int endPosition = currentIndex;
            if (endWithNegOrDot) {
                // Keep the '-' or '.' sign with next number.
                startPosition = endPosition;
            } else {
                startPosition = endPosition + 1;
            }
        }
    }

    private static int findNextStart(String s, int end) {
        while (end < s.length()) {
            char c = s.charAt(end);
            if ((((c - 'A') * (c - 'Z') <= 0) || ((c - 'a') * (c - 'z') <= 0))
                    && c != 'e' && c != 'E') {
                return end;
            }
            end++;
        }
        return end;
    }
}
