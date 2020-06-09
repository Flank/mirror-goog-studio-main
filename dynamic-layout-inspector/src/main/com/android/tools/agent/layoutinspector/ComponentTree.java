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
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import java.util.Map;

/** Services for writing the view hierarchy into a ComponentTreeEvent protobuf. */
class ComponentTree {
    private static final String COMPOSE_VIEW = "androidx.ui.core.AndroidComposeView";
    private final StringTable mStringTable = new StringTable();
    private final ResourceConfiguration mConfiguration = new ResourceConfiguration(mStringTable);
    private final boolean mShowComposeNodes;
    private final Properties mProperties;
    private ComposeTree mComposeTree;

    public ComponentTree(@NonNull Properties properties, boolean showComposeNodes) {
        mProperties = properties;
        mShowComposeNodes = showComposeNodes;
    }

    /**
     * Write the component tree starting with the specified view into the event buffer.
     *
     * @param view the root of the tree to load the component tree for
     * @param event a handle to a ComponentTreeEvent protobuf to pass back in native calls
     */
    public void writeTree(long event, @NonNull View view)
            throws LayoutInspectorService.LayoutModifiedException {
        // We shouldn't come in here more than once.
        assert !mStringTable.entries().iterator().hasNext();
        loadView(event, view, null, null);
        mConfiguration.writeConfiguration(event, view);
        loadStringTable(event);
    }

    private void loadView(
            long buffer,
            @NonNull View view,
            @Nullable ViewGroup parent,
            @Nullable int[] parentLocation)
            throws LayoutInspectorService.LayoutModifiedException {
        long viewId = view.getUniqueDrawingId();
        Class<? extends View> klass = view.getClass();
        int packageName = toInt(getPackageName(klass));
        int className = toInt(klass.getSimpleName());
        int textValue = toInt(getTextValue(view));
        int[] location = new int[2];
        if (parent == null || parentLocation == null) {
            view.getLocationOnScreen(location);
        } else {
            location[0] = view.getLeft() + parentLocation[0] - parent.getScrollX();
            location[1] = view.getTop() + parentLocation[1] - parent.getScrollY();
        }
        int flags = 0;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof WindowManager.LayoutParams) {
            flags = ((WindowManager.LayoutParams) layoutParams).flags;
        }
        long viewBuffer =
                addView(
                        buffer,
                        parent != null,
                        viewId,
                        location[0],
                        location[1],
                        view.getWidth(),
                        view.getHeight(),
                        className,
                        packageName,
                        textValue,
                        flags);
        Resource id = Resource.fromResourceId(view, view.getId());
        if (id != null) {
            addIdResource(
                    viewBuffer, toInt(id.getNamespace()), toInt(id.getType()), toInt(id.getName()));
        }
        Resource layout = Resource.fromResourceId(view, view.getSourceLayoutResId());
        if (layout != null) {
            addLayoutResource(
                    viewBuffer,
                    toInt(layout.getNamespace()),
                    toInt(layout.getType()),
                    toInt(layout.getName()));
        }
        if (mShowComposeNodes && COMPOSE_VIEW.equals(klass.getCanonicalName())) {
            try {
                if (mComposeTree == null) {
                    ClassLoader classLoader = view.getClass().getClassLoader();
                    mComposeTree = new ComposeTree(classLoader, mStringTable, mProperties);
                }
                mComposeTree.loadComposeTree(view, viewBuffer);
            } catch (Throwable ex) {
                Log.w("Compose", "loadComposeTree failed: ", ex);
            }
        }
        if (viewBuffer == 0 || !(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int index = 0; index < count; index++) {
            if (group.getChildCount() != count) {
                // The tree changed. Start over.
                throw new LayoutInspectorService.LayoutModifiedException();
            }
            loadView(viewBuffer, group.getChildAt(index), group, location);
        }
    }

    private void loadStringTable(long event) {
        for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
            addString(event, entry.getValue(), entry.getKey());
        }
    }

    @Nullable
    private static String getPackageName(@NonNull Class<? extends View> cls) {
        Package pkg = cls.getPackage();
        return pkg != null ? pkg.getName() : null;
    }

    @Nullable
    private static String getTextValue(@NonNull View view) {
        if (!(view instanceof TextView)) {
            return null;
        }
        TextView text = (TextView) view;
        CharSequence sequence = text.getText();
        return sequence != null ? sequence.toString() : null;
    }

    private int toInt(@Nullable String value) {
        return mStringTable.generateStringId(value);
    }

    /** Adds a string entry into the ComponentTreeEvent protobuf. */
    private native void addString(long event, int id, @NonNull String str);

    /** Adds a view to a ComponentTreeEvent or a View protobuf */
    private native long addView(
            long event,
            boolean isSubView,
            long drawId,
            int x,
            int y,
            int width,
            int height,
            int className,
            int packageName,
            int textValue,
            int layoutFlags);

    /** Adds an id resource value to a view in either a ComponentTreeEvent or a View protobuf */
    private native void addIdResource(long viewBuffer, int namespace, int type, int name);

    /** Adds a layout resource value to a view in either a ComponentTreeEvent or a View protobuf */
    private native void addLayoutResource(long viewBuffer, int namespace, int type, int name);
}
