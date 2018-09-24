/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_FOREGROUND;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_BOTTOM;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_TOP;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VIEW_INCLUDE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks whether a root FrameLayout can be replaced with a {@code <merge>} tag. */
public class MergeRootFrameLayoutDetector extends LayoutDetector implements SourceCodeScanner {
    /**
     * Set of layouts that we want to enable the warning for. We only warn for {@code
     * <FrameLayout>}'s that are the root of a layout included from another layout, or directly
     * referenced via a {@code setContentView} call.
     */
    private Set<String> mWhitelistedLayouts;

    /**
     * Set of pending [layout, location] pairs where the given layout is a FrameLayout that perhaps
     * should be replaced by a {@code <merge>} tag (if the layout is included or set as the content
     * view. This must be processed after the whole project has been scanned since the set of
     * includes etc can be encountered after the included layout.
     */
    private List<Pair<String, Location.Handle>> mPending;

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "MergeRootFrame",
                            "FrameLayout can be replaced with `<merge>` tag",
                            "If a `<FrameLayout>` is the root of a layout and does not provide background "
                                    + "or padding etc, it can often be replaced with a `<merge>` tag which is slightly "
                                    + "more efficient. Note that this depends on context, so make sure you understand "
                                    + "how the `<merge>` tag works before proceeding.",
                            Category.PERFORMANCE,
                            4,
                            Severity.WARNING,
                            new Implementation(
                                    MergeRootFrameLayoutDetector.class,
                                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "http://android-developers.blogspot.com/2009/03/android-layout-tricks-3-optimize-by.html");

    /** Constructs a new {@link MergeRootFrameLayoutDetector} */
    public MergeRootFrameLayoutDetector() {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPending != null && mWhitelistedLayouts != null) {
            // Process all the root FrameLayouts that are eligible, and generate
            // suggestions for <merge> replacements for any layouts that are included
            // from other layouts
            for (Pair<String, Handle> pair : mPending) {
                String layout = pair.getFirst();
                if (mWhitelistedLayouts.contains(layout)) {
                    Handle handle = pair.getSecond();

                    Object clientData = handle.getClientData();
                    if (clientData instanceof Node) {
                        if (context.getDriver().isSuppressed(null, ISSUE, (Node) clientData)) {
                            continue;
                        }
                    }

                    Location location = handle.resolve();
                    context.report(
                            ISSUE,
                            location,
                            "This `<FrameLayout>` can be replaced with a `<merge>` tag");
                }
            }
        }
    }

    // Implements XmlScanner

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(VIEW_INCLUDE, FRAME_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals(VIEW_INCLUDE)) {
            String layout = element.getAttribute(ATTR_LAYOUT); // NOTE: Not in android: namespace
            if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) { // Ignore @android:layout/ layouts
                layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                whiteListLayout(layout);
            }
        } else {
            assert tag.equals(FRAME_LAYOUT);
            if (Lint.isRootElement(element)
                    && ((isWidthFillParent(element) && isHeightFillParent(element))
                            || !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY))
                    && !element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
                    && !element.hasAttributeNS(ANDROID_URI, ATTR_FOREGROUND)
                    && !hasPadding(element)) {
                String layout = Lint.getLayoutName(context.file);
                Handle handle = context.createLocationHandle(element);
                handle.setClientData(element);

                if (!context.getProject().getReportIssues()) {
                    // If this is a library project not being analyzed, ignore it
                    return;
                }

                if (mPending == null) {
                    mPending = new ArrayList<>();
                }
                mPending.add(Pair.of(layout, handle));
            }
        }
    }

    private void whiteListLayout(String layout) {
        if (mWhitelistedLayouts == null) {
            mWhitelistedLayouts = new HashSet<>();
        }
        mWhitelistedLayouts.add(layout);
    }

    // implements SourceCodeScanner

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<UExpression> expressions = call.getValueArguments();
        if (expressions.size() == 1) {
            ResourceReference reference =
                    UastLintUtils.toAndroidReferenceViaResolve(expressions.get(0));

            if (reference != null && reference.getType() == ResourceType.LAYOUT) {
                whiteListLayout(reference.getName());
            }
        }
    }

    private static boolean isFillParent(@NonNull Element element, @NonNull String dimension) {
        String width = element.getAttributeNS(ANDROID_URI, dimension);
        return width.equals(VALUE_MATCH_PARENT) || width.equals(VALUE_FILL_PARENT);
    }

    protected static boolean isWidthFillParent(@NonNull Element element) {
        return isFillParent(element, ATTR_LAYOUT_WIDTH);
    }

    protected static boolean isHeightFillParent(@NonNull Element element) {
        return isFillParent(element, ATTR_LAYOUT_HEIGHT);
    }

    protected static boolean hasPadding(@NonNull Element root) {
        return root.hasAttributeNS(ANDROID_URI, ATTR_PADDING)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_LEFT)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_RIGHT)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_TOP)
                || root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_BOTTOM);
    }
}
