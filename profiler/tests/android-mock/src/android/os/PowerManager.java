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

package android.os;

/** PowerManager mock for tests. */
public final class PowerManager {
    public final class WakeLock {
        private int mFlags;
        private String mTag;
        private boolean mHeld;

        WakeLock(int flags, String tag) {
            mFlags = flags;
            mTag = tag;
            mHeld = false;
        }

        public void acquire() {}

        public void acquire(long timeout) {}

        public void release() {
            release(0);
        }

        public void release(int flags) {}

        public boolean isHeld() {
            return mHeld;
        }

        /**
         * This is not an actual API but just for mocking the value.
         *
         * @param isHeld new value for isHeld.
         */
        public void setHeld(boolean isHeld) {
            mHeld = isHeld;
        }
    }

    public WakeLock newWakeLock(int levelAndFlags, String tag) {
        return new WakeLock(levelAndFlags, tag);
    }

    /**
     * Same as {@link #newWakeLock(int, String)} but for testing. Different signature so this
     * doesn't trigger instrumentation.
     */
    public WakeLock newWakeLockForTesting(int levelAndFlags, String tag) {
        return newWakeLock(levelAndFlags, tag);
    }
}
