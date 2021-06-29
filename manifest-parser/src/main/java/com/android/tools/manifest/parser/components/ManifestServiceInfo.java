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

import com.android.tools.manifest.parser.XmlNode;
import com.android.xml.AndroidManifest;

public class ManifestServiceInfo extends ManifestAppComponentInfo {
    public final boolean isolatedProcess;

    public ManifestServiceInfo(XmlNode node, String name) {
        super(node, name);
        String isolatedProcessRawValue = node.attributes().getOrDefault(AndroidManifest.ATTRIBUTE_ISOLATED_PROCESS, "");
        isolatedProcess = !isolatedProcessRawValue.isEmpty() && "true".equals(isolatedProcessRawValue);
    }
}
