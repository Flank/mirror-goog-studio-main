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

import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.webkit.WebView;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.TreeBuilderWrapper.InspectorNodeWrapper;
import com.android.tools.agent.layoutinspector.TreeBuilderWrapper.NodeParameterWrapper;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import com.android.tools.agent.layoutinspector.property.LayoutParamsTypeTree;
import com.android.tools.agent.layoutinspector.property.Property;
import com.android.tools.agent.layoutinspector.property.ValueType;
import com.android.tools.agent.layoutinspector.property.ViewNode;
import com.android.tools.agent.layoutinspector.property.ViewTypeTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Services for writing the properties of a View into a PropertyEvent protobuf. */
class Properties {
    private final Object stringTableLock = new Object();

    @GuardedBy("stringTableLock")
    private final StringTable mStringTable = new StringTable();

    private Map<Long, InspectorNodeWrapper> mComposeNodes;

    Properties() {
        mComposeNodes = Collections.emptyMap();
    }

    /** Keep track of the latest parameters found in the compose section. */
    void setComposeNodes(@NonNull Map<Long, InspectorNodeWrapper> nodes) {
        mComposeNodes = nodes;
    }

    /**
     * Send the properties of the specified view to the agent.
     *
     * @param viewId the id of the view or compose node to send the properties/parameters for.
     */
    void handleGetProperties(long viewId, int generation) {
        Map<Long, InspectorNodeWrapper> tempThreadSafe = mComposeNodes;
        InspectorNodeWrapper node = tempThreadSafe.get(viewId);
        if (node != null) {
            sendComposeParameters(viewId, node, generation);
            return;
        }
        View view = findViewById(viewId);
        if (view != null) {
            sendViewAttributes(view, generation);
        }
    }

    /** Send the properties of all views. */
    void saveAllViewProperties(@NonNull List<View> rootViews, int generation) {
        List<View> views = findAllViews(rootViews);
        for (View view : views) {
            sendViewAttributes(view, generation);
        }
    }

    /** Send the parameters of all compose nodes. */
    void saveAllComposeParameters(int generation) {
        Map<Long, InspectorNodeWrapper> nodes = mComposeNodes;
        mComposeNodes = Collections.emptyMap();
        for (Map.Entry<Long, InspectorNodeWrapper> entry : nodes.entrySet()) {
            sendComposeParameters(entry.getKey(), entry.getValue(), generation);
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

    @NonNull
    private List<View> findAllViews(@NonNull List<View> roots) {
        if (roots.isEmpty()) {
            return roots;
        }
        List<View> views = new ArrayList<>();
        Runnable collectAllViewsOnUIThread =
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (View root : roots) {
                                collectChildren(root, views);
                            }
                        } finally {
                            synchronized (this) {
                                notify();
                            }
                        }
                    }
                };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            collectAllViewsOnUIThread.run();
        } else {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (collectAllViewsOnUIThread) {
                roots.get(0).post(collectAllViewsOnUIThread);
                try {
                    collectAllViewsOnUIThread.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        return views;
    }

    // NotThreadSafe
    private static void collectChildren(@NonNull View view, @NonNull List<View> list) {
        list.add(view);
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int index = 0; index < count; index++) {
            collectChildren(group.getChildAt(index), list);
        }
    }

    private void sendComposeParameters(
            long viewId, @NonNull InspectorNodeWrapper node, int generation) {
        long event = allocatePropertyEvent();
        try {
            synchronized (stringTableLock) {
                mStringTable.clear();
                writeParameters(node.getParameters(), event, 0);
                writeStringTable(event);
                mStringTable.clear();
            }
            sendPropertyEvent(event, viewId, generation);
        } catch (Throwable ex) {
            LayoutInspectorService.sendErrorMessage(ex);
        } finally {
            freePropertyEvent(event);
        }
    }

    private void sendViewAttributes(View view, int generation) {
        if (view instanceof WebView) {
            // A WebView requires all property access to happen on the UI thread:

            //noinspection Convert2Lambda
            view.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            handlePropertyEvent(view, generation);
                        }
                    });
        } else {
            // All other views can use a non UI thread:
            handlePropertyEvent(view, generation);
        }
    }

    private void handlePropertyEvent(@NonNull View view, int generation) {
        long event = allocatePropertyEvent();
        try {
            synchronized (stringTableLock) {
                mStringTable.clear();
                writeProperties(view, event);
                writeStringTable(event);
                mStringTable.clear();
            }
            sendPropertyEvent(event, view.getUniqueDrawingId(), generation);
        } catch (Throwable ex) {
            LayoutInspectorService.sendErrorMessage(ex);
        } finally {
            freePropertyEvent(event);
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
        synchronized (stringTableLock) {
            for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
                addString(event, entry.getValue(), entry.getKey());
            }
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
            case "Lambda":
                return ValueType.LAMBDA;
            case "FunctionReference":
                return ValueType.FUNCTION_REFERENCE;
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
            case LAMBDA:
            case FUNCTION_REFERENCE:
                if (!(value instanceof Object[] && ((Object[]) value).length >= 1)) {
                    return 0;
                }
                return addLambdaProperty(event, property, name, type, (Object[]) value);
            default:
                return 0;
        }
    }

    private long addLambdaProperty(long event, long property, int name, int type, Object[] value) {
        Object lambdaInstance = value[0];
        String functionName = value.length >= 2 ? String.valueOf(value[1]) : null;
        Class<?> lambdaClass = lambdaInstance.getClass();
        String lambdaClassName = lambdaClass.getName();
        String packageName = substringBeforeLast(lambdaClassName, '.', "");
        String lambdaName = substringAfter(lambdaClassName, '$');
        LambdaLocation location = getLambdaLocation(lambdaClass);
        if (location == null) {
            return 0;
        }
        return addLambdaProperty(
                event,
                property,
                name,
                type,
                toInt(packageName),
                toInt(location.getFileName()),
                toInt(lambdaName),
                toInt(functionName),
                location.getStartLine(),
                location.getEndLine());
    }

    // Similar to kotlins String.substringAfter(Char)
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static String substringAfter(@NonNull String value, char delimiter) {
        int index = value.indexOf(delimiter);
        return index >= 0 ? value.substring(index + 1) : value;
    }

    // Similar to kotlins String.substringBeforeLast(Char,String)
    @SuppressWarnings("SameParameterValue")
    @NonNull
    private static String substringBeforeLast(
            @NonNull String value, char delimiter, @NonNull String missingDelimiterValue) {
        int index = value.lastIndexOf(delimiter);
        return index >= 0 ? value.substring(0, index) : missingDelimiterValue;
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
        synchronized (stringTableLock) {
            return mStringTable.generateStringId(value);
        }
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
    private native void sendPropertyEvent(long event, long viewId, int generation);

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

    /** Adds a lambda property into the event or property protobuf. */
    private native long addLambdaProperty(
            long event,
            long property,
            int name,
            int type,
            int enclosedPackageName,
            int fileName,
            int lambdaName,
            int functionName,
            int startLine,
            int endLine);

    /** Adds a resource property value into the property protobuf. */
    private native void addPropertySource(long propertyId, int namespace, int type, int name);

    /** Adds a resolution property value into the property protobuf. */
    private native void addResolution(long propertyId, int namespace, int type, int name);

    /** Adds the layout of the view as a resource. */
    private native void addLayoutResource(long event, int namespace, int type, int name);

    /** Find the lambda source location from JVMTI */
    @Nullable
    private native LambdaLocation getLambdaLocation(@NonNull Class<?> lambdaClass);
}
