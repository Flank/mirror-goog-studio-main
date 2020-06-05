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

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.android.tools.agent.layoutinspector.common.StringTable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Services for writing a Compose hierarchy into a ComponentTreeEvent protobuf.
 *
 * <p>This class is using Java reflection to get access to tooling API for Compose. Note that the
 * compose tooling classes must be loaded in the same class loader as the view tree. The class
 * loader for this class will NOT work.
 *
 * <p>The DEBUG flag controls the output that is generated from this class:
 *
 * <ul>
 *   <li>the tree of Group nodes read from the Compose tooling API
 *   <li>a tree of {@link ComposeView} nodes with all the non essential nodes eliminated
 *   <li>the tree of {@link ComposeView} nodes written to the protobuf
 * </ul>
 *
 * <p>These outputs may be used to generate a new test. See testData/compose.
 */
class ComposeTree {
    /**
     * A pattern for matching Group positions found in the Compose tooling API.
     *
     * <p>The Group.position() extension method will return strings like:
     * "androidx.compose.Composer-Ah7q.startExpr (Composer.kt:620)"
     *
     * <p>From this format we would like to extract:
     *
     * <ul>
     *   <li>The qualified function name: "androidx.compose.Composer.startExpr" (group 1)
     *   <li>The inline differential part: "-Ah7q" (group 2)
     *   <li>The file where it is found: "Composer.kt" (group 3)
     *   <li>The line number where it was found: 620 (group 4)
     * </ul>
     */
    private static final String POSITION_REGEX =
            "([\\w\\\\.$]*?)(-\\w*)?\\s?\\(?([\\w.]+):(\\d+)\\)?";

    private static final Pattern POSITION_PATTERN = Pattern.compile(POSITION_REGEX);
    private static final int POSITION_GROUP_FUNCTION_NAME = 1;
    //  private static final int POSITION_GROUP_INLINE_DIFF = 2; // Not used...
    private static final int POSITION_GROUP_FILENAME = 3;
    private static final int POSITION_GROUP_LINE_NUMBER = 4;

    private static final Map<String, String> ignoredFunctions = createIgnoredComposeFunctions();
    private static final String INVOKE = ".invoke";
    private static final String LOG_TAG = "Compose";
    private static final ComposeView.Source EMPTY_SOURCE = new ComposeView.Source("", "", -1);
    private static final Rect EMPTY_RECT = new Rect();

    @VisibleForTesting protected static final boolean DEBUG_COMPOSE = false;

    private final Field mSlotTableSetKey;
    private final Method mGetTables;
    private final Method mAsTree;
    private final Method mGetBox;
    private final Method mGetLeft;
    private final Method mGetRight;
    private final Method mGetTop;
    private final Method mGetBottom;
    private final Method mGetPx;
    private final Method mGetPosition;
    private final Method mGetChildren;
    private final StringTable mStringTable;

    private String mDebugIndent = "";

    ComposeTree(@NonNull ClassLoader classLoader, @NonNull StringTable stringTable)
            throws Exception {
        mStringTable = stringTable;
        mSlotTableSetKey =
                findFieldOrNull(classLoader, "androidx.ui.core.R$id", "inspection_slot_table_set");
        mGetTables =
                findMethodOrNull(classLoader, "androidx.ui.tooling.InspectableKt", "getTables");
        mAsTree =
                findMethod(
                        classLoader,
                        "androidx.ui.tooling.SlotTreeKt",
                        "asTree",
                        "androidx.compose.SlotTable");
        mGetBox = findMethod(classLoader, "androidx.ui.tooling.Group", "getBox");
        mGetLeft = findMethod(classLoader, "androidx.ui.unit.IntPxBounds", "getLeft");
        mGetRight = findMethod(classLoader, "androidx.ui.unit.IntPxBounds", "getRight");
        mGetTop = findMethod(classLoader, "androidx.ui.unit.IntPxBounds", "getTop");
        mGetBottom = findMethod(classLoader, "androidx.ui.unit.IntPxBounds", "getBottom");
        mGetPx = findMethod(classLoader, "androidx.ui.unit.IntPx", "getValue");
        mGetPosition =
                findMethod(
                        classLoader,
                        "androidx.ui.tooling.SlotTreeKt",
                        "getPosition",
                        "androidx.ui.tooling.Group");
        mGetChildren = findMethod(classLoader, "androidx.ui.tooling.Group", "getChildren");
    }

