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
import com.android.tools.agent.layoutinspector.TreeBuilderWrapper.InspectorNodeWrapper;
import com.android.tools.agent.layoutinspector.TreeBuilderWrapper.NodeParameterWrapper;
import com.android.tools.agent.layoutinspector.common.StringTable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Services for writing a Compose hierarchy into a ComponentTreeEvent protobuf.
 */
class ComposeTree {
    private final TreeBuilderWrapper mTreeBuilder;
    private final StringTable mStringTable;
    private final Properties mProperties;
    private Map<Long, List<NodeParameterWrapper>> mPropertyMap;

    ComposeTree(
            @NonNull ClassLoader classLoader,
            @NonNull StringTable stringTable,
            @NonNull Properties properties)
            throws Exception {
        mTreeBuilder = new TreeBuilderWrapper(classLoader);
        mStringTable = stringTable;
        mProperties = properties;
    }

    /**
     * Main method.
     *
     * @param view the AndroidComposeView found in the view hierarchy
     * @param parentView the id representing the protobuf instance of the parent view
     */
    public void loadComposeTree(@NonNull View view, long parentView)
            throws ReflectiveOperationException {
        mPropertyMap = new HashMap<>();
        List<InspectorNodeWrapper> nodes = mTreeBuilder.convert(view);
        for (InspectorNodeWrapper node : nodes) {
            loadNode(node, parentView);
        }
        mProperties.setComposeParameters(mPropertyMap);
        mPropertyMap = null;
    }

    private void loadNode(@NonNull InspectorNodeWrapper node, long parentBuffer)
            throws ReflectiveOperationException {
        long buffer = writeToProtoBuf(node, parentBuffer);
        mPropertyMap.put(node.getId(), node.getParameters());
        for (InspectorNodeWrapper child : node.getChildren()) {
            loadNode(child, buffer);
        }
    }

    /** Write the node as a view node and its children to the protobuf. */
    private long writeToProtoBuf(@NonNull InspectorNodeWrapper node, long parentBuffer)
            throws ReflectiveOperationException {
        return addComposeView(
                parentBuffer,
                node.getId(),
                node.getLeft(),
                node.getTop(),
                node.getWidth(),
                node.getHeight(),
                toInt(node.getName()),
                toInt(node.getFileName()),
                node.getPackageHash(),
                node.getOffset(),
                node.getLineNumber());
    }

    private int toInt(@Nullable String value) {
        return mStringTable.generateStringId(value);
    }

    /** Adds a compose view to a View protobuf */
    private native long addComposeView(
            long parentView,
            long drawId,
            int x,
            int y,
            int width,
            int height,
            int composeClassName,
            int fileName,
            int packageHash,
            int offset,
            int lineNumber);
}
