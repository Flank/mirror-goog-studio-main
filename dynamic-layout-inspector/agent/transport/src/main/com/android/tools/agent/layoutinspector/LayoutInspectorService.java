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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This (singleton) class can register a callback to, whenever a view is updated, send the current
 * skia picture back to studio.
 */
@SuppressWarnings({"unused", "Convert2Lambda"}) // invoked via jni
public class LayoutInspectorService {
    // Copied from ViewDebug
    private static final int CAPTURE_TIMEOUT = 6000;
    private static final int RETRY_LIMIT_REFRESH = 2;
    private static final byte[] EMPTY_IMAGE = new byte[0];
    private static final long[] EMPTY_ROOT_VIEW_IDS = new long[0];

    private final Properties mProperties = new Properties();
    private final ComponentTree mComponentTree = new ComponentTree(mProperties);
    private final Object mLock = new Object();
    private final AtomicReference<DetectRootViewChange> mDetectRootChange =
            new AtomicReference<>(null);
    private final Map<View, AutoCloseable> mCaptureClosables = new HashMap<>();

    private boolean mUseScreenshotMode = false;
    private boolean mCaptureOnlyOnce = false;

    // The generation of the last component tree sent to the client
    private int mGeneration = 0;

    private static LayoutInspectorService sInstance;

    static class LayoutModifiedException extends Exception {}

    // should match ComponentTreeEvent.PayloadType
    enum ImageType {
        UNKNOWN,
        NONE,
        SKP,
        PNG_AS_REQUESTED
    }

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
    public void onStartLayoutInspectorCommand(boolean showComposeNodes, boolean hideSystemNodes) {
        mCaptureOnlyOnce = false;
        mComponentTree.setShowComposeNodes(showComposeNodes);
        mComponentTree.setHideSystemNodes(hideSystemNodes);
        List<View> roots = getRootViews();
        for (View root : roots) {
            startLayoutInspector(root);
        }
        if (roots.isEmpty()) {
            mGeneration++;
            sendEmptyRootViews();
        }
        startRootViewDetector();
    }

    public void startLayoutInspector(@NonNull View root) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final Callable<OutputStream> callable =
                new Callable<OutputStream>() {
                    @Override
                    public OutputStream call() {
                        return os;
                    }
                };

