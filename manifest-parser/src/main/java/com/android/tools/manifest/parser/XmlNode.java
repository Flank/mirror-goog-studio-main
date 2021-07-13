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
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceValue;
import com.google.devrel.gmscore.tools.apk.arsc.XmlAttribute;
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple representation of an xml code (name, attributes and child nodes) used when decoding an xml
 * document in binary resource format.
 */
public class XmlNode {

    private final String name;

    private final List<XmlNode> children = new ArrayList<>();

    private final Map<String, String> attributes = new HashMap<>();

    public XmlNode() {
        name = "";
    }

    public XmlNode(XmlStartElementChunk chunk, String chunkName) {
        name = chunkName;
        // TODO: Instead of a Map<String, String>, we should have it being a Map<String, Value>.
        for (XmlAttribute attribute : chunk.getAttributes()) {
            String name = attribute.name();
            String value;
            BinaryResourceValue typeValue = attribute.typedValue();
            if (typeValue.type() == BinaryResourceValue.Type.INT_BOOLEAN) {
                value = typeValue.data() == 0 ? "false" : "true";
            } else if (typeValue.type() == BinaryResourceValue.Type.INT_DEC) {
                value = String.valueOf(typeValue.data());
            } else {
                value = attribute.rawValue();
            }
            attributes.put(name, value);
        }
    }

    @NonNull
    public String name() {
        return name;
    }

    @NonNull
    public List<XmlNode> children() {
        return children;
    }

    @NonNull
    public Map<String, String> attributes() {
        return attributes;
    }
}
