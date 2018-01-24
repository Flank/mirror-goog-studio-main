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

/**
 * A set of helpers for Android Wake Lock instrumentation, used by the Energy profiler.
 *
 * <p>Both {@link PowerManager} and {@link WakeLock} are final classes so instead of extending
 * {@link WakeLock} we hook into each method.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class WakeLockWrapper {
    // Native functions to send wake lock events to perfd.
    private static native void sendWakeLockAcquired();

    private static native void sendWakeLockReleased();

    /**
     * Exit hook for {@link PowerManager#newWakeLock(int, String)}.
     *
     * @param wrapped the return value of the original method.
     * @return the original return value.
     */
    public static WakeLock onNewWakeLockExit(WakeLock wrapped) {
        // TODO: Send data via GRpc
        Class<?> c = wrapped.getClass();
        try {
            // Uses reflection to access the fields that store the original parameter values.
            // TODO(b/72337740): Use constructor entry hook once supported.
            Field flagsField = c.getDeclaredField("mFlags");
            Field tagField = c.getDeclaredField("mTag");
            flagsField.setAccessible(true);
            tagField.setAccessible(true);
            StudioLog.v(
                    String.format(
                            "Created WakeLock: %s. Flags: %x. Tag: %s",
                            System.identityHashCode(wrapped),
                            flagsField.getInt(wrapped),
                            tagField.get(wrapped)));
        } catch (NoSuchFieldException e) {
            StudioLog.e(e.getMessage());
        } catch (IllegalAccessException e) {
            StudioLog.e(e.getMessage());
        }
        return wrapped;
    }

    /**
     * Wraps {@link WakeLock#acquire()}.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     */
    public static void wrapAcquire(WakeLock wrapped) {
        sendWakeLockAcquired();
    }

    /**
     * Wraps {@link WakeLock#release(int)}.
     *
     * @param wrapped the wrapped {@link WakeLock} instance, i.e. "this".
     * @param timeout the timeout parameter passed to the original method.
     */
    public static void wrapRelease(WakeLock wrapped, int timeout) {
        sendWakeLockReleased();
    }
}
