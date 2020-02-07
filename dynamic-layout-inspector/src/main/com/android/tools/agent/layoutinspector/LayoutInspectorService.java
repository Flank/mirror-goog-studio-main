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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This (singleton) class can register a callback to, whenever a view is updated, send the current
 * skia picture back to studio.
 */
@SuppressWarnings({"unused", "Convert2Lambda"}) // invoked via jni
public class LayoutInspectorService {
    private static final int MAX_IMAGE_SIZE = (int) (3.5 * 1024. * 1024.);
    // Copied from ViewDebug
    private static final int CAPTURE_TIMEOUT = 6000;

    private final Properties properties = new Properties();
    private final Object lock = new Object();
    private DetectRootViewChange detectRootChange = null;
    private Map<View, AutoCloseable> captureClosables = new HashMap<>();

    private static LayoutInspectorService sInstance;

    static class LayoutModifiedException extends Exception {}

    @NonNull
    public static LayoutInspectorService instance() {
        if (sInstance == null) {
            sInstance = new LayoutInspectorService();
        }
        return sInstance;
    }

    private LayoutInspectorService() {}

    /** This method is called when a layout inspector command is received by the agent. */
    @SuppressWarnings("unused") // invoked via jni
    public void onStartLayoutInspectorCommand() {
        List<View> roots = getRootViews();
        for (View root : roots) {
            startLayoutInspector(root);
        }
        detectRootChange = new DetectRootViewChange(this);
        detectRootChange.start(roots);
    }

