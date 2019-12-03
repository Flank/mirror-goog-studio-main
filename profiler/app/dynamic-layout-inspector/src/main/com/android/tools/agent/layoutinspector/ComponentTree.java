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

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/** Services for writing the view hierarchy into a ComponentTreeEvent protobuf. */
class ComponentTree {
    private final StringTable mStringTable = new StringTable();
    private final ResourceConfiguration mConfiguration = new ResourceConfiguration(mStringTable);

    /**
     * Write the component tree starting with the specified view into the event buffer.
     *
     * @param view the root of the tree to load the component tree for
     * @param event a handle to a ComponentTreeEvent protobuf to pass back in native calls
     */
    public void writeTree(long event, View view)
            throws LayoutInspectorService.LayoutModifiedException {
        // We shouldn't come in here more than once.
        assert !mStringTable.entries().iterator().hasNext();
        loadView(event, view, false);
        mConfiguration.writeConfiguration(event, view);
        loadStringTable(event);
    }

    private void loadView(long buffer, View view, boolean isSubView)
            throws LayoutInspectorService.LayoutModifiedException {
        long viewId = getUniqueDrawingId(view);
        Class<? extends View> klass = view.getClass();
        int packageName = toInt(getPackageName(klass));
        int className = toInt(klass.getSimpleName());
        int textValue = toInt(getTextValue(view));
        int[] location = new int[2];
        if (isSubView) {
            location[0] = view.getLeft();
            location[1] = view.getTop();
        } else {
            view.getLocationOnScreen(location);
        }
        int flags = 0;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof WindowManager.LayoutParams) {
            flags = ((WindowManager.LayoutParams) layoutParams).flags;
        }
        long viewBuffer =
                addView(
                        buffer,
                        isSubView,
                        viewId,
                        location[0],
                        location[1],
                        view.getScrollX(),
                        view.getScrollY(),
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
        Resource layout = Resource.fromResourceId(view, getSourceLayoutResId(view));
        if (layout != null) {
            addLayoutResource(
                    viewBuffer,
                    toInt(layout.getNamespace()),
                    toInt(layout.getType()),
                    toInt(layout.getName()));
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
            loadView(viewBuffer, group.getChildAt(index), true);
        }
    }

    private void loadStringTable(long event) {
        for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
            addString(event, entry.getValue(), entry.getKey());
        }
    }

    private long getUniqueDrawingId(View view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getUniqueDrawingId");
            Long layoutId = (Long) method.invoke(view);
            return layoutId != null ? layoutId : 0;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return 0;
        }
    }

    private int getSourceLayoutResId(View view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getSourceLayoutResId");
            Integer layoutId = (Integer) method.invoke(view);
            return layoutId != null ? layoutId : 0;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return 0;
        }
    }

    private String getPackageName(Class<? extends View> klass) {
        Package pkg = klass.getPackage();
        return pkg != null ? pkg.getName() : null;
    }

    private String getTextValue(View view) {
        if (!(view instanceof TextView)) {
            return null;
        }
        TextView text = (TextView) view;
        CharSequence sequence = text.getText();
        return sequence != null ? sequence.toString() : null;
    }

    private int toInt(String value) {
        return mStringTable.generateStringId(value);
    }

    /** Adds a string entry into the ComponentTreeEvent protobuf. */
    private native void addString(long event, int id, String str);

    /** Adds a view to a ComponentTreeEvent or a View protobuf */
    private native long addView(
            long event,
            boolean isSubView,
            long drawId,
            int x,
            int y,
            int scrollX,
            int scrollY,
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
