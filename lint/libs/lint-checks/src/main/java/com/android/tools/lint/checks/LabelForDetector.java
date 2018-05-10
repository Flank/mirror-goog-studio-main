/*
 * Copyright (C) 2012 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LABEL_FOR;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/** Detector which finds unlabeled text fields */
public class LabelForDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "LabelFor",
                    "Missing accessibility label",
                    "Editable text fields should provide an `android:hint` or, provided your "
                            + "`minSdkVersion` is at least 17, they may be referenced by a view "
                            + "with a `android:labelFor` attribute.\n"
                            + "\n"
                            + "When using `android:labelFor`, be sure to provide an `android:text` or an "
                            + "`android:contentDescription`.\n"
                            + "\n"
                            + "If your view is labeled but by a label in a different layout which "
                            + "includes this one, just suppress this warning from lint.",
                    Category.A11Y,
                    2,
                    Severity.WARNING,
                    new Implementation(LabelForDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String PREFIX = "Missing accessibility label";

    private static final String PROVIDE_HINT =
            "where minSdk < 17, you should provide an `android:hint`";

    private static final String PROVIDE_LABEL_FOR_OR_HINT =
            "provide either a view with an "
                    + "`android:labelFor` that references this view or provide an `android:hint`";

    private Set<String> mLabels;
    private List<Element> mEditableTextFields;

    /** Constructs a new {@link LabelForDetector} */
    public LabelForDetector() {}

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_LABEL_FOR);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mEditableTextFields != null) {
            if (mLabels == null) {
                mLabels = Collections.emptySet();
            }

            for (Element element : mEditableTextFields) {
                String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);

                boolean hintProvided = element.hasAttributeNS(ANDROID_URI, ATTR_HINT);
                boolean labelForProvided = false;

                if (mLabels.contains(id)) {
                    labelForProvided = true;
                } else if (id.startsWith(NEW_ID_PREFIX)) {
                    labelForProvided = mLabels.contains(ID_PREFIX + stripIdPrefix(id));
                } else if (id.startsWith(ID_PREFIX)) {
                    labelForProvided = mLabels.contains(NEW_ID_PREFIX + stripIdPrefix(id));
                }

                XmlContext xmlContext = (XmlContext) context;
                String message = "";
                Location location = xmlContext.getElementLocation(element);
                int minSdk = context.getMainProject().getMinSdk();

                if (hintProvided && labelForProvided) {
                    // Note: labelFor no-ops below 17.
                    if (minSdk >= 17) {
                        message = PROVIDE_LABEL_FOR_OR_HINT + ", but not both";
                    }
                } else if (!hintProvided && !labelForProvided) {
                    if (minSdk < 17) {
                        message = PROVIDE_HINT;
                    } else {
                        message = PROVIDE_LABEL_FOR_OR_HINT;
                    }
                } else {
                    // Note: if only android:hint is provided, no need for a warning.

                    if (labelForProvided) {
                        if (minSdk < 17) {
                            message = PROVIDE_HINT;
                        }
                    }
                }

                if (!message.isEmpty()) {
                    xmlContext.report(ISSUE, element, location, messageWithPrefix(message));
                }
            }
        }

        mLabels = null;
        mEditableTextFields = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {

        assert (attribute.getLocalName().equals(ATTR_LABEL_FOR));

        Element element = attribute.getOwnerElement();

        if (mLabels == null) {
            mLabels = Sets.newHashSet();
        }
        mLabels.add(attribute.getValue());

        // Unlikely this is anything other than a TextView. If it is, bail.
        if (!element.getLocalName().equals(TEXT_VIEW)) {
            return;
        }

        Attr textAttributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_TEXT);
        Attr contentDescriptionNode =
                element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);

        if ((textAttributeNode == null || textAttributeNode.getValue().isEmpty())
                && (contentDescriptionNode == null
                        || contentDescriptionNode.getValue().isEmpty())) {
            LintFix fix =
                    fix().alternatives(
                                    fix().set(ANDROID_URI, ATTR_TEXT, "").caretBegin().build(),
                                    fix().set(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, "")
                                            .caretBegin()
                                            .build());

            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element, null, ANDROID_URI, ATTR_LABEL_FOR),
                    messageWithPrefix(
                            "when using `android:labelFor`, you must also define an "
                                    + "`android:text` or an `android:contentDescription`"),
                    fix);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
            Attr hintAttributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT);
            if (hintAttributeNode.getValue().isEmpty()) {
                context.report(
                        ISSUE,
                        hintAttributeNode,
                        context.getLocation(hintAttributeNode),
                        "Empty `android:hint` attribute");
            }
        }
        if (mEditableTextFields == null) {
            mEditableTextFields = new ArrayList<>();
        }
        mEditableTextFields.add(element);
    }

    private static String messageWithPrefix(String message) {
        return PREFIX + ": " + message;
    }
}
