/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.PixelCopy.OnPixelCopyFinishedListener;
import android.view.Surface;

/**
 * Provides a convenient way to synchronously copy pixels from a surface to a bitmap.
 *
 * <p>Adapted from
 * cts/libs/deviceutillegacy/src/com/android/compatibility/common/util/SynchronousPixelCopy.java.
 * This differs in that it provides a way to shutdown the Looper it creates nicely.
 */
public class SynchronousPixelCopy implements OnPixelCopyFinishedListener {
    private static Handler sHandler = null;
    private static final Object handlerLock = new Object();

    private static Handler ensureHandlerStarted() {
        synchronized (handlerLock) {
            if (sHandler != null) {
                return sHandler;
            }
            HandlerThread thread = new HandlerThread("PixelCopyHelper");
            thread.start();
            sHandler = new Handler(thread.getLooper());
            return sHandler;
        }
    }

    public static void stopHandler() {
        synchronized (handlerLock) {
            if (sHandler != null) {
                sHandler.getLooper().quitSafely();
                sHandler = null;
            }
        }
    }

    private int mStatus = -1;

    public int request(Surface source, Rect srcRect, Bitmap dest) throws InterruptedException {
        synchronized (this) {
            Handler handler = ensureHandlerStarted();
            PixelCopy.request(source, srcRect, dest, this, handler);
            return getResultLocked();
        }
    }

    private int getResultLocked() throws InterruptedException {
        // The normal amount of time should be much less--around 10ms. However it's possible for
        // other things that are going on at the same time to delay substantially if e.g. an
        // activity is launching.
        // Note also that PixelCopy doesn't check whether its post() is successful, so it's possible
        // that the handler was exiting and the request won't even be run, and so we'll time out
        // here.
        this.wait(1000);
        return mStatus;
    }

    @Override
    public void onPixelCopyFinished(int copyResult) {
        synchronized (this) {
            mStatus = copyResult;
            this.notify();
        }
    }
}
