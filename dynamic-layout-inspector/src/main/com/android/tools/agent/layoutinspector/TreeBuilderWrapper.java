/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for LayoutInspectorTree & InspectorNode from the ui-tooling library.
 *
 * <p>This class is using Java reflection to get access to tooling API for Compose. Note that this
 * class is loaded by the boot class loader of the agent where the ui-tooling classes are loaded by
 * the application class loader. As such we do NOT have access to the ui-tooling classes other than
 * with reflection.
 */
class TreeBuilderWrapper {
    private final Object mInstance;
    private final Method mConvert;
    private final Method mGetId;
    private final Method mGetName;
    private final Method mGetFileName;
    private final Method mGetPackageHash;
    private final Method mGetLineNumber;
    private final Method mGetOffset;
    private final Method mGetLeft;
    private final Method mGetTop;
    private final Method mGetWidth;
    private final Method mGetHeight;
    private final Method mGetChildren;
    private final Method mGetParameters;
    private final Method mGetParamName;
    private final Method mGetParamType;
    private final Method mGetParamValue;
    private final Method mGetParamElements;

    TreeBuilderWrapper(@NonNull ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> builderClass =
                classLoader.loadClass("androidx.ui.tooling.inspector.LayoutInspectorTree");
        Class<?> nodeClass = classLoader.loadClass("androidx.ui.tooling.inspector.InspectorNode");
        Class<?> paramClass = classLoader.loadClass("androidx.ui.tooling.inspector.NodeParameter");
        Class<?> viewClass = classLoader.loadClass("android.view.View");
        mInstance = builderClass.newInstance();
        mConvert = builderClass.getDeclaredMethod("convert", viewClass);
        mGetId = nodeClass.getDeclaredMethod("getId");
        mGetName = nodeClass.getDeclaredMethod("getName");
        mGetFileName = nodeClass.getDeclaredMethod("getFileName");
        mGetPackageHash = nodeClass.getDeclaredMethod("getPackageHash");
        mGetLineNumber = nodeClass.getDeclaredMethod("getLineNumber");
        mGetOffset = nodeClass.getDeclaredMethod("getOffset");
        mGetLeft = nodeClass.getDeclaredMethod("getLeft");
        mGetTop = nodeClass.getDeclaredMethod("getTop");
        mGetWidth = nodeClass.getDeclaredMethod("getWidth");
        mGetHeight = nodeClass.getDeclaredMethod("getHeight");
        mGetChildren = nodeClass.getDeclaredMethod("getChildren");
        mGetParameters = nodeClass.getDeclaredMethod("getParameters");
        mGetParamName = paramClass.getDeclaredMethod("getName");
        mGetParamType = paramClass.getDeclaredMethod("getType");
        mGetParamValue = paramClass.getDeclaredMethod("getValue");
        mGetParamElements = paramClass.getDeclaredMethod("getElements");
    }

    /** See documentation for androidx.compose.tooling.inspector.LayoutInspectorTree */
    public List<InspectorNodeWrapper> convert(@NonNull View view)
            throws ReflectiveOperationException {
        //noinspection unchecked
        return wrap((List<Object>) mConvert.invoke(mInstance, view));
    }

    private List<InspectorNodeWrapper> wrap(@NonNull List<Object> nodes) {
        List<InspectorNodeWrapper> views = new ArrayList<>();
        for (Object node : nodes) {
            views.add(new InspectorNodeWrapper(node));
        }
        return views;
    }

    private List<NodeParameterWrapper> wrapParameters(@NonNull List<Object> composeParameters) {
        List<NodeParameterWrapper> parameters = new ArrayList<>();
        for (Object parameter : composeParameters) {
            parameters.add(new NodeParameterWrapper(parameter));
        }
        return parameters;
    }

    /** See documentation for androidx.compose.tooling.inspector.InspectorNode */
    class InspectorNodeWrapper {
        private final Object mInstance;

        InspectorNodeWrapper(@NonNull Object node) {
            mInstance = node;
        }

        public long getId() throws ReflectiveOperationException {
            return (long) mGetId.invoke(mInstance);
        }

        @NonNull
        public String getName() throws ReflectiveOperationException {
            return (String) mGetName.invoke(mInstance);
        }

        @NonNull
        public String getFileName() throws ReflectiveOperationException {
            return (String) mGetFileName.invoke(mInstance);
        }

        public int getPackageHash() throws ReflectiveOperationException {
            return (int) mGetPackageHash.invoke(mInstance);
        }

        public int getLineNumber() throws ReflectiveOperationException {
            return (int) mGetLineNumber.invoke(mInstance);
        }

        public int getOffset() throws ReflectiveOperationException {
            return (int) mGetOffset.invoke(mInstance);
        }

        public int getLeft() throws ReflectiveOperationException {
            return (int) mGetLeft.invoke(mInstance);
        }

        public int getTop() throws ReflectiveOperationException {
            return (int) mGetTop.invoke(mInstance);
        }

        public int getWidth() throws ReflectiveOperationException {
            return (int) mGetWidth.invoke(mInstance);
        }

        public int getHeight() throws ReflectiveOperationException {
            return (int) mGetHeight.invoke(mInstance);
        }

        @NonNull
        public List<NodeParameterWrapper> getParameters() throws ReflectiveOperationException {
            //noinspection unchecked
            return wrapParameters((List<Object>) mGetParameters.invoke(mInstance));
        }

        @NonNull
        public List<InspectorNodeWrapper> getChildren() throws ReflectiveOperationException {
            //noinspection unchecked
            return wrap((List<Object>) mGetChildren.invoke(mInstance));
        }
    }

    /** See documentation for androidx.compose.tooling.inspector.NodeParameterWrapper */
    public class NodeParameterWrapper {
        private final Object mInstance;

        NodeParameterWrapper(@NonNull Object parameter) {
            mInstance = parameter;
        }

        @NonNull
        public String getName() throws ReflectiveOperationException {
            return (String) mGetParamName.invoke(mInstance);
        }

        @NonNull
        public String getType() throws ReflectiveOperationException {
            Enum<?> type = (Enum<?>) mGetParamType.invoke(mInstance);
            if (type == null) {
                return "OBJECT";
            }
            return type.name();
        }

        @Nullable
        public Object getValue() throws ReflectiveOperationException {
            return mGetParamValue.invoke(mInstance);
        }

        @NonNull
        public List<NodeParameterWrapper> getElements() throws ReflectiveOperationException {
            //noinspection unchecked
            return wrapParameters((List<Object>) mGetParamElements.invoke(mInstance));
        }
    }
}
