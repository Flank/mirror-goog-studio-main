/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.agent.appinspection.framework;

import android.graphics.HardwareRenderer;
import android.graphics.Picture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dalvik.system.VMDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public class SkiaQWorkaround {

    @Nullable
    private static Object getFieldValue(@NonNull Object o, @NonNull String fieldName) {
        Field f = null;

        for (Class<?> c = o.getClass(); c != null && f == null; c = c.getSuperclass()) {
            try {
                f = c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // nothing, it's expected if the field is declared in a parent class.
            }
        }
        if (f != null) {
            f.setAccessible(true);
            try {
                return f.get(o);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                // shouldn't happen
            }
        }
        return null;
    }

    @Nullable
    public static AutoCloseable startRenderingCommandsCapture(
            @NonNull View tree,
            @NonNull Executor executor,
            @NonNull Callable<OutputStream> callback) {
        VMDebug.allowHiddenApiReflectionFrom(SkiaQWorkaround.class);
        final Object attachInfo = getFieldValue(tree, "mAttachInfo");
        if (attachInfo == null) {
            throw new IllegalArgumentException("Given view isn't attached");
        }

        final Handler handler = (Handler) getFieldValue(attachInfo, "mHandler");
        if (handler == null || handler.getLooper() != Looper.myLooper()) {
            throw new IllegalStateException(
                    "Called on the wrong thread."
                            + " Must be called on the thread that owns the given View");
        }

        final Object renderer = getFieldValue(attachInfo, "mThreadedRenderer");
        if (renderer == null) {
            return null;
        }

        Object streamingPictureCallbackHandler =
                StreamingPictureCallbackHelper.createCallback(renderer, callback, executor);

        VMDebug.allowHiddenApiReflectionFrom(streamingPictureCallbackHandler.getClass());
        VMDebug.allowHiddenApiReflectionFrom(StreamingPictureCallbackHelper.class);

        StreamingPictureCallbackHelper.setPictureCaptureCallback(
                renderer, streamingPictureCallbackHandler);
        return (AutoCloseable) streamingPictureCallbackHandler;
    }

    private static class StreamingPictureCallbackHelper {

        public static void setPictureCaptureCallback(Object renderer, Object callback) {
            try {
                Class<?> rendererClass = renderer.getClass();
                if (!rendererClass.getName().contains("Hardware")) {
                    rendererClass = rendererClass.getSuperclass();
                }
                Method setPictureCaptureCallback =
                        rendererClass.getDeclaredMethod(
                                "setPictureCaptureCallback",
                                HardwareRenderer.PictureCapturedCallback.class);
                setPictureCaptureCallback.invoke(renderer, callback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static Object createCallback(
                Object renderer, Callable<OutputStream> callback, Executor executor) {
            return new StreamingPictureCallbackHandler(renderer, callback, executor);
        }

        @SuppressWarnings("resource")
        private static class StreamingPictureCallbackHandler
                implements AutoCloseable, HardwareRenderer.PictureCapturedCallback, Runnable {

            private final Object mRenderer;

            private final Callable<OutputStream> mCallback;

            private final Executor mExecutor;

            private final ReentrantLock mLock = new ReentrantLock(false);

            private final ArrayDeque<byte[]> mQueue = new ArrayDeque<>(3);

            private final ByteArrayOutputStream mByteStream = new ByteArrayOutputStream();

            private boolean mStopListening;

            private Thread mRenderThread;

            private StreamingPictureCallbackHandler(
                    Object renderer, Callable<OutputStream> callback, Executor executor) {
                mRenderer = renderer;
                mCallback = callback;
                mExecutor = executor;
            }

            @Override
            public void close() {
                mLock.lock();
                mStopListening = true;
                mLock.unlock();
                setPictureCaptureCallback(mRenderer, null);
            }

            @Override
            public void onPictureCaptured(Picture picture) {
                mLock.lock();
                if (mStopListening) {
                    mLock.unlock();
                    setPictureCaptureCallback(mRenderer, null);
                    return;
                }
                if (mRenderThread == null) {
                    mRenderThread = Thread.currentThread();
                }
                boolean needsInvoke = true;
                if (mQueue.size() == 3) {
                    mQueue.removeLast();
                    needsInvoke = false;
                }
                try {
                    //noinspection JavaReflectionMemberAccess
                    Picture.class
                            .getDeclaredMethod("writeToStream", OutputStream.class)
                            .invoke(picture, mByteStream);
                } catch (Exception e) {
                    // shouldn't happen
                }
                mQueue.add(mByteStream.toByteArray());
                mByteStream.reset();
                mLock.unlock();

                if (needsInvoke) {
                    mExecutor.execute(this);
                }
            }

            @Override
            public void run() {
                mLock.lock();
                final byte[] picture = mQueue.poll();
                final boolean isStopped = mStopListening;
                mLock.unlock();
                if (Thread.currentThread() == mRenderThread) {
                    close();
                    throw new IllegalStateException(
                            "ViewDebug#startRenderingCommandsCapture must be given an executor that "
                                    + "invokes asynchronously");
                }
                if (isStopped) {
                    return;
                }
                OutputStream stream = null;
                try {
                    stream = mCallback.call();
                } catch (Exception ex) {
                    Log.w(
                            "ViewDebug",
                            "Aborting rendering commands capture because callback threw exception",
                            ex);
                }
                if (stream != null) {
                    try {
                        stream.write(picture);
                    } catch (IOException ex) {
                        Log.w(
                                "ViewDebug",
                                "Aborting rendering commands capture due to IOException"
                                        + " writing to output stream",
                                ex);
                    }
                } else {
                    close();
                }
            }
        }
    }
}
