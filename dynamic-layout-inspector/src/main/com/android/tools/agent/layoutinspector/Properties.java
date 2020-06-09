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

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.TreeBuilderWrapper.NodeParameterWrapper;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import com.android.tools.agent.layoutinspector.property.LayoutParamsTypeTree;
import com.android.tools.agent.layoutinspector.property.Property;
import com.android.tools.agent.layoutinspector.property.ValueType;
import com.android.tools.agent.layoutinspector.property.ViewNode;
import com.android.tools.agent.layoutinspector.property.ViewTypeTree;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Services for writing the properties of a View into a PropertyEvent protobuf. */
class Properties {
    private final StringTable mStringTable = new StringTable();
    private final Object parameterSync = new Object();
    private Map<Long, List<NodeParameterWrapper>> mComposeParameters;

    Properties() {
        mComposeParameters = Collections.emptyMap();
    }

    /** Keep track of the latest parameters found in the compose section. */
    void setComposeParameters(@NonNull Map<Long, List<NodeParameterWrapper>> parameters) {
        synchronized (parameterSync) {
            mComposeParameters = parameters;
        }
    }

    /**
     * Send the properties of the specified view to the agent.
     *
     * @param viewId the id of the view or compose node to send the properties/parameters for.
     */
    void handleGetProperties(long viewId) {
        List<NodeParameterWrapper> parameters;
        synchronized (parameterSync) {
            parameters = mComposeParameters.get(viewId);
        }
        if (parameters != null) {
            sendComposeParameters(viewId, parameters);
            return;
        }
        View view = findViewById(viewId);
        if (view != null) {
            sendViewAttributes(view);
        }
    }

    /** Set the attribute to the value on the specified view. */
    void handleSetProperty(long viewId, int attributeId, int value) {
        View view = findViewById(viewId);
        if (view != null) {
            applyPropertyEdit(view, attributeId, value);
        }
    }

    @Nullable
    private static View findViewById(long viewId) {
        List<View> roots = WindowInspector.getGlobalWindowViews();
        for (View root : roots) {
            View view = findViewById(root, viewId);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    @Nullable
    private static View findViewById(@Nullable View parent, long id) {
        if (parent != null && parent.getUniqueDrawingId() == id) {
            return parent;
        }
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) parent;
        int count = group.getChildCount();
        for (int index = 0; index < count; index++) {
            View found = findViewById(group.getChildAt(index), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void sendComposeParameters(
            long viewId, @NonNull List<NodeParameterWrapper> parameters) {
        mStringTable.clear();
        long event = allocatePropertyEvent();
        try {
            writeParameters(parameters, event, 0);
            writeStringTable(event);
            sendPropertyEvent(event, viewId);
        } catch (Throwable ex) {
            LayoutInspectorService.sendErrorMessage(ex);
        } finally {
            freePropertyEvent(event);
            mStringTable.clear();
        }
    }

    private void sendViewAttributes(View view) {
        if (view instanceof WebView) {
            // A WebView requires all property access to happen on the UI thread:

            //noinspection Convert2Lambda
            view.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            handlePropertyEvent(view);
                        }
                    });
        } else {
            // All other views can use a non UI thread:
            handlePropertyEvent(view);
        }
    }

    private void handlePropertyEvent(@NonNull View view) {
        mStringTable.clear();
        long event = allocatePropertyEvent();
        try {
            writeProperties(view, event);
            writeStringTable(event);
            sendPropertyEvent(event, view.getUniqueDrawingId());
        } catch (Throwable ex) {
            LayoutInspectorService.sendErrorMessage(ex);
        } finally {
            freePropertyEvent(event);
            mStringTable.clear();
        }
    }

    private void writeParameters(
            @NonNull List<NodeParameterWrapper> parameters, long event, long property)
            throws ReflectiveOperationException {
        for (NodeParameterWrapper parameter : parameters) {
            long buffer = addParameter(parameter, event, property);
            if (buffer != 0) {
                writeParameters(parameter.getElements(), 0, buffer);
            }
        }
    }

    private void writeStringTable(long event) {
        for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
            addString(event, entry.getValue(), entry.getKey());
        }
    }

