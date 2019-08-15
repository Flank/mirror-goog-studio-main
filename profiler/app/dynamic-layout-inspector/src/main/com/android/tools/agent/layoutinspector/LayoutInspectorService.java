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
import android.view.ViewDebug;
import android.view.ViewGroup;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This (singleton) class can register a callback to, whenever a view is updated, send the current
 * skia picture back to studio.
 */
@SuppressWarnings("unused") // invoked via jni
public class LayoutInspectorService {
    private final Properties properties = new Properties();
    private final ComponentTree componentTree = new ComponentTree();
    private final Object lock = new Object();
    private AutoCloseable captureClosable = null;

    private static LayoutInspectorService sInstance;

    public static LayoutInspectorService instance() {
        if (sInstance == null) {
            sInstance = new LayoutInspectorService();
        }
        return sInstance;
    }

    private LayoutInspectorService() {}

    /** This method is called when a layout inspector command is recieved by the agent. */
    @SuppressWarnings("unused") // invoked via jni
    public void onStartLayoutInspectorCommand() {
        View root = getRootView();

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final Callable<OutputStream> callable =
                new Callable<OutputStream>() {
                    @Override
                    public OutputStream call() {
                        return os;
                    }
                };

        final Executor realExecutor = Executors.newSingleThreadExecutor();

        final Executor executor =
                new Executor() {
                    @Override
                    public void execute(final Runnable command) {
                        realExecutor.execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        command.run();
                                        byte[] arr = os.toByteArray();
                                        os.reset();
                                        captureAndSendComponentTree(arr);
                                    }
                                });
                    }
                };

        final Method startCaptureMethod = getStartCaptureMethod();

        // Stop a running capture:
        onStopLayoutInspectorCommand();

        root.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            synchronized (lock) {
                                captureClosable =
                                        (AutoCloseable)
                                                startCaptureMethod.invoke(
                                                        null, root, executor, callable);
                            }
                            root.invalidate();
                            root.getViewTreeObserver()
                                    .addOnWindowAttachListener(new DetectDetach(sInstance, root));
                        } catch (Throwable e) {
                            sendErrorMessage(e);
                        }
                    }
                });
    }

    /** Stops the capture from sending more messages. */
    @SuppressWarnings("unused") // invoked via jni
    public void onStopLayoutInspectorCommand() {
        synchronized (lock) {
            if (captureClosable != null) {
                try {
                    captureClosable.close();
                } catch (Exception ex) {
                    sendErrorMessage(ex);
                }
                captureClosable = null;
            }
        }
    }

    public View getRootView() {
        try {
            Class<?> windowInspector = Class.forName("android.view.inspector.WindowInspector");
            Method getViewsMethod = windowInspector.getMethod("getGlobalWindowViews");
            List<View> views = (List<View>) getViewsMethod.invoke(null);
            return views.isEmpty() ? null : views.get(0);
        } catch (Throwable e) {
            sendErrorMessage(e);
            return null;
        }
    }

    private Method getStartCaptureMethod() {
        try {
            return ViewDebug.class.getMethod(
                    "startRenderingCommandsCapture", View.class, Executor.class, Callable.class);
        } catch (Throwable e) {
            sendErrorMessage(e);
            return null;
        }
    }

    /** Allocates a SendRequest protobuf. */
    private native long allocateSendRequest();

    /** Frees a SendRequest protobuf. */
    private native long freeSendRequest(long request);

    /** Initializes the request as a ComponentTree and returns an event handle */
    private native long initComponentTree(long request);

    /** Sends a component tree to Ansroid Studio. */
    private native long sendComponentTree(long request, byte[] image, int len, int id);

    /** This method is called when a new image has been snapped. */
    private void captureAndSendComponentTree(byte[] image) {
        long request = 0;
        try {
            View root = findRootView();
            request = allocateSendRequest();
            long event = initComponentTree(request);
            if (root != null) {
                componentTree.writeTree(event, root);
            }
            int id = (int) System.currentTimeMillis();
            sendComponentTree(request, image, image.length, id);
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        } finally {
            freeSendRequest(request);
        }
    }

    /** Sends the properties via the agent. */
    private native void sendProperties(long event, long viewId);

    /**
     * This method is called when a layout inspector command is recieved by the agent.
     *
     * @param viewId the uniqueDrawingId on the view which is the same id used in the skia image
     * @param event a handle to an PropertyEvent protobuf to pass back in native calls
     */
    @SuppressWarnings("unused") // invoked via jni
    public void onGetPropertiesInspectorCommand(long viewId, long event) {
        try {
            View root = findRootView();
            View view = findViewById(root, viewId);
            if (view == null) {
                return;
            }
            properties.writeProperties(view, event);
            sendProperties(event, viewId);
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        }
    }

    /**
     * This method is called when an edit property command is received by the agent.
     *
     * @param viewId the uniqueDrawingId of the view to modify
     * @param attributeId the resources ID of the attribute to modify
     * @param value the value to set the attribute to
     */
    @SuppressWarnings("unused") // invoked via jni
    public void onEditPropertyInspectorCommand(long viewId, int attributeId, int value) {
        try {
            View root = findRootView();
            View view = findViewById(root, viewId);
            if (view == null) {
                return;
            }
            applyPropertyEdit(view, attributeId, value);
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        }
    }

    private View findRootView() {
        try {
            Class<?> windowInspector = Class.forName("android.view.inspector.WindowInspector");
            Method getViewsMethod = windowInspector.getMethod("getGlobalWindowViews");
            List<View> views = (List<View>) getViewsMethod.invoke(null);
            return views.isEmpty() ? null : views.get(0);
        } catch (Throwable ex) {
            return null;
        }
    }

    private View findViewById(View parent, long id) {
        if (parent != null && getUniqueDrawingId(parent) == id) {
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

    /** Sends an LayoutInspector Event with an error message back to Studio */
    private native void sendErrorMessage(String message);

    private void sendErrorMessage(Throwable e) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(error));
        sendErrorMessage(error.toString());
    }

    private void applyPropertyEdit(View view, int attributeId, int value) {
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
                sendErrorMessage(
                        "Unsupported attribute for editing: " + Integer.toHexString(attributeId));
        }
    }
}
