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

    private static final class CreationParams {

        int myLevelAndFlags;
        String myTag;

        CreationParams(int levelAndFlags, String tag) {
            myLevelAndFlags = levelAndFlags;
            myTag = tag;
        }
    }

    private static final CreationParams DEFAULT_CREATION_PARAMS = new CreationParams(0, "");

    /** For generating wake lock IDs. */
    private static final AtomicInteger atomicInteger = new AtomicInteger();

    /**
     * Use a thread-local variable for wake lock creation flags, so a value can be temporarily
     * stored when we enter a wakelock's constructor and retrieved when we exit it. Using a
     * ThreadLocal protects against the situation when multiple threads create wake locks at the
     * same time.
     */
    private static final ThreadLocal<CreationParams> newWakeLockData =
            new ThreadLocal<CreationParams>() {
                @Override
                protected CreationParams initialValue() {
                    return DEFAULT_CREATION_PARAMS;
                }
            };

    /** Maps a wake lock instance to its generated ID. */
    private static final Map<WakeLock, Integer> wakeLockIdMap = new HashMap<>();

    /** Maps a wake lock instance to its creation data (flags and tag). */
    private static final Map<WakeLock, CreationParams> wakeLockCreationDataMap = new HashMap<>();

    /**
     * Entry hook for {@link PowerManager#newWakeLock(int, String)}. Captures the flags and myTag
     * parameters.
     *
     * @param wrapped the wrapped PowerManager instance.
     * @param levelAndFlags the myLevelAndFlags parameter passed to the original method.
     * @param tag the myTag parameter passed to the original method.
     */
    public static void onNewWakeLockEntry(PowerManager wrapped, int levelAndFlags, String tag) {
        newWakeLockData.set(new CreationParams(levelAndFlags, tag));
    }

    /**
     * Exit hook for {@link PowerManager#newWakeLock(int, String)}. Associates wake lock instance
     * with the previously captured flags and myTag parameters.
     *
     * @param wrapped the wrapped return value.
     * @return the same wrapped return value.
     */
    public static WakeLock onNewWakeLockExit(WakeLock wrapped) {
        wakeLockCreationDataMap.put(wrapped, newWakeLockData.get());
        return wrapped;
    }

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
        CreationParams creationData =
                wakeLockCreationDataMap.containsKey(wrapped)
                        ? wakeLockCreationDataMap.get(wrapped)
                        : DEFAULT_CREATION_PARAMS;

        sendWakeLockAcquired(wakeLockId, creationData.myLevelAndFlags, creationData.myTag, timeout);
    }

    /**
     * Wraps {@link WakeLock#release(int)}.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     * @param flags the flags parameter passed to the original method.
     */
    public static void wrapRelease(WakeLock wrapped, int flags) {
        sendWakeLockReleased(
                wakeLockIdMap.containsKey(wrapped) ? wakeLockIdMap.get(wrapped) : 0, flags);
    }

    // Native functions to send wake lock events to perfd.
    private static native void sendWakeLockAcquired(
            int wakeLockId, int flags, String tag, long timeout);

    private static native void sendWakeLockReleased(int wakeLockId, int releaseFlags);
}
