/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.manifest.parser.components;

import com.android.annotations.NonNull;
import com.android.tools.manifest.parser.XmlNode;
import com.android.xml.AndroidManifest;

import java.util.ArrayList;
import java.util.List;

public abstract class ManifestAppComponentInfo {

    protected String qualifiedName = "";

    protected boolean enabled = true;

    protected boolean exported = true;

    protected List<IntentFilter> intentFilters = new ArrayList<>();

    /**
     * Parse an activity xml element (see https://developer.android.com/guide/topics/manifest/activity-element).
     */
    public ManifestAppComponentInfo(@NonNull XmlNode node, @NonNull String applicationId) {
        for (String attribute : node.attributes().keySet()) {
            String value = node.attributes().get(attribute);

            if (AndroidManifest.ATTRIBUTE_NAME.equals(attribute)) {
                // TODO: extract this logic to separate class
                if (value.startsWith(".")) {
                    qualifiedName = applicationId + value;
                }
                else {
                    qualifiedName = value;
                }
            } else if (AndroidManifest.ATTRIBUTE_ENABLED.equals(attribute)) {
                enabled = value.isEmpty() || "true".equals(value);
            } else if (AndroidManifest.ATTRIBUTE_EXPORTED.equals(attribute)) {
                exported = value.isEmpty() || "true".equals(value);
            }
        }

        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_INTENT.equals(child.name())) {
                IntentFilter intentFilter = parseIntentFilter(child);
                intentFilters.add(intentFilter);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean getExported() {
        return exported;
    }

    public boolean hasIntentFilter() {
        return !intentFilters.isEmpty();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    @NonNull
    public List<IntentFilter> getIntentFilters() {
        return intentFilters;
    }

    public boolean hasCategory(@NonNull String name) {
        for (IntentFilter intentFilter : getIntentFilters()) {
            if (intentFilter.hasCategory(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAction(@NonNull String name) {
        for (IntentFilter intentFilter : getIntentFilters()) {
            if (intentFilter.hasAction(name)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static IntentFilter parseIntentFilter(@NonNull XmlNode node) {
        IntentFilter intentFilter = new IntentFilter();
        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_ACTION.equals(child.name())) {
                String action = getNameChildNodeValue(child);
                intentFilter.addAction(action);
            } else if (AndroidManifest.NODE_CATEGORY.equals(child.name())) {
                String category = getNameChildNodeValue(child);
                intentFilter.addCategory(category);
            }
        }
        return intentFilter;
    }

    @NonNull
    private static String getNameChildNodeValue(@NonNull XmlNode node) {
        for (String attribute : node.attributes().keySet()) {
            if (AndroidManifest.ATTRIBUTE_NAME.equals(attribute)) {
                return node.attributes().get(attribute);
            }
        }
        throw new IllegalStateException("No name attribute in node " + node.name());
    }
}
