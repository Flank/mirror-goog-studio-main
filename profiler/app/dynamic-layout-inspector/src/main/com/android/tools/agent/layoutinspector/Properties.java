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

package com.android.tools.agent.layoutinspector;

import android.graphics.drawable.Drawable;
import android.view.View;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import com.android.tools.agent.layoutinspector.property.Property;
import com.android.tools.agent.layoutinspector.property.PropertyType;
import com.android.tools.agent.layoutinspector.property.ValueType;
import com.android.tools.agent.layoutinspector.property.ViewNode;
import com.android.tools.agent.layoutinspector.property.ViewTypeTree;
import java.util.Map;
import java.util.Set;

/** Services for writing the properties of a View into a PropertyEvent protobuf. */
class Properties {
    private final StringTable mStringTable = new StringTable();

    /**
     * Write the properties of the specified view into the specified properties event buffer.
     *
     * @param view the view to load the properties for
     * @param event a handle to a PropertyEvent protobuf to pass back in native calls
     */
    void writeProperties(View view, long event) {
        mStringTable.clear();
        ViewTypeTree typeTree = new ViewTypeTree();
        ViewNode<View> node = typeTree.nodeOf(view);
        node.readProperties(view);
        Resource layout = node.getLayoutResource(view);
        if (layout != null) {
            addLayoutResource(
                    event,
                    toInt(layout.getNamespace()),
                    toInt(layout.getType()),
                    toInt(layout.getName()));
        }

        for (Property property : node.getProperties()) {
            long propertyId = addProperty(event, property);
            Resource source = property.getSource();
            if (propertyId != 0) {
                if (source != null) {
                    addPropertySource(
                            propertyId,
                            toInt(source.getNamespace()),
                            toInt(source.getType()),
                            toInt(source.getName()));
                }
                for (Resource resolution : property.getResolutionStack()) {
                    addResolution(
                            propertyId,
                            toInt(resolution.getNamespace()),
                            toInt(resolution.getType()),
                            toInt(resolution.getName()));
                }
            }
        }

        for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
            addString(event, entry.getValue(), entry.getKey());
        }

        mStringTable.clear();
    }

    private long addProperty(long event, Property property) {
        PropertyType propertyType = property.getPropertyType();
        ValueType valueType = property.getValueType();
        int name = toInt(propertyType.getName());
        int type = valueType.ordinal();
        Object value = property.getValue();
        if (value == null) {
            return 0;
        }
        switch (valueType) {
            case STRING:
            case INT_ENUM:
                return addIntProperty(event, name, type, toInt((String) value));
            case INT32:
            case INT16:
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case COLOR:
                return addIntProperty(event, name, type, (int) value);
            case GRAVITY:
            case INT_FLAG:
                return addIntFlagProperty(event, name, type, (Set<String>) value);
            case INT64:
                return addLongProperty(event, name, type, (long) value);
            case DOUBLE:
                return addDoubleProperty(event, name, type, (double) value);
            case FLOAT:
                return addFloatProperty(event, name, type, (float) value);
            case RESOURCE:
                Resource resource = (Resource) value;
                return addResourceProperty(
                        event,
                        name,
                        type,
                        toInt(resource.getNamespace()),
                        toInt(resource.getType()),
                        toInt(resource.getName()));
            case DRAWABLE:
            case ANIM:
            case ANIMATOR:
            case INTERPOLATOR:
                return addIntProperty(event, name, type, toInt(value.getClass().getName()));
            default:
                return 0;
        }
    }

    private int toInt(String value) {
        return mStringTable.generateStringId(value);
    }

    private long addIntFlagProperty(long event, int name, int type, Set<String> value) {
        long propertyEvent = addFlagProperty(event, name, type);
        for (String flag : value) {
            addFlagPropertyValue(propertyEvent, toInt(flag));
        }
        return propertyEvent;
    }

    /** Adds a string entry into the event protobuf. */
    private native void addString(long event, int id, String str);

    /** Adds an int32 property value into the event protobuf. */
    private native long addIntProperty(long event, int name, int type, int value);

    /** Adds an int64 property value into the event protobuf. */
    private native long addLongProperty(long event, int name, int type, long value);

    /** Adds a double property value into the event protobuf. */
    private native long addDoubleProperty(long event, int name, int type, double value);

    /** Adds a float property value into the event protobuf. */
    private native long addFloatProperty(long event, int name, int type, float value);

    /** Adds a resource property value into the event protobuf. */
    private native long addResourceProperty(
            long event, int name, int type, int res_namespace, int res_type, int res_name);

    /** Adds a flag property into the event protobuf. */
    private native long addFlagProperty(long event, int name, int type);

    /** Adds a flag property value into the flag property protobuf. */
    private native void addFlagPropertyValue(long property, int flag);

    /** Adds a resource property value into the property protobuf. */
    private native void addPropertySource(long propertyId, int namespace, int type, int name);

    /** Adds a resolution property value into the property protobuf. */
    private native void addResolution(long propertyId, int namespace, int type, int name);

    /** Adds the layout of the view as a resource. */
    private native void addLayoutResource(long event, int namespace, int type, int name);
}
