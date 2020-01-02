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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.common.Resource;
import java.util.ArrayList;
import java.util.List;

/** Representation of a Property including name, type, and value. */
public class Property {
    private PropertyType mType;
    private Object mValue;
    private ValueType mValueType;
    private Resource mSource;
    private final List<Resource> mResolutionStack;

    public Property(@NonNull PropertyType type) {
        mType = type;
        mValueType = type.getType();
        mResolutionStack = new ArrayList<>();
    }

    @NonNull
    public PropertyType getPropertyType() {
        return mType;
    }

    /**
     * The value of the property.
     *
     * <p>The type of the value depends on the value type which is stored separately. Certain values
     * are encoded e.g. a string is stored as an Integer which represent an id in the string table
     * that is generated along with the properties.
     */
    @Nullable
    public Object getValue() {
        return mValue;
    }

    @NonNull
    public ValueType getValueType() {
        return mValueType;
    }

    public void setType(@NonNull ValueType type) {
        mValueType = type;
    }

    public void setValue(@Nullable Object value) {
        mValue = value;
    }

    /**
     * The source location where the value was set.
     *
     * <p>This can be a layout file or from a style.
     */
    @Nullable
    public Resource getSource() {
        return mSource;
    }

    public void setSource(@Nullable Resource source) {
        mSource = source;
    }

    /** Get the resolution stack for this property. */
    @NonNull
    public List<Resource> getResolutionStack() {
        return mResolutionStack;
    }
}
