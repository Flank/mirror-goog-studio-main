/*
 * Copyright (C) 2016 - 2018 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_GROUP;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_LOWER;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Check which looks for potential errors in declarations of ConstraintLayout, such as under
 * specifying constraints
 */
public class ConstraintLayoutDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "MissingConstraints",
                    "Missing Constraints in ConstraintLayout",
                    "The layout editor allows you to place widgets anywhere on the canvas, and it "
                            + "records the current position with designtime attributes (such as "
                            + "`layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if "
                            + "you push your layout on a device, the widgets may appear in a different location "
                            + "than shown in the editor. To fix this, make sure a widget has both horizontal and "
                            + "vertical constraints by dragging from the edge connections.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(ConstraintLayoutDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Latest known version of the ConstraintLayout library (as a {@link GradleVersion} */
    @SuppressWarnings("ConstantConditions")
    @NonNull
    public static final GradleCoordinate LATEST_KNOWN_VERSION =
            new GradleCoordinate(
                    CONSTRAINT_LAYOUT_LIB_GROUP_ID,
                    CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID,
                    SdkConstants.LATEST_CONSTRAINT_LAYOUT_VERSION);

    /** Constructs a new {@link ConstraintLayoutDetector} check */
    public ConstraintLayoutDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return ImmutableSet.of(CONSTRAINT_LAYOUT.oldName(), CONSTRAINT_LAYOUT.newName());
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element layout) {
        // Make sure we're using the current version
        Variant variant = context.getMainProject().getCurrentVariant();
        GradleCoordinate latestAvailable = null;

        if (variant != null) {
            Dependencies dependencies = variant.getMainArtifact().getDependencies();
            for (AndroidLibrary library : dependencies.getLibraries()) {
                MavenCoordinates rc = library.getResolvedCoordinates();
                if (CONSTRAINT_LAYOUT_LIB_GROUP_ID.equals(rc.getGroupId())
                        && CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID.equals(rc.getArtifactId())) {
                    if (latestAvailable == null) {
                        latestAvailable = getLatestVersion(context);
                    }
                    GradleCoordinate version =
                            new GradleCoordinate(
                                    CONSTRAINT_LAYOUT_LIB_GROUP_ID,
                                    CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID,
                                    rc.getVersion());
                    if (COMPARE_PLUS_LOWER.compare(latestAvailable, version) > 0) {
                        String message =
                                "Using version "
                                        + version.getRevision()
                                        + " of the constraint library, which is obsolete";
                        LintFix fix = fix().data(ConstraintLayoutDetector.class);
                        context.report(
                                GradleDetector.DEPENDENCY,
                                layout,
                                context.getLocation(layout),
                                message,
                                fix);
                    }
                }
            }
        }

        // Ensure that all the children have been constrained horizontally and vertically
        for (Node child = layout.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String elementTagName = element.getTagName();

            if (CLASS_CONSTRAINT_LAYOUT_GUIDELINE.isEquals(elementTagName)) {
                continue;
            }

            if (CLASS_CONSTRAINT_LAYOUT_GROUP.isEquals(elementTagName)) {
                // Groups do not need to be constrained
                continue;
            }

            if (TAG_INCLUDE.equals(elementTagName)) {
                // Don't flag includes; they might have the right constraints inside.
                continue;
            }

            if (Lint.isLayoutMarkerTag(elementTagName)) {
                // <requestFocus/>, <tag/>, etc should not have constraint attributes
                continue;
            }

            if (!Strings.isNullOrEmpty(elementTagName)
                    && CLASS_CONSTRAINT_LAYOUT_BARRIER.isEquals(elementTagName)
                    && scanForBarrierConstraint(element)) {
                // The Barrier has the necessary layout constraints. This element is constrained correctly.
                break;
            }

            boolean isConstrainedHorizontally = false;
            boolean isConstrainedVertically = false;

            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String name = attribute.getLocalName();
                if (name == null) {
                    continue;
                }

                if (!name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) || name.endsWith("_creator")) {
                    continue;
                }

                if ((ATTR_LAYOUT_WIDTH.equals(name)
                                && VALUE_MATCH_PARENT.equals(attribute.getNodeValue()))
                        || name.endsWith("toLeftOf")
                        || name.endsWith("toRightOf")
                        || name.endsWith("toStartOf")
                        || name.endsWith("toEndOf")
                        || name.endsWith("toCenterX")) {
                    isConstrainedHorizontally = true;
                    if (isConstrainedVertically) {
                        break;
                    }
                } else if ((ATTR_LAYOUT_HEIGHT.equals(name)
                                && VALUE_MATCH_PARENT.equals(attribute.getNodeValue()))
                        || name.endsWith("toTopOf")
                        || name.endsWith("toBottomOf")
                        || name.endsWith("toCenterY")
                        || name.endsWith("toBaselineOf")) {
                    isConstrainedVertically = true;
                    if (isConstrainedHorizontally) {
                        break;
                    }
                }
            }

            if (!isConstrainedHorizontally || !isConstrainedVertically) {
                // Don't complain if the element doesn't specify absolute x/y - that's
                // when it gets confusing

                String message;
                if (isConstrainedVertically) {
                    message =
                            "This view is not constrained horizontally: at runtime it will "
                                    + "jump to the left unless you add a horizontal constraint";
                } else if (isConstrainedHorizontally) {
                    message =
                            "This view is not constrained vertically: at runtime it will "
                                    + "jump to the top unless you add a vertical constraint";
                } else {
                    message =
                            "This view is not constrained. It only has designtime positions, "
                                    + "so it will jump to (0,0) at runtime unless you add the constraints";
                }
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }

    /**
     * @param element to scan
     * @return true if barrier specific constraint is set. False otherwise.
     */
    private static boolean scanForBarrierConstraint(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName();

            if (name == null) {
                continue;
            }

            if (name.endsWith("barrierDirection")) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    private static GradleCoordinate getLatestVersion(@NonNull XmlContext context) {
        GradleCoordinate latestAvailable = LATEST_KNOWN_VERSION;
        AndroidSdkHandler sdkHandler = context.getClient().getSdk();
        if (sdkHandler != null) {
            ProgressIndicator progress = context.getClient().getRepositoryLogger();
            RepoPackage latestPackage =
                    SdkMavenRepository.findLatestVersion(
                            LATEST_KNOWN_VERSION, sdkHandler, null, progress);
            if (latestPackage != null) {
                GradleCoordinate fromPackage =
                        SdkMavenRepository.getCoordinateFromSdkPath(latestPackage.getPath());
                if (fromPackage != null
                        && COMPARE_PLUS_LOWER.compare(latestAvailable, fromPackage) < 0) {
                    latestAvailable = fromPackage;
                }
            }
        }
        return latestAvailable;
    }
}
