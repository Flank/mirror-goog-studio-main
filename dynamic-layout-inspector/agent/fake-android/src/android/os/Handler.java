/*
 * Copyright (C) 2020 The Android Open Source Project
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

import androidx.annotation.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fake implementation of Handler.
 *
 * <p>During testing this is used instead of the version in android.jar, since all the methods there
 * are stubbed out.
 */
public class Handler {
    private final Looper mLooper;
    private final Map<Long, Runnable> mMessages;
    private long mTime;

    public Handler(Looper looper) {
        mLooper = looper;
        mMessages = new HashMap<>();
    }

    public Looper getLooper() {
        return mLooper;
    }

    @SuppressWarnings("MethodMayBeStatic")
    public boolean post(Runnable runnable) {
        runnable.run();
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean postDelayed(Runnable runnable, long delay) {
        mMessages.put(mTime + delay, runnable);
        return true;
    }

    @SuppressWarnings("unused")
    public final void removeCallbacksAndMessages(Object token) {
        mMessages.clear();
    }

    /** This method does't exist on the real Handler, but is used in tests. */
    @VisibleForTesting
    public void advance(long delta) {
        mTime += delta;
        Set<Long> times =
                mMessages.keySet().stream()
                        .filter(time -> time <= mTime)
                        .collect(Collectors.toSet());
        times.forEach(time -> mMessages.remove(time).run());
    }

    /** This method does't exist on the real Handler, but is used in tests. */
    @VisibleForTesting
    public int waitingMessages() {
        return mMessages.size();
    }
}
