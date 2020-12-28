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

import android.graphics.Matrix;
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
    private static final String COMPOSE_VIEW = "androidx.compose.ui.platform.AndroidComposeView";
    private final StringTable mStringTable = new StringTable();
    private final ResourceConfiguration mConfiguration = new ResourceConfiguration(mStringTable);
    private final Properties mProperties;
    private final Object mComposeLock = new Object();
    private boolean mShowComposeNodes;
    private ComposeTree mComposeTree;

    public ComponentTree(@NonNull Properties properties) {
        mProperties = properties;
    }

    public void setShowComposeNodes(boolean showComposeNodes) {
        mShowComposeNodes = showComposeNodes;
    }

    public boolean showComposeNodes() {
        return mShowComposeNodes;
    }

    /**
     * Write the component tree starting with the specified view into the event buffer.
     *
     * @param view the root of the tree to load the component tree for
     * @param event a handle to a ComponentTreeEvent protobuf to pass back in native calls
     */
    public synchronized void writeTree(long event, @NonNull View view)
            throws LayoutInspectorService.LayoutModifiedException {
        mStringTable.clear();
        if (mComposeTree != null) {
            mComposeTree.resetGeneratedId();
        }
        loadView(event, view, null, null);
        mConfiguration.writeConfiguration(event, view);
        loadStringTable(event);
        if (mComposeTree != null) {
            mComposeTree.saveNodeParameters(mProperties);
        }
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
        int[] untransformedLocation = new int[4];
        untransformedLocation[2] = view.getWidth();
        untransformedLocation[3] = view.getHeight();
        int[] transformedCorners = null;
        if (parent == null || parentLocation == null) {
            view.getLocationOnScreen(untransformedLocation);
        } else {
            Matrix transform = new Matrix();
            view.transformMatrixToGlobal(transform);
            if (transform.isIdentity()) {
                view.getLocationOnScreen(untransformedLocation);
            } else {
                float[] corners = new float[8];
                corners[0] = 0;
                corners[1] = 0;
                corners[2] = view.getWidth();
                corners[3] = 0;
                corners[4] = 0;
                corners[5] = view.getHeight();
                corners[6] = view.getWidth();
                corners[7] = view.getHeight();
                transform.mapPoints(corners);
                transformedCorners = new int[8];
                for (int i = 0; i < 8; i += 2) {
                    transformedCorners[i] = Math.round(corners[i]);
                    transformedCorners[i + 1] = Math.round(corners[i + 1]);
                }

                untransformedLocation[0] = view.getLeft() + parentLocation[0] - parent.getScrollX();
                untransformedLocation[1] = view.getTop() + parentLocation[1] - parent.getScrollY();
            }
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
                        untransformedLocation,
                        transformedCorners,
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
            if (mComposeTree == null) {
                try {
                    ClassLoader classLoader = view.getClass().getClassLoader();
                    mComposeTree = new ComposeTree(classLoader, mStringTable);
                } catch (Throwable ex) {
                    Log.w("Compose", "ComposeTree creation failed: ", ex);
                }
            }
            if (mComposeTree != null) {
                synchronized (mComposeLock) {
                    view.post(
                            () -> {
                                try {
                                    mComposeTree.loadComposeTree(view, viewBuffer);
                                } catch (Throwable ex) {
                                    Log.w("Compose", "loadComposeTree failed: ", ex);
                                } finally {
                                    synchronized (mComposeLock) {
                                        mComposeLock.notify();
                                    }
                                }
                            });
                    try {
                        mComposeLock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
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
            loadView(viewBuffer, group.getChildAt(index), group, untransformedLocation);
        }
    }

    private void loadStringTable(long event) {
        for (Map.Entry<String, Integer> entry : mStringTable.entries()) {
            addString(event, entry.getValue(), entry.getKey());
        }
        mStringTable.clear();
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
            int[] untransformedBounds,
            int[] transformedCorners,
            int className,
            int packageName,
            int textValue,
            int layoutFlags);

    /** Adds an id resource value to a view in either a ComponentTreeEvent or a View protobuf */
    private native void addIdResource(long viewBuffer, int namespace, int type, int name);

    /** Adds a layout resource value to a view in either a ComponentTreeEvent or a View protobuf */
    private native void addLayoutResource(long viewBuffer, int namespace, int type, int name);
}