    /**
     * Main method.
     *
     * @param parentView the id representing the protobuf instance of the parent view
     */
    public void loadComposeTree(@NonNull View view, long parentView)
            throws ReflectiveOperationException {
        dumpGroupHeader();

        Collection<Object> tables = null;
        if (mSlotTableSetKey != null) { // version 0.1.0.dev14 and above
            //noinspection unchecked
            tables = (Collection<Object>) view.getTag(mSlotTableSetKey.getInt(null));
        } else if (mGetTables != null) {
            //noinspection unchecked
            tables = (Collection<Object>) mGetTables.invoke(null);
        }
        if (tables == null) {
            return;
        }

        List<ComposeView> views = new ArrayList<>();
        for (Object table : tables) {
            resetDebugIndent();
            Object group = mAsTree.invoke(null, table);
            if (group == null) {
                continue;
            }
            List<ComposeView> groupViews = convertGroup(group);
            for (ComposeView tree : groupViews) {
                if (hasNonOriginViews(tree)) {
                    views.add(tree);
                }
            }
        }

        dumpViewsFound(views);
        writeViewsFound(parentView, views);
    }

    /**
     * Workaround for possible bug in the compose tooling.
     *
     * <p>Sometimes there is an extra tree in the SlotTable that is identical to another tree, but
     * where all coordinates are at the origin (0,0). For now: disregard such trees. TODO: Check
     * with Compose team why we are getting these extra trees.
     */
    private static boolean hasNonOriginViews(@NonNull ComposeView tree) {
        if (tree.getBounds().left != 0 || tree.getBounds().top != 0) {
            return true;
        }
        for (ComposeView subView : tree.getChildren()) {
            if (hasNonOriginViews(subView)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert the specified group into a list of {@link ComposeView}s.
     *
     * <p>The tree of group nodes contains many nodes with either no information or information of
     * little value such as "startExpr", ambient Providers. These nodes are filtered out controlled
     * by the result of {@link #getSource}.
     *
     * <p>A new tree of the nodes of interest is created and returned.
     */
    @NonNull
    private List<ComposeView> convertGroup(Object group) throws ReflectiveOperationException {
        dumpGroup(group);
        ComposeView.Source source = getSource(group);

        List<ComposeView> children = Collections.emptyList();
        List<Object> subGroups = getChildren(group);
        if (!subGroups.isEmpty()) {
            incrementDebugIndent();
            for (Object subGroup : subGroups) {
                List<ComposeView> subViews = convertGroup(subGroup);
                if (!subViews.isEmpty()) {
                    if (children.isEmpty()) {
                        children = new ArrayList<>();
                    }
                    children.addAll(subViews);
                }
            }
            decrementDebugIndent();
        }
        Rect bounds = source.isEmpty() ? EMPTY_RECT : getBounds(group);
        if (!source.isEmpty() && bounds.height() > 0 && bounds.width() > 0) {
            return Collections.singletonList(new ComposeView(source, bounds, children));
        } else {
            return children;
        }
    }

    private void writeViewsFound(long parentViewBuffer, @NonNull List<ComposeView> views) {
        dumpEmittedViewHeader();
        for (ComposeView view : views) {
            writeInvocationsAsViews(parentViewBuffer, view);
        }
    }

    /**
     * Write the nodes to the protobuf as View information.
     *
     * <p>The idea is to emit each Composable node to the protobuf along with the location where the
     * Composable was instantiated from a lambda in a parent Composable statement.
     */
    private void writeInvocationsAsViews(long parentViewBuffer, @NonNull ComposeView parent) {
        if (parent.getChildren().size() == 1
                && parent.getMethod().endsWith(INVOKE)
                && !parent.getChildren().get(0).getMethod().endsWith((INVOKE))) {
            ComposeView child = parent.getChildren().get(0);
            long buffer = emit(parentViewBuffer, parent, child);
            incrementDebugIndent();
            writeInvocationsAsViews(buffer, child);
            decrementDebugIndent();
        } else {
            for (ComposeView child : parent.getChildren()) {
                writeInvocationsAsViews(parentViewBuffer, child);
            }
        }
    }

    /**
     * Emit the invocation & composable pair as a view node to the protobuf.
     */
    private long emit(
            long parentView, @NonNull ComposeView invocation, @NonNull ComposeView composable) {
        String composableName = extractComposableName(composable.getMethod());
        String[] invocationPair = extractPackageName(invocation.getMethod());
        dumpView(invocation, composableName);
        Rect bounds = composable.getBounds();
        return addComposeView(
                parentView,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
                toInt(composableName),
                toInt(invocation.getFilename()),
                toInt(invocationPair[1]),
                toInt(invocationPair[0]),
                invocation.getLineNumber());
    }

    private int toInt(@Nullable String value) {
        return mStringTable.generateStringId(value);
    }

    /**
     * Return the composable from the method name.
     *
     * <p>Example of method names to map:
     *
     * <ul>
     *   <li>androidx.ui.material.MaterialThemeKt.MaterialTheme -> MaterialTheme
     *   <li>com.example.mycompose.MainActivityKt$Greeting$1.invoke -> Greeting
     * </ul>
     */
    @NonNull
    private static String extractComposableName(@NonNull String method) {
        int innerClassStart = method.indexOf('$');
        if (innerClassStart > 0) {
            int innerClassEnd = method.indexOf('$', innerClassStart + 1);
            return innerClassEnd > 0
                    ? method.substring(innerClassStart + 1, innerClassEnd)
                    : method.substring(innerClassStart);
        }
        int colon = method.lastIndexOf('.');
        return colon > 0 ? method.substring(colon + 1) : method;
    }

    /**
     * Split a method name in package name and method name without the package
     *
     * <p>Example of method names to map: androidx.ui.material.MaterialThemeKt.MaterialTheme ->
     * (androidx.ui.material, MaterialThemeKt.MaterialTheme)
     * com.example.mycompose.MainActivityKt$Greeting$1.invoke -> (com.example.mycompose,
     * MaterialThemeKt.$Greeting$1.invoke)
     */
    @NonNull
    private static String[] extractPackageName(@NonNull String method) {
        int innerClassStart = method.indexOf('$');
        if (innerClassStart < 0) {
            innerClassStart = method.length();
        }
        int dot = method.lastIndexOf('.', innerClassStart - 1);
        return new String[] {method.substring(0, dot), method.substring(dot + 1)};
    }

    @NonNull
    private Rect getBounds(@NonNull Object group) throws ReflectiveOperationException {
        Rect bounds = new Rect();
        Object box = mGetBox.invoke(group);
        bounds.left = getPixels(mGetLeft.invoke(box));
        bounds.right = getPixels(mGetRight.invoke(box));
        bounds.top = getPixels(mGetTop.invoke(box));
        bounds.bottom = getPixels(mGetBottom.invoke(box));
        return bounds;
    }

    private int getPixels(@NonNull Object pixels) throws ReflectiveOperationException {
        if (pixels instanceof Integer) {
            return (int) pixels;
        }
        // for dev09 and earlier...
        return (int) mGetPx.invoke(pixels);
    }

    @NonNull
    private List<Object> getChildren(@NonNull Object group) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        List<Object> children = (List<Object>) mGetChildren.invoke(group);
        return children != null ? children : Collections.emptyList();
    }

    @Nullable
    private String getPosition(@NonNull Object group) throws ReflectiveOperationException {
        return (String) mGetPosition.invoke(null, group);
    }

    @NonNull
    private ComposeView.Source getSource(@NonNull Object group)
            throws ReflectiveOperationException {
        String position = getPosition(group);
        if (position == null) {
            return EMPTY_SOURCE;
        }
        Matcher matcher = POSITION_PATTERN.matcher(position);
        if (!matcher.matches()) {
            return EMPTY_SOURCE;
        }
        String method = matcher.group(POSITION_GROUP_FUNCTION_NAME);
        String fileName = matcher.group(POSITION_GROUP_FILENAME);
        int lineNumber = Integer.parseInt(matcher.group(POSITION_GROUP_LINE_NUMBER));
        if (unwantedGroup(fileName, method)) {
            return EMPTY_SOURCE;
        }
        return new ComposeView.Source(method, fileName, lineNumber);
    }

    private static boolean unwantedGroup(@NonNull String fileName, @NonNull String function) {
        String unwantedFunctionPrefix = ignoredFunctions.get(fileName);
        if (unwantedFunctionPrefix == null) {
            return false;
        }
        return function.startsWith(unwantedFunctionPrefix);
    }

    @NonNull
    private static Method findMethod(
            @NonNull ClassLoader classLoader,
            @NonNull String className,
            @NonNull String methodName,
            @NonNull String... argumentClassNames)
            throws Exception {
        Class<?> classInstance = classLoader.loadClass(className);
        Class<?>[] argumentClasses = new Class[argumentClassNames.length];
        for (int index = 0; index < argumentClassNames.length; index++) {
            argumentClasses[index] = classLoader.loadClass(argumentClassNames[index]);
        }
        return classInstance.getDeclaredMethod(methodName, argumentClasses);
    }

    @Nullable
    @SuppressWarnings("SameParameterValue")
    private static Method findMethodOrNull(
            @NonNull ClassLoader classLoader,
            @NonNull String className,
            @NonNull String methodName,
            @NonNull String... argumentClassNames) {
        try {
            return findMethod(classLoader, className, methodName, argumentClassNames);
        } catch (Exception ex) {
            return null;
        }
    }

    @NonNull
    private static Field findField(
            @NonNull ClassLoader classLoader, @NonNull String className, @NonNull String fieldName)
            throws Exception {
        Class<?> classInstance = classLoader.loadClass(className);
        return classInstance.getDeclaredField(fieldName);
    }

    @Nullable
    @SuppressWarnings("SameParameterValue")
    private static Field findFieldOrNull(
            @NonNull ClassLoader classLoader,
            @NonNull String className,
            @NonNull String fieldName) {
        try {
            return findField(classLoader, className, fieldName);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, String> createIgnoredComposeFunctions() {
        Map<String, String> map = new HashMap<>();
        map.put("AndroidAmbients.kt", "androidx.ui.core.");
        map.put("Ambient.kt", "androidx.compose.");
        map.put("Ambients.kt", "androidx.ui.core.");
        map.put("Composer.kt", "androidx.compose.");
        map.put("Inspectable.kt", "androidx.ui.tooling.");
        map.put("Layout.kt", "androidx.ui.core.");
        map.put("SelectionContainer.kt", "androidx.ui.core.selection.");
        map.put("Semantics.kt", "androidx.ui.semantics.");
        map.put("Wrapper.kt", "androidx.ui.core.");
        map.put("null", "");
        return Collections.unmodifiableMap(map);
    }

    /** Adds a compose view to a View protobuf */
    private native long addComposeView(
            long parentView,
            int x,
            int y,
            int width,
            int height,
            int composeClassName,
            int fileName,
            int invocationName,
            int invocationPackageName,
            int lineNumber);

    // region DEBUG print methods
    private void dumpGroupHeader() {
        if (DEBUG_COMPOSE) {
            resetDebugIndent();
            Log.w(LOG_TAG, "==== Groups Found ===================================>");
        }
    }

    private void dumpViewsFound(@NonNull List<ComposeView> views) {
        if (DEBUG_COMPOSE) {
            Log.w(LOG_TAG, "");
            Log.w(LOG_TAG, "==== ComposeViews Found =============================>");
            resetDebugIndent();
            dumpViews(views);
        }
    }

    private void dumpEmittedViewHeader() {
        if (DEBUG_COMPOSE) {
            Log.w(LOG_TAG, "");
            resetDebugIndent();
            Log.w(LOG_TAG, "==== ComposeViews Written to protobuf ===============>");
        }
    }

    private void dumpViews(@NonNull List<ComposeView> views) {
        if (DEBUG_COMPOSE) {
            for (ComposeView view : views) {
                dumpView(view, null);
                incrementDebugIndent();
                dumpViews(view.getChildren());
                decrementDebugIndent();
            }
        }
    }

    private void dumpView(@NonNull ComposeView view, @Nullable String composable) {
        if (DEBUG_COMPOSE) {
            //noinspection VariableNotUsedInsideIf
            String quote = composable != null ? "\"" : "";
            Log.w(
                    LOG_TAG,
                    String.format(
                            "\"%s\", %s%s%s, \"%s\", %d, \"%s\", %d, %d, %d, %d",
                            mDebugIndent,
                            quote,
                            composable,
                            quote,
                            view.getFilename(),
                            view.getLineNumber(),
                            view.getMethod(),
                            view.getBounds().left,
                            view.getBounds().top,
                            view.getBounds().right,
                            view.getBounds().bottom));
        }
    }

    private void dumpGroup(@NonNull Object group) throws ReflectiveOperationException {
        if (DEBUG_COMPOSE) {
            String position = getPosition(group);
            //noinspection VariableNotUsedInsideIf
            String quote = position != null ? "\"" : "";
            Rect bounds = getBounds(group);
            Log.w(
                    LOG_TAG,
                    String.format(
                            "\"%s\", %s%s%s, %d, %d, %d, %d",
                            mDebugIndent,
                            quote,
                            position,
                            quote,
                            bounds.left,
                            bounds.right,
                            bounds.top,
                            bounds.bottom));
        }
    }

    private void resetDebugIndent() {
        if (DEBUG_COMPOSE) {
            mDebugIndent = "";
        }
    }

    private void incrementDebugIndent() {
        if (DEBUG_COMPOSE) {
            mDebugIndent += " ";
        }
    }

    private void decrementDebugIndent() {
        if (DEBUG_COMPOSE) {
            mDebugIndent = mDebugIndent.substring(0, mDebugIndent.length() - 1);
        }
    }
    // endregion
}
