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

import android.os.PowerManager.WakeLock;
import com.android.tools.profiler.support.util.StudioLog;

/**
 * Wrapper for Android WakeLock instrumentation.
 *
 * <p>Both {@link android.os.PowerManager} and {@link WakeLock} are final classes so instead of
 * extending {@link WakeLock} we hook into each method.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class WakeLockWrapper {
    public static void wrapAcquire(WakeLock wrapped) {
        // TODO: Send data via GRpc
        StudioLog.v(String.format("Acquiring WakeLock: %s", System.identityHashCode(wrapped)));
    }

    public static void wrapRelease(WakeLock wrapped, int timeout) {
        // TODO: Send data via GRpc
        StudioLog.v(String.format("Releasing WakeLock: %s", System.identityHashCode(wrapped)));
    }
}
