/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.sql;

import java.lang.reflect.Method;
import java.util.Collection;

public class RoomInvalidationTrackerHelper {

    private static Method getRefreshMethod(Object invTracker) {
        try {
            return invTracker.getClass().getMethod("refreshVersionsAsync");
        } catch (Throwable th) {
            log("cannot find refresh method " + th);
        }
        return null;
    }

    private static void refreshTracker(Object invTracker) {
        Method method = getRefreshMethod(invTracker);
        if (method != null) {
            try {
                method.invoke(invTracker);
                log("called refresh on " + invTracker);
            } catch (Throwable th) {
                log("error while invoking refresh triggers");
            }
        } else {
            log("cannot find refresh method, skipping");
        }
    }

    public static void invokeTrackers(Collection<Object> invalidationTrackers) {
        log("invoking triggers...");
        for (Object invTracker : invalidationTrackers) {
            refreshTracker(invTracker);
        }
    }

    private static void log(Object msg) {
        System.out.println("INV_TRACKER_LOG:" + msg.toString());
    }
}