    public void startLayoutInspector(@NonNull View root) {
        @SuppressWarnings("resource")
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
                    public void execute(@NonNull final Runnable command) {
                        realExecutor.execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        command.run();
                                        byte[] arr = os.toByteArray();
                                        os.reset();
                                        if (captureClosables.get(root) != null) {
                                            captureAndSendComponentTree(arr, root);
                                        }
                                    }
                                });
                    }
                };

        // Stop a running capture:
        stopCapturing(root);

        root.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            synchronized (lock) {
                                // Sometimes the window has become detached before we get in here,
                                // so check one more time before trying to start capture.
                                if (root.isAttachedToWindow()) {
                                    captureClosables.put(
                                            root,
                                            SkiaQWorkaround.startRenderingCommandsCapture(
                                                    root, executor, callable));
                                }
                                // TODO: The above should be
                                // ViewDebug.startRenderingCommandsCapture(...) once it's fixed.
                            }
                            root.invalidate();
                        } catch (Throwable e) {
                            sendErrorMessage(e);
                        }
                    }
                });
    }

    /** Stops the capture from sending more messages. */
    @SuppressWarnings("unused") // invoked via jni
    public void onStopLayoutInspectorCommand() {
        stopCapturing();
        detectRootChange.stop();
        detectRootChange = null;
    }

    private void stopCapturing() {
        synchronized (lock) {
            for (AutoCloseable closeable : captureClosables.values()) {
                try {
                    closeable.close();
                } catch (Exception ex) {
                    sendErrorMessage(ex);
                }
            }
            captureClosables.clear();
        }
    }

    public void stopCapturing(View root) {
        synchronized (lock) {
            if (captureClosables.containsKey(root)) {
                try {
                    captureClosables.remove(root).close();
                } catch (Exception ex) {
                    sendErrorMessage(ex);
                }
            }
        }
    }

    @NonNull
    public List<View> getRootViews() {
        try {
            List<View> views = WindowInspector.getGlobalWindowViews();
            List<View> result = new ArrayList<>();
            for (View view : views) {
                if (view.getVisibility() == View.VISIBLE && view.isAttachedToWindow()) {
                    result.add(view);
                }
            }
            result.sort(
                    new Comparator<View>() {
                        @Override
                        public int compare(View a, View b) {
                            return Float.compare(a.getZ(), b.getZ());
                        }
                    });
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            sendErrorMessage(e);
            return Collections.emptyList();
        }
    }

    /** Allocates a SendRequest protobuf. */
    private native long allocateSendRequest();

    /** Frees a SendRequest protobuf. */
    private native long freeSendRequest(long request);

    /** Initializes the request as a ComponentTree and returns an event handle */
    private native long initComponentTree(long request, @NonNull long[] allIds);

    /** Sends a component tree to Android Studio. */
    private native long sendComponentTree(
            long request, @NonNull byte[] image, int len, int id, boolean fallbackToPng);

    /** This method is called when a new image has been snapped. */
    private void captureAndSendComponentTree(@NonNull byte[] image, @Nullable View root) {
        long request = 0;
        try {
            request = allocateSendRequest();
            boolean fallbackToPng = false;
            if (image.length > MAX_IMAGE_SIZE && root != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                capture(baos, root);
                image = baos.toByteArray();
                fallbackToPng = true;
            }
            int index = 0;
            List<View> rootViews = getRootViews();
            long[] rootViewIds = new long[rootViews.size()];
            for (int i = 0; i < rootViews.size(); i++) {
                rootViewIds[i] = rootViews.get(i).getUniqueDrawingId();
            }
            long event = initComponentTree(request, rootViewIds);
            if (root != null) {
                new ComponentTree().writeTree(event, root);
            }
            int messageId = (int) System.currentTimeMillis();
            sendComponentTree(request, image, image.length, messageId, fallbackToPng);
        } catch (LayoutModifiedException e) {
            // The layout changed while we were traversing, start over.
            captureAndSendComponentTree(image, root);
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        } finally {
            freeSendRequest(request);
        }
    }

    // Copied from ViewDebug
    private static void capture(@NonNull OutputStream clientStream, @NonNull View captureView)
            throws IOException {
        Bitmap b = performViewCapture(captureView);
        if (b == null) {
            return;
        }
        try (BufferedOutputStream out = new BufferedOutputStream(clientStream, 32 * 1024)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } finally {
            b.recycle();
        }
    }

    // Copied from ViewDebug
    private static Bitmap performViewCapture(final View captureView) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] cache = new Bitmap[1];

        captureView.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Picture picture = new Picture();
                            Canvas canvas =
                                    picture.beginRecording(
                                            captureView.getWidth(), captureView.getHeight());
                            captureView.draw(canvas);
                            picture.endRecording();
                            cache[0] = Bitmap.createBitmap(picture);
                        } catch (OutOfMemoryError e) {
                            Log.w("View", "Out of memory for bitmap");
                        } finally {
                            latch.countDown();
                        }
                    }
                });

        try {
            latch.await(CAPTURE_TIMEOUT, TimeUnit.MILLISECONDS);
            return cache[0];
        } catch (InterruptedException e) {
            Log.w("View", "Could not complete the capture of the view " + captureView);
            Thread.currentThread().interrupt();
        }

        return null;
    }

    /**
     * This method is called when a layout inspector command is received by the agent.
     *
     * @param viewId the uniqueDrawingId on the view which is the same id used in the skia image
     */
    @SuppressWarnings("unused") // invoked via jni
    public void onGetPropertiesInspectorCommand(long viewId) {
        try {
            List<View> roots = getRootViews();
            for (View root : roots) {
                View view = findViewById(root, viewId);
                if (view != null) {
                    properties.handleGetProperties(view);
                    return;
                }
            }
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
            for (View root : getRootViews()) {
                View view = findViewById(root, viewId);
                if (view != null) {
                    applyPropertyEdit(view, attributeId, value);
                    return;
                }
            }
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        }
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

    /** Sends an LayoutInspector Event with an error message back to Studio */
    private static native void sendErrorMessage(@NonNull String message);

    public static void sendErrorMessage(@NonNull Throwable e) {
        //noinspection resource
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(error));
        sendErrorMessage(error.toString());
    }

    private void applyPropertyEdit(@NonNull View view, int attributeId, int value) {
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
