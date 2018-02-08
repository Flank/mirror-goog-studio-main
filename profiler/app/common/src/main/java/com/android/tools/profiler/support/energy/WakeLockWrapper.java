/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.profiler.support.energy;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import com.android.tools.profiler.support.util.StudioLog;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A set of helpers for Android Wake Lock instrumentation, used by the Energy profiler.
 *
 * <p>Both {@link PowerManager} and {@link WakeLock} are final classes so instead of extending
 * {@link WakeLock} we hook into each method.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class WakeLockWrapper {
    private static final AtomicInteger atomicInteger = new AtomicInteger();
    private static final Map<WakeLock, Integer> wakeLockIdMap = new HashMap<>();

    // Native functions to send wake lock events to perfd.
    private static native void sendWakeLockAcquired(
            int wakeLockId, int flags, String tag, long timeout);

    private static native void sendWakeLockReleased(int wakeLockId, int releaseFlags);

    /**
     * Wraps {@link WakeLock#acquire()}.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     */
    public static void wrapAcquire(WakeLock wrapped) {
        wrapAcquire(wrapped, 0);
    }

    /**
     * Wraps {@link WakeLock#acquire(long)}.
     *
     * <p>Since {@link WakeLock#acquire(long)} does not call {@link WakeLock#acquire()} (vice
     * versa), this will not cause double-instrumentation.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     * @param timeout the timeout parameter passed to the original method.
     */
    public static void wrapAcquire(WakeLock wrapped, long timeout) {
        if (!wakeLockIdMap.containsKey(wrapped)) {
            wakeLockIdMap.put(wrapped, atomicInteger.incrementAndGet());
        }
        int wakeLockId = wakeLockIdMap.get(wrapped);
        int flags = 0;
        String tag = "";

        Class<?> c = wrapped.getClass();
        // Uses reflection to access the fields that store the original parameter values.
        // TODO(b/72337740): Use constructor entry hook once supported.
        try {
            Field flagsField = c.getDeclaredField("mFlags");
            flagsField.setAccessible(true);
            flags = flagsField.getInt(wrapped);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            StudioLog.e("Failed to retrieve WakeLock flags: ", e);
        }
        try {
            Field tagField = c.getDeclaredField("mTag");
            tagField.setAccessible(true);
            tag = (String) tagField.get(wrapped);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            StudioLog.e("Failed to retrieve WakeLock tag: ", e);
        }

        sendWakeLockAcquired(wakeLockId, flags, tag, timeout);
    }

    /**
     * Wraps {@link WakeLock#release(int)}.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     * @param flags the flags parameter passed to the original method.
     */
    public static void wrapRelease(WakeLock wrapped, int flags) {
        sendWakeLockReleased(wakeLockIdMap.getOrDefault(wrapped, 0), flags);
    }
}
