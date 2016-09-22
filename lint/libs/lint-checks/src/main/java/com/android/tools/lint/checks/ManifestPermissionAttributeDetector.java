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

import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_PERMISSION;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.ImmutableSet;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Checks if the 'permission' attribute was set on a valid tag. Valid tags in the Manifest are:
 * activity, application, provider, service, receiver, activity-alias and path-permission.
 */
public class ManifestPermissionAttributeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidPermission", //$NON-NLS-1$
            "Invalid Permission Attribute",

            "Not all elements support the permission attribute. If a permission is set on an " +
            "invalid element, it is a no-op and ignored. Ensure that this permission attribute " +
            "was set on the correct element to protect the correct component.",

            Category.SECURITY,
            5,
            Severity.ERROR,
            new Implementation(
                    ManifestPermissionAttributeDetector.class,
                    Scope.MANIFEST_SCOPE
            ));

    private static final Set<String> ALLOWED_TAGS = ImmutableSet.of(
            NODE_ACTIVITY, NODE_APPLICATION, NODE_PROVIDER, NODE_SERVICE, NODE_RECEIVER,
            NODE_ACTIVITY_ALIAS, NODE_PERMISSION);

    // ---- Implements Detector.XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_PERMISSION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String parent = attribute.getOwnerElement().getTagName();

        if (!ALLOWED_TAGS.contains(parent)) {
            String message =
                    "Protecting an unsupported element with a permission is a no-op and " +
                    "potentially dangerous.";
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}
