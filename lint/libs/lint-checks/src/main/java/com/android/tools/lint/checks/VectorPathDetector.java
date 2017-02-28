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

import static com.android.SdkConstants.TAG_VECTOR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
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
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
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
        if (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
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

        if (value.length() < MAX_PATH_DATA_LENGTH) {
            return;
        }

        // Make sure this is in a vector; pathData can occur in transition files too!
        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null || !root.getTagName().equals(TAG_VECTOR)) {
            return;
        }

        // Only warn if we're not already rasterizing
        if (isRasterizingVector(context)) {
            return;
        }

        String message = String.format("Very long vector path (%1$d characters), which is bad for "
                + "performance. Considering reducing precision, removing minor details or "
                + "rasterizing vector.", value.length());
        context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
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

}
