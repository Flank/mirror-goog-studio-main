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

package com.android.tools.profiler.support.energy.gms;

import android.app.PendingIntent;
import android.os.Looper;
import com.android.tools.profiler.support.energy.EventIdGenerator;
import com.android.tools.profiler.support.energy.LocationManagerWrapper;
import com.android.tools.profiler.support.util.StackTrace;
import com.android.tools.profiler.support.util.StudioLog;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class FusedLocationProviderClientWrapper {
    private static final String LOCATION_PROVIDER_NAME = "fused";
    private static final Map<LocationCallback, Integer> callbackIdMap =
            new HashMap<LocationCallback, Integer>();
    private static final Map<PendingIntent, Integer> intentIdMap =
            new HashMap<PendingIntent, Integer>();

    /**
     * Entry hook on {@code FusedLocationProviderClient.requestLocationUpdate} (callback version).
     * Passes "this" as an object so we get the class loader for GMS classes.
     *
     * @param client the wrapped FusedLocationProviderClient client, i.e. "this".
     * @param request the request parameter passed to the original method.
     * @param callback the callback parameter passed to the original method.
     * @param looper the looper parameter passed to the original method.
     */
    public static void wrapRequestLocationUpdates(
            Object client, LocationRequest request, LocationCallback callback, Looper looper) {
        sendLocationUpdatesInternal(client, request, callback, null);
    }

    /**
     * Entry hook on {@code FusedLocationProviderClient.requestLocationUpdate} (PendingIntent
     * version). Passes "this" as an object so we get the class loader for GMS classes.
     *
     * @param client the wrapped FusedLocationProviderClient client, i.e. "this".
     * @param request the request parameter passed to the original method.
     * @param callbackIntent the callbackIntent parameter passed to the original method.
     */
    public static void wrapRequestLocationUpdates(
            Object client, LocationRequest request, PendingIntent callbackIntent) {
        sendLocationUpdatesInternal(client, request, null, callbackIntent);
    }

    private static void sendLocationUpdatesInternal(
            Object client,
            LocationRequest request,
            LocationCallback callback,
            PendingIntent pendingIntent) {
        try {
            Class<?> requestClass =
                    client.getClass()
                            .getClassLoader()
                            .loadClass("com.google.android.gms.location.LocationRequest");
            long interval = (Long) requestClass.getMethod("getInterval").invoke(request);
            long fastestInterval =
                    (Long) requestClass.getMethod("getFastestInterval").invoke(request);
            float smallestDisplacement =
                    (Float) requestClass.getMethod("getSmallestDisplacement").invoke(request);
            int priority = (Integer) requestClass.getMethod("getPriority").invoke(request);
            if (callback != null) {
                if (!callbackIdMap.containsKey(callback)) {
                    callbackIdMap.put(callback, EventIdGenerator.nextId());
                }
                LocationManagerWrapper.sendListenerLocationUpdateRequested(
                        callbackIdMap.get(callback),
                        LOCATION_PROVIDER_NAME,
                        interval,
                        interval,
                        smallestDisplacement,
                        0,
                        0,
                        priority,
                        // API requestLocationUpdates is one level down of user code.
                        StackTrace.getStackTrace(1));
            } else if (pendingIntent != null) {
                if (!intentIdMap.containsKey(pendingIntent)) {
                    intentIdMap.put(pendingIntent, EventIdGenerator.nextId());
                }
                LocationManagerWrapper.sendIntentLocationUpdateRequested(
                        intentIdMap.get(pendingIntent),
                        "fused",
                        interval,
                        fastestInterval,
                        smallestDisplacement,
                        0,
                        0,
                        priority,
                        pendingIntent.getCreatorPackage(),
                        pendingIntent.getCreatorUid(),
                        // API requestLocationUpdates is one level down of user code.
                        StackTrace.getStackTrace(1));
            }
        } catch (Exception e) {
            StudioLog.e("Could not send GMS LocationUpdateRequested event", e);
        }
    }

    /**
     * Entry hook on {@code FusedLocationProviderClient.removeLocationUpdate} (callback version).
     *
     * @param client the wrapped FusedLocationProviderClient client, i.e. "this".
     * @param callback the callback parameter passed to the original method.
     */
    public static void wrapRemoveLocationUpdates(Object client, LocationCallback callback) {
        if (callbackIdMap.containsKey(callback)) {
            LocationManagerWrapper.sendListenerLocationUpdateRemoved(
                    callbackIdMap.get(callback),
                    // API removeUpdates is one level down of user code.
                    StackTrace.getStackTrace(1));
        }
    }

    /**
     * Entry hook on {@code FusedLocationProviderClient.removeLocationUpdate} (PendingIntent
     * version).
     *
     * @param client the wrapped FusedLocationProviderClient client, i.e. "this".
     * @param callbackIntent the callbackIntent parameter passed to the original method.
     */
    public static void wrapRemoveLocationUpdates(Object client, PendingIntent callbackIntent) {
        if (intentIdMap.containsKey(callbackIntent)) {
            LocationManagerWrapper.sendIntentLocationUpdateRemoved(
                    intentIdMap.get(callbackIntent),
                    callbackIntent.getCreatorPackage(),
                    callbackIntent.getCreatorUid(),
                    // API removeUpdates is one level down of user code.
                    StackTrace.getStackTrace(1));
        }
    }
}
