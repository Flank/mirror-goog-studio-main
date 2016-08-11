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

package com.android.tools.profiler.support.energy;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import java.util.HashMap;

/**
 * Tracks power manager wake locks.
 * TODO Replace logging with sending data to perfa.
 */
public class PowerManagerWakeLockTracker {
    private static final HashMap<Integer, WakeLockInfo> wlInfoMap
            = new HashMap<Integer, WakeLockInfo>();

    public static PowerManager.WakeLock wrapNewWakeLock(PowerManager powerManager,
            int levelAndFlags, String tag) {
        onPowerManagerWakeLockCreated(tag);

        // Add the {hashCode: WakeLockInfo} to the map.
        WakeLock wl = powerManager.newWakeLock(levelAndFlags, tag);
        wlInfoMap.put(System.identityHashCode(wl), new WakeLockInfo(tag, levelAndFlags));

        return wl;
    }

    public static void wrapSetReferenceCounted(WakeLock wl, boolean value) {
        wl.setReferenceCounted(value);
        WakeLockInfo info = wlInfoMap.get(System.identityHashCode(wl));
    }

    public static void wrapAcquire(WakeLock wl) {
        wl.acquire();
        internalLogAcquire(wl, -1);
    }

    public static void wrapAcquire(WakeLock wl, long timeout) {
        wl.acquire(timeout);
        internalLogAcquire(wl, timeout);
    }

    public static void wrapRelease(WakeLock wl) {
        wl.release();
        internalLogRelease(wl, 0, false);
    }

    public static void wrapRelease(WakeLock wl, int flags) {
        wl.release(flags);
        internalLogRelease(wl, flags, false);
    }

    /**
     * Logs wake lock acquire operations and also mocks release timer operations to reflect what
     * happens internally in the WakeLock class.
     */
    private static void internalLogAcquire(WakeLock wl, long timeout) {
        WakeLockInfo info = wlInfoMap.get(System.identityHashCode(wl));

        cancelPreviousTimerIfExists(info);

        if (timeout > 0) {
            info.releaserThread = new Thread(new TimedReleaser(wl, timeout));
            info.releaserThread.start();
        }

        onPowerManagerWakeLockAcquired(info.tag, timeout);
    }

    /**
     * Logs wake lock release operations and also mocks release timer operations to reflect what
     * happens internally in the WakeLock class.
     */
    private static void internalLogRelease(WakeLock wl, int flags, boolean autoRelease) {
        WakeLockInfo info = wlInfoMap.get(System.identityHashCode(wl));

        if (!wl.isHeld()) {
            cancelPreviousTimerIfExists(info);
        }

        onPowerManagerWakeLockReleased(info.tag, autoRelease);
    }

    private static void cancelPreviousTimerIfExists(WakeLockInfo info) {
        if (info.releaserThread != null) {
            info.releaserThread.interrupt();
            info.releaserThread = null;
        }
    }

    /**
     * A wrapper for wake lock information we need to keep track of.
     */
    private static class WakeLockInfo {
        public String tag;
        public int levelAndFlags;
        public boolean isReferenceCounted;
        public Thread releaserThread;

        public WakeLockInfo(String tag, int levelAndFlags) {
            this.tag = tag;
            this.levelAndFlags = levelAndFlags;
            this.isReferenceCounted = true;
            this.releaserThread = null;
        }
    }

    /**
     * A runnable that logs a wake lock release after timeout. This class exists because timed wake
     * locks are times and released internally and we cannot get hooks to the release operation, so
     * we have to time it ourselves.
     */
    private static class TimedReleaser implements Runnable {
        private final WakeLock mWakeLock;
        private final long mSleepTime;

        public TimedReleaser(WakeLock wakeLock, long sleepTime) {
            mWakeLock = wakeLock;
            mSleepTime = sleepTime;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(mSleepTime);
                internalLogRelease(mWakeLock, 0, true);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static native void onPowerManagerWakeLockCreated(String tag);

    private static native void onPowerManagerWakeLockAcquired(String tag, long timeout);

    private static native void onPowerManagerWakeLockReleased(String tag, boolean wasAutoRelease);
}
