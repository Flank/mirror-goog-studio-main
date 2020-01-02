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

import android.graphics.HardwareRenderer;
import android.graphics.Picture;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Workaround for 141772764, until ViewDebug.startRenderingCommandsCapture is fixed. This reproduces
 * the fix in ViewDebug, currently pending as I3316d49970b96f1c59bb0a28ff7335db608e539e.
 */
class SkiaQWorkaround {

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
                // shouldn't happen
            }
        }
        return null;
    }

    @Nullable
    static AutoCloseable startRenderingCommandsCapture(
            @NonNull View tree,
            @NonNull Executor executor,
            @NonNull Callable<OutputStream> callback) {
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
        final HardwareRenderer renderer =
                (HardwareRenderer) getFieldValue(attachInfo, "mThreadedRenderer");
        if (renderer != null) {
            return new StreamingPictureCallbackHandler(renderer, callback, executor);
        }
        return null;
    }

    @SuppressWarnings("resource")
    private static class StreamingPictureCallbackHandler
            implements AutoCloseable, HardwareRenderer.PictureCapturedCallback, Runnable {
        private final HardwareRenderer mRenderer;
        private final Callable<OutputStream> mCallback;
        private final Executor mExecutor;
        private final ReentrantLock mLock = new ReentrantLock(false);
        private final ArrayDeque<byte[]> mQueue = new ArrayDeque<>(3);
        private final ByteArrayOutputStream mByteStream = new ByteArrayOutputStream();
        private boolean mStopListening;
        private Thread mRenderThread;

        private StreamingPictureCallbackHandler(
                @NonNull HardwareRenderer renderer,
                @NonNull Callable<OutputStream> callback,
                @NonNull Executor executor) {
            mRenderer = renderer;
            mCallback = callback;
            mExecutor = executor;
            mRenderer.setPictureCaptureCallback(this);
        }

        @Override
        public void close() {
            mLock.lock();
            mStopListening = true;
            mLock.unlock();
            mRenderer.setPictureCaptureCallback(null);
        }

        @Override
        public void onPictureCaptured(@NonNull Picture picture) {
            mLock.lock();
            if (mStopListening) {
                mLock.unlock();
                mRenderer.setPictureCaptureCallback(null);
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
                        "Aborting rendering commands capture " + "because callback threw exception",
                        ex);
            }
            if (stream != null) {
                try {
                    stream.write(picture);
                } catch (IOException ex) {
                    Log.w(
                            "ViewDebug",
                            "Aborting rendering commands capture "
                                    + "due to IOException writing to output stream",
                            ex);
                }
            } else {
                close();
            }
        }
    }
}