        final Executor realExecutor =
                Executors.newSingleThreadExecutor(
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                // ThreadWatcher accepts threads starting with "Studio:"
                                return new Thread(r, "Studio:LayInsp");
                            }
                        });

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
                                        if (mCaptureClosables.get(root) != null) {
                                            if (mCaptureOnlyOnce) {
                                                stopCapturing(root);
                                                stopRootViewDetector();
                                            } else {
                                                mGeneration++;
                                            }
                                            captureAndSendComponentTree(arr, root);
                                            if (mCaptureOnlyOnce) {
                                                mProperties.saveAllViewProperties(
                                                        Collections.singletonList(root),
                                                        mGeneration);
                                            }
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
                            synchronized (mLock) {
                                // Sometimes the window has become detached before we get in here,
                                // so check one more time before trying to start capture.
                                if (root.isAttachedToWindow()) {
                                    mCaptureClosables.put(
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
        stopRootViewDetector();
        stopCapturing();
        try {
            mProperties.saveAllViewProperties(getRootViews(), mGeneration);
            mProperties.saveAllComposeParameters(mGeneration);
        } catch (Throwable ex) {
            sendErrorMessage(ex);
        }
    }

    /** Sends a single capture and properties. */
    @SuppressWarnings("unused") // invoked via jni
    public void onRefreshLayoutInspectorCommand(boolean showComposeNodes, boolean hideSystemNodes) {
        mCaptureOnlyOnce = true;
        mComponentTree.setShowComposeNodes(showComposeNodes);
        mComponentTree.setHideSystemNodes(hideSystemNodes);
        mGeneration++;

        // Start the root view detector here because:
        // - There may not be any visible root views yet (the app may be starting up)
        // - The current root views may be in the process of shutting down
        startRootViewDetector();

        List<View> roots = getRootViews();
        for (View root : roots) {
            startLayoutInspector(root);
        }
        if (roots.isEmpty()) {
            sendEmptyRootViews();
        }
    }

    private void startRootViewDetector() {
        int retryLimit = mCaptureOnlyOnce ? RETRY_LIMIT_REFRESH : -1;
        DetectRootViewChange old =
                mDetectRootChange.getAndSet(new DetectRootViewChange(this, retryLimit));
        if (old != null) {
            old.cancel();
        }
    }

    private void stopRootViewDetector() {
        DetectRootViewChange detector = mDetectRootChange.getAndSet(null);
        if (detector != null) {
            detector.cancel();
        }
    }

    private void stopCapturing() {
        synchronized (mLock) {
            for (AutoCloseable closeable : mCaptureClosables.values()) {
                try {
                    closeable.close();
                } catch (Exception ex) {
                    sendErrorMessage(ex);
                }
            }
            mCaptureClosables.clear();
        }
    }

    public void stopCapturing(View root) {
        synchronized (mLock) {
            if (mCaptureClosables.containsKey(root)) {
                try {
                    mCaptureClosables.remove(root).close();
                } catch (Exception ex) {
                    sendErrorMessage(ex);
                }
            }
        }
    }

    public void sendEmptyRootViews() {
        long request = 0;
        try {
            request = allocateSendRequest();
            long event = initComponentTree(request, EMPTY_ROOT_VIEW_IDS, 0, 0);
            byte[] image = EMPTY_IMAGE;
            int messageId = 0;
            ImageType type = ImageType.NONE;
            sendComponentTree(request, image, image.length, messageId, type.ordinal(), mGeneration);
        } finally {
            freeSendRequest(request);
        }
    }

    public void onUseScreenshotModeCommand(boolean useScreenshotMode) {
        if (mUseScreenshotMode != useScreenshotMode) {
            mUseScreenshotMode = useScreenshotMode;
            for (View root : getRootViews()) {
                root.post(root::invalidate);
            }
        }
    }

    @SuppressWarnings("MethodMayBeStatic") // Kept non static for tests
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

    private long[] getRootViewIds() {
        List<View> rootViews = getRootViews();
        long[] rootViewIds = new long[rootViews.size()];
        for (int i = 0; i < rootViews.size(); i++) {
            rootViewIds[i] = rootViews.get(i).getUniqueDrawingId();
        }
        return rootViewIds;
    }

    /** Allocates a SendRequest protobuf. */
    private native long allocateSendRequest();

    /** Frees a SendRequest protobuf. */
    private native long freeSendRequest(long request);

    /** Initializes the request as a ComponentTree and returns an event handle */
    private native long initComponentTree(
            long request, @NonNull long[] allIds, int rootOffsetX, int rootOffsetY);

    /** Sends a component tree to Android Studio. */
    private native long sendComponentTree(
            long request, @NonNull byte[] image, int len, int id, int imageType, int generation);

    /** This method is called when a new image has been snapped. */
    private void captureAndSendComponentTree(@NonNull byte[] image, @Nullable View root) {
        long request = 0;
        try {
            request = allocateSendRequest();
            ImageType type = ImageType.SKP;
            if (mUseScreenshotMode) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                capture(baos, root);
                image = baos.toByteArray();
                type = ImageType.PNG_AS_REQUESTED;
            }
            int index = 0;
            long[] rootViewIds = getRootViewIds();
            // The offset of the root view from the origin of the surface. Will always be 0,0 for
            // the main window, but can be positive for floating windows (e.g. dialogs).
            int[] rootOffset = new int[2];
            if (root != null) {
                root.getLocationInSurface(rootOffset);
            }

            long event = initComponentTree(request, rootViewIds, rootOffset[0], rootOffset[1]);
            if (root != null) {
                mComponentTree.writeTree(event, root);
                if (mComponentTree.showComposeNodes() && mCaptureOnlyOnce) {
                    mProperties.saveAllComposeParameters(mGeneration);
                }
            }
            // Send the message from a non UI thread:
            int messageId = (int) System.currentTimeMillis();
            sendComponentTree(request, image, image.length, messageId, type.ordinal(), mGeneration);
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
            mProperties.handleGetProperties(viewId, mGeneration);
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
        mProperties.handleSetProperty(viewId, attributeId, value);
    }

    /** Sends an LayoutInspector Event with an error message back to Studio */
    public static native void sendErrorMessage(@NonNull String message);

    public static void sendErrorMessage(@NonNull Throwable e) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(error));
        sendErrorMessage(error.toString());
    }
}