    private long addParameter(@NonNull NodeParameterWrapper parameter, long event, long property)
            throws ReflectiveOperationException {
        ValueType valueType = typeOf(parameter.getType());
        if (valueType == ValueType.OBJECT) {
            return 0L;
        }
        return addProperty(
                event, property, parameter.getName(), valueType, parameter.getValue(), false);
    }

    @NonNull
    private static ValueType typeOf(@NonNull String type) {
        switch (type) {
            case "String":
                return ValueType.STRING;
            case "Boolean":
                return ValueType.BOOLEAN;
            case "Double":
                return ValueType.DOUBLE;
            case "Float":
                return ValueType.FLOAT;
            case "Int32":
                return ValueType.INT32;
            case "Int64":
                return ValueType.INT64;
            case "Color":
                return ValueType.COLOR;
            case "Resource":
                return ValueType.RESOURCE;
            case "DimensionDp":
                return ValueType.DIMENSION_DP;
            case "DimensionSp":
                return ValueType.DIMENSION_SP;
            case "DimensionEm":
                return ValueType.DIMENSION_EM;
            default:
                Log.w("Compose", "Could not map this type: " + type);
                return ValueType.OBJECT;
        }
    }

    private void writeProperties(@NonNull View view, long event) {
        ViewTypeTree typeTree = new ViewTypeTree();
        LayoutParamsTypeTree layoutTypeTree = new LayoutParamsTypeTree();
        ViewNode<View> node =
                new ViewNode<>(
                        typeTree.typeOf(view), layoutTypeTree.typeOf(view.getLayoutParams()));
        node.readProperties(view);
        Resource layout = node.getLayoutResource(view);
        if (layout != null) {
            addLayoutResource(
                    event,
                    toInt(layout.getNamespace()),
                    toInt(layout.getType()),
                    toInt(layout.getName()));
        }

        for (Property property : node.getViewProperties()) {
            addPropertyAndSourceResolutionStack(event, property, false);
        }

        for (Property property : node.getLayoutProperties()) {
            addPropertyAndSourceResolutionStack(event, property, true);
        }
    }

