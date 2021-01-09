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
    private Map<Long, InspectorNodeWrapper> mNodeMap;

    ComposeTree(@NonNull ClassLoader classLoader, @NonNull StringTable stringTable)
            throws ReflectiveOperationException {
        mTreeBuilder = new TreeBuilderWrapper(classLoader);
        mStringTable = stringTable;
    }

    /**
     * Main method.
     *
     * @param view the AndroidComposeView found in the view hierarchy
     * @param parentView the id representing the protobuf instance of the parent view
     */
    public void loadComposeTree(@NonNull View view, long parentView)
            throws ReflectiveOperationException {
        if (mNodeMap == null) {
            mNodeMap = new HashMap<>();
        }
        List<InspectorNodeWrapper> nodes = mTreeBuilder.convert(view);
        for (InspectorNodeWrapper node : nodes) {
            loadNode(node, parentView);
        }
    }

    public void setHideSystemNodes(boolean hideSystemNodes) {
        try {
            mTreeBuilder.setHideSystemNodes(hideSystemNodes);
        } catch (ReflectiveOperationException ignore) {
            // ignore
        }
    }

    public void resetGeneratedId() {
        try {
            mTreeBuilder.resetGeneratedId();
        } catch (ReflectiveOperationException ignore) {
            // ignore
        }
    }

    public void saveNodeParameters(@NonNull Properties properties) {
        properties.setComposeNodes(mNodeMap);
        mNodeMap = null;
    }

    private void loadNode(@NonNull InspectorNodeWrapper node, long parentBuffer)
            throws ReflectiveOperationException {
        long buffer = writeToProtoBuf(node, parentBuffer);
        mNodeMap.put(node.getId(), node);
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
                node.getLineNumber(),
                node.getBounds());
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
            int lineNumber,
            int[] transformedCorners);
}
