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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Checks for the combination of textAllCaps=true and using markup in
 * the string being formatted
 */
public class AllCapsDetector extends LayoutDetector {
    /** Using all caps with markup */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE = Issue.create(
            "AllCaps", //$NON-NLS-1$
            "Combining textAllCaps and markup",
            "The textAllCaps text transform will end up calling `toString` on the " +
            "`CharSequence`, which has the net effect of removing any markup such as " +
            "`<b>`. This check looks for usages of strings containing markup that also " +
            "specify `textAllCaps=true`.",
            Category.TYPOGRAPHY,
            8,
            Severity.WARNING,
            new Implementation(
                    AllCapsDetector.class,
                    Scope.ALL_RESOURCES_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs an {@linkplain AllCapsDetector} */
    public AllCapsDetector() {
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("textAllCaps");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!VALUE_TRUE.equals(attribute.getValue())) {
            return;
        }

        String text = attribute.getOwnerElement().getAttributeNS(ANDROID_URI, ATTR_TEXT);
        if (text.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(text);
        if (url == null || url.framework) {
            return;
        }

        LintClient client = context.getClient();
        Project project = context.getMainProject();
        AbstractResourceRepository repository = client.getResourceRepository(project, true, true);
        if (repository == null) {
            return;
        }

        List<ResourceItem> items = repository.getResourceItem(url.type, url.name);
        if (items == null || items.isEmpty()) {
            return;
        }
        ResourceValue resourceValue = items.get(0).getResourceValue(false);
        if (resourceValue == null) {
            return;
        }

        String rawXmlValue = resourceValue.getRawXmlValue();
        if (rawXmlValue.contains("<")) {
            String message = String.format("Using `textAllCaps` with a string (`%1$s`) that "
                            + "contains markup; the markup will be dropped by the caps "
                    + "conversion", url.name);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}
