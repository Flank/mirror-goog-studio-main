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
import com.android.tools.profiler.support.util.StackTrace;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of helpers for Android Wake Lock instrumentation, used by the Energy profiler.
 *
 * <p>Both {@link PowerManager} and {@link WakeLock} are final classes so instead of extending
 * {@link WakeLock} we hook into each method.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class WakeLockWrapper {

    /** Data structure for wake lock creation parameters. */
    private static final class CreationParams {
        final int myLevelAndFlags;
        final String myTag;

        CreationParams(int levelAndFlags, String tag) {
            myLevelAndFlags = levelAndFlags;
            myTag = tag;
        }
    }

    /** Data structure for wake lock release parameters. */
    private static final class ReleaseParams {
        final WakeLock myWakeLock;
        final int myFlags;

        ReleaseParams(WakeLock wakeLock, int flags) {
            myWakeLock = wakeLock;
            myFlags = flags;
        }
    }

    /**
     * Use a thread-local variable for wake lock creation parameters, so a value can be temporarily
     * stored when we enter a wakelock's constructor and retrieved when we exit it. Using a
     * ThreadLocal protects against the situation when multiple threads create wake locks at the
     * same time.
     */
    private static final ThreadLocal<CreationParams> newWakeLockData =
            new ThreadLocal<CreationParams>();

    /**
     * Use a thread-local variable for wake lock release parameters, so a value can be temporarily
     * stored when we enter the release method and retrieved when we exit it.
     */
    private static final ThreadLocal<ReleaseParams> releaseWakeLockData =
            new ThreadLocal<ReleaseParams>();

    /** Used by acquire and release hooks to look up the generated ID by wake lock instance. */
    private static final Map<WakeLock, Integer> eventIdMap = new HashMap<WakeLock, Integer>();

    /** Used by acquire hooks to retrieve wake lock creation parameters. */
    private static final Map<WakeLock, CreationParams> wakeLockCreationParamsMap =
            new HashMap<WakeLock, CreationParams>();

    /**
     * Entry hook for {@link PowerManager#newWakeLock(int, String)}. Captures the flags and myTag
     * parameters.
     *
     * @param powerManager the wrapped PowerManager instance, i.e. "this".
     * @param levelAndFlags the myLevelAndFlags parameter passed to the original method.
     * @param tag the myTag parameter passed to the original method.
     */
    public static void onNewWakeLockEntry(
            PowerManager powerManager, int levelAndFlags, String tag) {
        newWakeLockData.set(new CreationParams(levelAndFlags, tag));
    }

    /**
     * Exit hook for {@link PowerManager#newWakeLock(int, String)}. Associates wake lock instance
     * with the previously captured flags and myTag parameters.
     *
     * @param returnedWakeLock the wrapped return value.
     * @return the same wrapped return value.
     */
    public static WakeLock onNewWakeLockExit(WakeLock returnedWakeLock) {
        wakeLockCreationParamsMap.put(returnedWakeLock, newWakeLockData.get());
        return returnedWakeLock;
    }

    /**
     * Wraps {@link WakeLock#acquire()}.
     *
     * @param wakeLock the wrapped {@link WakeLock} instance, i.e. "this".
     */
    public static void wrapAcquire(WakeLock wakeLock) {
        wrapAcquire(wakeLock, 0);
    }

    /**
     * Wraps {@link WakeLock#acquire(long)}.
     *
     * <p>Since {@link WakeLock#acquire(long)} does not call {@link WakeLock#acquire()} (vice
     * versa), this will not cause double-instrumentation.
     *
     * @param wakeLock the wrapped {@link WakeLock} instance, i.e. "this".
     * @param timeout the timeout parameter passed to the original method.
     */
    public static void wrapAcquire(WakeLock wakeLock, long timeout) {
        if (!eventIdMap.containsKey(wakeLock)) {
            eventIdMap.put(wakeLock, EventIdGenerator.nextId());
        }
        if (wakeLockCreationParamsMap.containsKey(wakeLock)) {
            CreationParams creationParams = wakeLockCreationParamsMap.get(wakeLock);
            sendWakeLockAcquired(
                    eventIdMap.get(wakeLock),
                    creationParams.myLevelAndFlags,
                    creationParams.myTag,
                    timeout,
                    StackTrace.getStackTrace());
        }
    }

    /**
     * Entry hook for {@link WakeLock#release(int)}. Capture the flags passed to the method and the
     * "this" instance so the exit hook can retrieve them back.
     *
     * @param wakeLock the wrapped {@link WakeLock} instance, i.e. "this".
     * @param flags the flags parameter passed to the original method.
     */
    public static void onReleaseEntry(WakeLock wakeLock, int flags) {
        releaseWakeLockData.set(new ReleaseParams(wakeLock, flags));
    }

    /**
     * Exit hook for {@link WakeLock#release(int)}. {@link WakeLock#isHeld()} may be updated in the
     * method, so we should retrieve the value in an exit hook. Then we send the held state along
     * with the flags from the entry hook to Studio Profiler.
     */
    public static void onReleaseExit() {
        ReleaseParams releaseParams = releaseWakeLockData.get();
        if (eventIdMap.containsKey(releaseParams.myWakeLock)) {
            sendWakeLockReleased(
                    eventIdMap.get(releaseParams.myWakeLock),
                    releaseParams.myFlags,
                    releaseParams.myWakeLock.isHeld(),
                    StackTrace.getStackTrace());
        }
    }

    // Native functions to send wake lock events to perfd.
    private static native void sendWakeLockAcquired(
            int eventId, int flags, String tag, long timeout, String stack);

    private static native void sendWakeLockReleased(
            int eventId, int releaseFlags, boolean isHeld, String stack);
}
