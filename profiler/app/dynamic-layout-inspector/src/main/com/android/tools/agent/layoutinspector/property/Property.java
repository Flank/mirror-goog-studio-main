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

import com.android.tools.agent.layoutinspector.common.Resource;

/** Representation of a Property including name, type, and value. */
public class Property {
    private PropertyType mType;
    private Object mValue;
    private ValueType mValueType;
    private Resource mSource;

    public Property(PropertyType type) {
        mType = type;
        mValueType = type.getType();
    }

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
    public Object getValue() {
        return mValue;
    }

    public ValueType getValueType() {
        return mValueType;
    }

    public void setType(ValueType type) {
        mValueType = type;
    }

    public void setValue(Object value) {
        mValue = value;
    }

    /**
     * The source location where the value was set.
     *
     * <p>This can be a layout file or from a style.
     */
    public Resource getSource() {
        return mSource;
    }

    public void setSource(Resource source) {
        mSource = source;
    }
}
