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
package com.android.tools.manifest.parser;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import com.android.tools.manifest.parser.components.ManifestServiceInfo;
import com.android.xml.AndroidManifest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ManifestInfo {

    private final List<ManifestActivityInfo> activities;

    private final List<ManifestServiceInfo> services;

    private final List<String> sdkLibraries;

    private final List<String> instrumentationTargetPackages;

    private String applicationId;

    private String split;

    private int versionCode;

    private boolean debuggable;

    private ManifestInfo() {
        activities = new ArrayList<>();
        services = new ArrayList<>();
        sdkLibraries = new ArrayList<>();
        applicationId = "";
        instrumentationTargetPackages = new ArrayList<>();
        split = null;
        versionCode = 0;
        debuggable = false;
    }

    @NonNull
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    public List<ManifestActivityInfo> activities() {
        return activities;
    }

    @NonNull
    public List<ManifestServiceInfo> services() {
        return services;
    }

    @NonNull
    public List<String> getSdkLibraries() {
        return sdkLibraries;
    }

    @NonNull
    public List<String> getInstrumentationTargetPackages() {
        return instrumentationTargetPackages;
    }

    @Nullable
    public String getSplitName() {
        return split;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public boolean getDebuggable() {
        return debuggable;
    }

    private void parseNode(@NonNull XmlNode node) {
        for (String attribute : node.attributes().keySet()) {
            String value = node.attributes().get(attribute);
            if (AndroidManifest.ATTRIBUTE_PACKAGE.equals(attribute)) {
                applicationId = value;
            } else if (AndroidManifest.ATTRIBUTE_SPLIT.equals(attribute)) {
                split = value;
            }
            else if (AndroidManifest.ATTRIBUTE_VERSIONCODE.equals(attribute) && !value.isEmpty()) {
                versionCode = Integer.parseInt(value);
            }
        }

        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_APPLICATION.equals(child.name())) {
                parseApplication(child);
            } else if (AndroidManifest.NODE_INSTRUMENTATION.equals(child.name())) {
                parseInstrumentation(child);
            }
        }
    }

    private void parseInstrumentation(@NonNull XmlNode node) {
        for (String attribute : node.attributes().keySet()) {
            if (AndroidManifest.ATTRIBUTE_TARGET_PACKAGE.equals(attribute)) {
                instrumentationTargetPackages.add(node.attributes().get(attribute));
            }
        }
    }

    private void parseApplication(@NonNull XmlNode node) {
        debuggable =
                Boolean.parseBoolean(
                        node.attributes()
                                .getOrDefault(AndroidManifest.ATTRIBUTE_DEBUGGABLE, "false"));
        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_ACTIVITY.equals(child.name()) ||
                AndroidManifest.NODE_ACTIVITY_ALIAS.equals(child.name())) {
                activities.add(new ManifestActivityInfo(child, applicationId));
            } else if (AndroidManifest.NODE_SERVICE.equals(child.name())) {
                services.add(new ManifestServiceInfo(child, applicationId));
            } else if (AndroidManifest.NODE_SDK_LIBRARY.equals(child.name())) {
                sdkLibraries.add(child.attributes().get("name"));
            }
        }
    }

    @NonNull
    public static ManifestInfo parseBinaryFromStream(@NonNull InputStream inputStream)
            throws IOException {
        XmlNode node = BinaryXmlParser.parse(inputStream);
        ManifestInfo manifest = new ManifestInfo();
        manifest.parseNode(node);
        return manifest;
    }
}
