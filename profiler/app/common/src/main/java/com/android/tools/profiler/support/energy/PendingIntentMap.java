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

import android.app.PendingIntent;
import android.content.Intent;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains a mapping from {@link Intent} to {@link PendingIntent} so we when an Intent is sent we
 * can trace back to its PendingIntent (if any) for event tracking.
 */
public final class PendingIntentMap {
    private static final Map<IntentWrapper, PendingIntent> intentMap =
            new HashMap<IntentWrapper, PendingIntent>();

    /**
     * Wraps an {@link Intent} and overrides its {@code equals} and {@code hashCode} methods so we
     * can use it as a HashMap key. Two intents are considered equal iff {@link
     * Intent#filterEquals(Intent)} returns true.
     */
    private static class IntentWrapper {
        private final Intent mIntent;

        private IntentWrapper(Intent intent) {
            mIntent = intent;
        }

        static IntentWrapper wrap(Intent intent) {
            return new IntentWrapper(intent);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IntentWrapper
                    && mIntent.filterEquals(((IntentWrapper) obj).mIntent);
        }

        @Override
        public int hashCode() {
            return mIntent.filterHashCode();
        }
    }

    public boolean containsIntent(Intent intent) {
        return intentMap.containsKey(IntentWrapper.wrap(intent));
    }

    public void putIntent(Intent intent, PendingIntent pendingIntent) {
        intentMap.put(IntentWrapper.wrap(intent), pendingIntent);
    }

    public PendingIntent getPendingIntent(Intent intent) {
        return intentMap.get(IntentWrapper.wrap(intent));
    }
}
