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

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTO_TEXT;
import static com.android.SdkConstants.ATTR_CAPITALIZE;
import static com.android.SdkConstants.ATTR_EDITABLE;
import static com.android.SdkConstants.ATTR_INPUT_METHOD;
import static com.android.SdkConstants.ATTR_NUMERIC;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;
import static com.android.SdkConstants.ATTR_SINGLE_LINE;
import static com.android.SdkConstants.CLASS_PREFERENCE;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Check which looks for usage of deprecated tags, attributes, etc. */
public class DeprecationDetector extends ResourceXmlDetector {
    /** Usage of deprecated views or attributes */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE =
            Issue.create(
                    "Deprecated",
                    "Using deprecated resources",
                    "Deprecated views, attributes and so on are deprecated because there "
                            + "is a better way to do something. Do it that new way. You've been warned.",
                    Category.CORRECTNESS,
                    2,
                    Severity.WARNING,
                    new Implementation(
                            DeprecationDetector.class,
                            Scope.MANIFEST_AND_RESOURCE_SCOPE,
                            Scope.MANIFEST_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link DeprecationDetector} */
    public DeprecationDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() == ResourceFolderType.XML) {
            Element rootElement = document.getDocumentElement();
            String tagName = rootElement.getTagName();
            if (tagName.startsWith("android.preference.")) {
                context.report(
                        ISSUE,
                        rootElement,
                        context.getNameLocation(rootElement),
                        "The `android.preference` library is deprecated, it is recommended that you migrate to the AndroidX Preference library instead.");
            }
            if (tagName.startsWith("androidx.preference.")) {
                // Qualified androidx preference tags can skip inheritance checking.
                return;
            }
            UastParser parser = context.getClient().getUastParser(context.getProject());
            PsiClass tagClass = parser.getEvaluator().findClass(rootElement.getTagName());
            if (tagClass != null) {
                if (parser.getEvaluator().inheritsFrom(tagClass, CLASS_PREFERENCE, false)) {
                    context.report(
                            ISSUE,
                            rootElement,
                            context.getNameLocation(rootElement),
                            String.format(
                                    "`%1$s` inherits from `android.preference.Preference` which is now deprecated, it is recommended that you migrate to the AndroidX Preference library.",
                                    tagName));
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(ABSOLUTE_LAYOUT, TAG_USES_PERMISSION_SDK_M);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                // TODO: fill_parent is deprecated as of API 8.
                // We could warn about it, but it will probably be very noisy
                // and make people disable the deprecation check; let's focus on
                // some older flags for now
                //"fill_parent",

                ATTR_EDITABLE,
                ATTR_INPUT_METHOD,
                ATTR_AUTO_TEXT,
                ATTR_CAPITALIZE,
                ATTR_NUMERIC,
                ATTR_PHONE_NUMBER,
                ATTR_PASSWORD

                // ATTR_SINGLE_LINE is marked deprecated, but (a) it's used a lot everywhere,
                // including in our own apps, and (b) replacing it with the suggested replacement
                // can lead to crashes; see issue 37137344

                // ATTR_ENABLED  is marked deprecated in android.R.attr but apparently
                // using the suggested replacement of state_enabled doesn't work, see issue 27613

                // These attributes are also deprecated; not yet enabled until we
                // know the API level to apply the deprecation for:

                // "ignored as of ICS (but deprecated earlier)"
                //"fadingEdge",

                // "This attribute is not used by the Android operating system."
                //"restoreNeedsApplication",

                // "This will create a non-standard UI appearance, because the search bar UI is
                // changing to use only icons for its buttons."
                //"searchButtonText",
                );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String message = String.format("`%1$s` is deprecated", tagName);
        if (TAG_USES_PERMISSION_SDK_M.equals(tagName)) {
            message += ": Use `" + TAG_USES_PERMISSION_SDK_23 + " instead";
        }
        context.report(ISSUE, element, context.getNameLocation(element), message);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        String fix;
        int minSdk = 1;
        if (name.equals(ATTR_EDITABLE)) {
            if (!EDIT_TEXT.equals(attribute.getOwnerElement().getTagName())) {
                fix = "Use an `<EditText>` to make it editable";
            } else {
                if (VALUE_TRUE.equals(attribute.getValue())) {
                    fix = "`<EditText>` is already editable";
                } else {
                    fix = "Use `inputType` instead";
                }
            }
        } else if (name.equals(ATTR_SINGLE_LINE)) {
            if (VALUE_FALSE.equals(attribute.getValue())) {
                fix = "False is the default, so just remove the attribute";
            } else {
                fix = "Use `maxLines=\"1\"` instead";
            }
        } else {
            assert name.equals(ATTR_INPUT_METHOD)
                    || name.equals(ATTR_CAPITALIZE)
                    || name.equals(ATTR_NUMERIC)
                    || name.equals(ATTR_PHONE_NUMBER)
                    || name.equals(ATTR_PASSWORD)
                    || name.equals(ATTR_AUTO_TEXT);
            fix = "Use `inputType` instead";
            // The inputType attribute was introduced in API 3 so don't warn about
            // deprecation if targeting older platforms
            minSdk = 3;
        }

        if (context.getProject().getMinSdk() < minSdk) {
            return;
        }

        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                String.format("`%1$s` is deprecated: %2$s", attribute.getName(), fix));
    }
}