    private void addPropertyAndSourceResolutionStack(
            long event, @NonNull Property property, boolean isLayout) {
        long propertyId = addProperty(event, property, isLayout);
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

    private long addProperty(long event, @NonNull Property property, boolean isLayout) {
        String name = property.getPropertyType().getName();
        return addProperty(event, 0, name, property.getValueType(), property.getValue(), isLayout);
    }

    private long addProperty(
            long event,
            long property,
            @NonNull String propertyName,
            @NonNull ValueType valueType,
            @Nullable Object value,
            boolean isLayout) {
        if (value == null) {
            return 0;
        }
        int name = toInt(propertyName);
        int type = valueType.ordinal();
        switch (valueType) {
            case STRING:
            case INT_ENUM:
                return addIntProperty(event, property, name, isLayout, type, toInt((String) value));
            case INT32:
            case INT16:
            case BYTE:
            case CHAR:
            case COLOR:
            case DIMENSION:
                return addIntProperty(event, property, name, isLayout, type, (int) value);
            case BOOLEAN:
                return addIntProperty(
                        event, property, name, isLayout, type, value == Boolean.TRUE ? 1 : 0);
            case GRAVITY:
            case INT_FLAG:
                //noinspection unchecked
                return addIntFlagProperty(
                        event, property, name, isLayout, type, (Set<String>) value);
            case INT64:
                return addLongProperty(event, property, name, isLayout, type, (long) value);
            case DOUBLE:
                return addDoubleProperty(event, property, name, isLayout, type, (double) value);
            case FLOAT:
            case DIMENSION_FLOAT:
            case DIMENSION_DP:
            case DIMENSION_SP:
            case DIMENSION_EM:
                return addFloatProperty(event, property, name, isLayout, type, (float) value);
            case RESOURCE:
                Resource resource = findResource(value);
                if (resource == null) {
                    return 0;
                }
                return addResourceProperty(
                        event,
                        property,
                        name,
                        isLayout,
                        type,
                        toInt(resource.getNamespace()),
                        toInt(resource.getType()),
                        toInt(resource.getName()));
            case DRAWABLE:
            case ANIM:
            case ANIMATOR:
            case INTERPOLATOR:
                return addIntProperty(
                        event, property, name, isLayout, type, toInt(value.getClass().getName()));
            default:
                return 0;
        }
    }

    @Nullable
    private static Resource findResource(@NonNull Object value) {
        if (value instanceof Resource) {
            return (Resource) value;
        }
        if (!(value instanceof Integer)) {
            return null;
        }
        // A Resource is passed by resource id for Compose:
        int resId = (int) value;
        List<View> roots = WindowInspector.getGlobalWindowViews();
        if (roots.isEmpty()) {
            return null;
        }
        return Resource.fromResourceId(roots.get(0), resId);
    }

    private int toInt(@Nullable String value) {
        return mStringTable.generateStringId(value);
    }

    private long addIntFlagProperty(
            long event,
            long property,
            int name,
            boolean isLayout,
            int type,
            @NonNull Set<String> value) {
        long propertyEvent = addFlagProperty(event, property, name, isLayout, type);
        for (String flag : value) {
            addFlagPropertyValue(propertyEvent, toInt(flag));
        }
        return propertyEvent;
    }

    private static void applyPropertyEdit(@NonNull View view, int attributeId, int value) {
        switch (attributeId) {
            case android.R.attr.padding:
                view.setPadding(value, value, value, value);
                break;
            case android.R.attr.paddingLeft:
                view.setPadding(
                        value,
                        view.getPaddingTop(),
                        view.getPaddingRight(),
                        view.getPaddingBottom());
                break;
            case android.R.attr.paddingTop:
                view.setPadding(
                        view.getPaddingLeft(),
                        value,
                        view.getPaddingRight(),
                        view.getPaddingBottom());
                break;
            case android.R.attr.paddingRight:
                view.setPadding(
                        view.getPaddingLeft(),
                        view.getPaddingTop(),
                        value,
                        view.getPaddingBottom());
                break;
            case android.R.attr.paddingBottom:
                view.setPadding(
                        view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), value);
                break;
            default:
                LayoutInspectorService.sendErrorMessage(
                        "Unsupported attribute for editing: " + Integer.toHexString(attributeId));
        }
    }

    /** Allocates a PropertyEvent protobuf. */
    private native long allocatePropertyEvent();

    /** Frees a PropertyEvent protobuf. */
    private native void freePropertyEvent(long event);

    /** Sends a property event to Android Studio */
    private native void sendPropertyEvent(long event, long viewId);

    /** Adds a string entry into the event protobuf. */
    private native void addString(long event, int id, @NonNull String str);

    /** Adds an int32 property value into the event or property protobuf. */
    private native long addIntProperty(
            long event, long property, int name, boolean isLayout, int type, int value);

    /** Adds an int64 property value into the event or property protobuf. */
    private native long addLongProperty(
            long event, long property, int name, boolean isLayout, int type, long value);

    /** Adds a double property value into the event or property protobuf. */
    private native long addDoubleProperty(
            long event, long property, int name, boolean isLayout, int type, double value);

    /** Adds a float property value into the event or property protobuf. */
    private native long addFloatProperty(
            long event, long property, int name, boolean isLayout, int type, float value);

    /** Adds a resource property value into the event or property protobuf. */
    private native long addResourceProperty(
            long event,
            long property,
            int name,
            boolean isLayout,
            int type,
            int res_namespace,
            int res_type,
            int res_name);

    /** Adds a flag property into the event or property protobuf. */
    private native long addFlagProperty(
            long event, long property, int name, boolean isLayout, int type);

    /** Adds a flag property value into the flag property protobuf. */
    private native void addFlagPropertyValue(long property, int flag);

    /** Adds a resource property value into the property protobuf. */
    private native void addPropertySource(long propertyId, int namespace, int type, int name);

    /** Adds a resolution property value into the property protobuf. */
    private native void addResolution(long propertyId, int namespace, int type, int name);

    /** Adds the layout of the view as a resource. */
    private native void addLayoutResource(long event, int namespace, int type, int name);
}
