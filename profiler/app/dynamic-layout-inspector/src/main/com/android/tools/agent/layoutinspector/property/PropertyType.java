/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector.property;

import java.util.Set;
import java.util.function.IntFunction;

/** The property type as determined from inspection. */
public class PropertyType {
    private String mName;
    private int mAttributeId;
    private int mPropertyId;
    private ValueType mType;
    private IntFunction<String> mEnumMapping;
    private IntFunction<Set<String>> mFlagMapping;

    public PropertyType(String name, int attributeId, int propertyId, ValueType type) {
        mName = name;
        mAttributeId = attributeId;
        mPropertyId = propertyId;
        mType = type;
    }

    public String getName() {
        return mName;
    }

    public int getAttributeId() {
        return mAttributeId;
    }

    public int getPropertyId() {
        return mPropertyId;
    }

    public ValueType getType() {
        return mType;
    }

    public void setEnumMapping(IntFunction<String> enumMapping) {
        mEnumMapping = enumMapping;
    }

    public IntFunction<String> getEnumMapping() {
        return mEnumMapping;
    }

    public void setFlagMapping(IntFunction<Set<String>> flagMapping) {
        mFlagMapping = flagMapping;
    }

    public IntFunction<Set<String>> getFlagMapping() {
        return mFlagMapping;
    }
}
