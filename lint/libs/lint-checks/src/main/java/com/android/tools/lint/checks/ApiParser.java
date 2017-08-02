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

import java.util.HashMap;
import java.util.Map;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser for the simplified XML API format version 1.
 */
public class ApiParser extends DefaultHandler {
    private static final String NODE_CLASS = "class";
    private static final String NODE_FIELD = "field";
    private static final String NODE_METHOD = "method";
    private static final String NODE_EXTENDS = "extends";
    private static final String NODE_IMPLEMENTS = "implements";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_SINCE = "since";
    private static final String ATTR_DEPRECATED = "deprecated";
    private static final String ATTR_REMOVED = "removed";

    private final Map<String, ApiClass> mClasses = new HashMap<>(6000);
    private final Map<String, ApiClassOwner> mContainers = new HashMap<>();

    private ApiClass mCurrentClass;

    ApiParser() {
    }

    Map<String, ApiClass> getClasses() {
        return mClasses;
    }
    Map<String, ApiClassOwner> getContainers() { return mContainers; }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (localName == null || localName.isEmpty()) {
            localName = qName;
        }

        try {
            //noinspection StatementWithEmptyBody
            if (NODE_METHOD.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int deprecatedIn = getDeprecatedIn(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addMethod(name, since, deprecatedIn, removedIn);
            } else if (NODE_FIELD.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int deprecatedIn = getDeprecatedIn(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addField(name, since, deprecatedIn, removedIn);
            } else if (NODE_IMPLEMENTS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addInterface(name, since, removedIn);
            } else if (NODE_EXTENDS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addSuperClass(name, since, removedIn);
            } else if (NODE_CLASS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getAttributeValue(attributes, ATTR_SINCE, 1);
                int deprecatedIn = getAttributeValue(attributes, ATTR_DEPRECATED, 0);
                int removedIn = getAttributeValue(attributes, ATTR_REMOVED, 0);
                mCurrentClass = addClass(name, since, deprecatedIn, removedIn);
            }
        } finally {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    private ApiClass addClass(String name, int apiLevel, int deprecatedIn, int removedIn) {
        // There should not be any duplicates.
        ApiClass cls = mClasses.get(name);
        assert cls == null;
        cls = new ApiClass(name, apiLevel, deprecatedIn, removedIn);
        mClasses.put(name, cls);

        String containerName = cls.getContainerName();
        int len = containerName.length();
        boolean isClass = len < name.length() && name.charAt(len) == '$';
        ApiClassOwner container = mContainers.get(containerName);
        if (container == null) {
            container = new ApiClassOwner(containerName, isClass);
            mContainers.put(containerName, container);
        } else if (container.isClass() != isClass) {
            throw new RuntimeException("\"" + containerName + "\" is both a package and a class");
        }
        container.addClass(cls);

        return cls;
    }

    private int getSince(Attributes attributes) {
        return getAttributeValue(attributes, ATTR_SINCE, mCurrentClass.getSince());
    }

    private int getDeprecatedIn(Attributes attributes) {
        return getAttributeValue(attributes, ATTR_DEPRECATED, mCurrentClass.getDeprecatedIn());
    }

    private int getRemovedIn(Attributes attributes) {
        return getAttributeValue(attributes, ATTR_REMOVED, mCurrentClass.getRemovedIn());
    }

    private int getAttributeValue(Attributes attributes, String attributeName, int defaultValue) {
        String attributeValue = attributes.getValue(attributeName);
        return attributeValue == null ? defaultValue : Integer.parseInt(attributeValue);
    }
}
