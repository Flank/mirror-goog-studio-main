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

    private String applicationId;

    private ManifestInfo() {
        activities = new ArrayList<>();
        services = new ArrayList<>();
        applicationId = "";
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

    private void parseNode(@NonNull XmlNode node) {
        for (String attribute : node.attributes().keySet()) {
            String value = node.attributes().get(attribute);
            if (AndroidManifest.ATTRIBUTE_PACKAGE.equals(attribute)) {
                applicationId = value;
            }
        }

        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_APPLICATION.equals(child.name())) {
                parseApplication(child);
            }
        }
    }

    private void parseApplication(@NonNull XmlNode node) {
        for (XmlNode child : node.children()) {
            if (AndroidManifest.NODE_ACTIVITY.equals(child.name()) ||
                AndroidManifest.NODE_ACTIVITY_ALIAS.equals(child.name())) {
                activities.add(new ManifestActivityInfo(child, applicationId));
            } else if (AndroidManifest.NODE_SERVICE.equals(child.name())) {
                services.add(new ManifestServiceInfo(child, applicationId));
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
