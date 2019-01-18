/*
 * Copyright (C) 2014 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.tools.lint.checks.ViewHolderDetector.INFLATE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiMethod;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Looks for layout inflation calls passing null as the view root */
public class LayoutInflationDetector extends LayoutDetector implements SourceCodeScanner {

    @SuppressWarnings("unchecked")
    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutInflationDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES,
                    Scope.JAVA_FILE_SCOPE);

    /** Passing in a null parent to a layout inflater */
    public static final Issue ISSUE =
            Issue.create(
                            "InflateParams",
                            "Layout Inflation without a Parent",
                            "When inflating a layout, avoid passing in null as the parent view, since "
                                    + "otherwise any layout parameters on the root of the inflated layout will be ignored.",
                            Category.CORRECTNESS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://www.doubleencore.com/2013/05/layout-inflation-as-intended");

    private static final String ERROR_MESSAGE =
            "Avoid passing `null` as the view root (needed to resolve "
                    + "layout parameters on the inflated layout's root element)";

    /** Constructs a new {@link LayoutInflationDetector} check */
    public LayoutInflationDetector() {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPendingErrors != null) {
            for (Pair<String, Location> pair : mPendingErrors) {
                String inflatedLayout = pair.getFirst();
                if (mLayoutsWithRootLayoutParams == null
                        || !mLayoutsWithRootLayoutParams.contains(inflatedLayout)) {
                    // No root layout parameters on the inflated layout: no need to complain
                    continue;
                }
                Location location = pair.getSecond();
                context.report(ISSUE, location, ERROR_MESSAGE);
            }
        }
    }

    // ---- Implements XmlScanner ----

    private Set<String> mLayoutsWithRootLayoutParams;
    private List<Pair<String, Location>> mPendingErrors;

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            NamedNodeMap attributes = root.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (attribute.getLocalName() != null
                        && attribute.getLocalName().startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
                    if (mLayoutsWithRootLayoutParams == null) {
                        mLayoutsWithRootLayoutParams = Sets.newHashSetWithExpectedSize(20);
                    }
                    mLayoutsWithRootLayoutParams.add(Lint.getBaseName(context.file.getName()));
                    break;
                }
            }
        }
    }

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(INFLATE);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        assert method.getName().equals(INFLATE);
        if (call.getReceiver() == null) {
            return;
        }
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        UExpression second = arguments.get(1);
        if (!UastLiteralUtils.isNullLiteral(second)) {
            return;
        }

        UExpression first = arguments.get(0);
        ResourceReference reference = ResourceReference.get(first);
        if (reference == null) {
            return;
        }

        String layoutName = reference.getName();
        if (context.getScope().contains(Scope.RESOURCE_FILE)) {
            // We're doing a full analysis run: we can gather this information
            // incrementally
            if (!context.getDriver().isSuppressed(context, ISSUE, call)) {
                if (mPendingErrors == null) {
                    mPendingErrors = Lists.newArrayList();
                }
                Location location = context.getLocation(second);
                mPendingErrors.add(Pair.of(layoutName, location));
            }
        } else if (hasLayoutParams(context, layoutName)) {
            context.report(ISSUE, call, context.getLocation(second), ERROR_MESSAGE);
        }
    }

    private static boolean hasLayoutParams(@NonNull JavaContext context, String name) {
        LintClient client = context.getClient();
        if (!client.supportsProjectResources()) {
            return true; // not certain
        }

        Project project = context.getProject();
        ResourceRepository resources = client.getResourceRepository(project, true, false);
        if (resources == null) {
            return true; // not certain
        }

        List<ResourceItem> items =
                resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, name);

        for (ResourceItem item : items) {
            PathString source = item.getSource();
            if (source == null) {
                return true; // Not certain.
            }

            try {
                XmlPullParser parser = client.createXmlPullParser(source);
                if (parser != null && hasLayoutParams(parser)) {
                    return true;
                }
            } catch (XmlPullParserException | IOException e) {
                context.log(e, "Could not read/parse inflated layout");
                return true; // not certain
            }
        }

        return false;
    }

    @VisibleForTesting
    static boolean hasLayoutParams(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.START_TAG) {
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if (parser.getAttributeName(i).startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
                        String prefix = parser.getAttributePrefix(i);
                        if (prefix != null
                                && !prefix.isEmpty()
                                && ANDROID_URI.equals(parser.getNamespace(prefix))) {
                            return true;
                        }
                    }
                }

                return false;
            } else if (event == XmlPullParser.END_DOCUMENT || event == XmlPullParser.END_TAG) {
                return false;
            }
        }
    }
}
