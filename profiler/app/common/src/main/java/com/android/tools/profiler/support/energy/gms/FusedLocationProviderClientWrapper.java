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
import android.location.Location;
import android.os.Looper;
import com.android.tools.profiler.support.energy.EnergyUtils;
import com.android.tools.profiler.support.energy.LocationManagerWrapper;
import com.android.tools.profiler.support.energy.PendingIntentWrapper;
import com.android.tools.profiler.support.util.StackTrace;
import com.android.tools.profiler.support.util.StudioLog;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class FusedLocationProviderClientWrapper {
    private static final String LOCATION_PROVIDER_NAME = "fused";
    private static final Map<LocationCallback, Long> callbackIdMap =
            new HashMap<LocationCallback, Long>();
    private static final Map<PendingIntent, Long> intentIdMap = new HashMap<PendingIntent, Long>();

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
        long timestamp = EnergyUtils.getCurrentTime();
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
                    callbackIdMap.put(callback, EnergyUtils.nextId());
                }
                LocationManagerWrapper.sendListenerLocationUpdateRequested(
                        timestamp,
                        callbackIdMap.get(callback),
                        LOCATION_PROVIDER_NAME,
                        interval,
                        fastestInterval,
                        smallestDisplacement,
                        0,
                        0,
                        priority,
                        // API requestLocationUpdates is one level down of user code.
                        StackTrace.getStackTrace(1));
            } else if (pendingIntent != null) {
                if (!intentIdMap.containsKey(pendingIntent)) {
                    intentIdMap.put(pendingIntent, EnergyUtils.nextId());
                }
                LocationManagerWrapper.sendIntentLocationUpdateRequested(
                        timestamp,
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
        long timestamp = EnergyUtils.getCurrentTime();
        if (!callbackIdMap.containsKey(callback)) {
            callbackIdMap.put(callback, EnergyUtils.nextId());
        }
        LocationManagerWrapper.sendListenerLocationUpdateRemoved(
                timestamp,
                callbackIdMap.get(callback),
                // API removeUpdates is one level down of user code.
                StackTrace.getStackTrace(1));
    }

    /**
     * Entry hook on {@code FusedLocationProviderClient.removeLocationUpdate} (PendingIntent
     * version).
     *
     * @param client the wrapped FusedLocationProviderClient client, i.e. "this".
     * @param callbackIntent the callbackIntent parameter passed to the original method.
     */
    public static void wrapRemoveLocationUpdates(Object client, PendingIntent callbackIntent) {
        long timestamp = EnergyUtils.getCurrentTime();
        if (!intentIdMap.containsKey(callbackIntent)) {
            intentIdMap.put(callbackIntent, EnergyUtils.nextId());
        }
        LocationManagerWrapper.sendIntentLocationUpdateRemoved(
                timestamp,
                intentIdMap.get(callbackIntent),
                callbackIntent.getCreatorPackage(),
                callbackIntent.getCreatorUid(),
                // API removeUpdates is one level down of user code.
                StackTrace.getStackTrace(1));
    }

    /**
     * Sends the location-changed event if the given {@link PendingIntent} exists in the map.
     *
     * <p>Location change intents are sent from other components of the system (e.g. Activity) so
     * this is called by {@link PendingIntentWrapper} when there is a potential match.
     *
     * @param pendingIntent the PendingIntent that was used in scheduling the alarm.
     * @param location the location data stored in the intent extras.
     */
    public static void sendIntentLocationChangedIfExists(
            PendingIntent pendingIntent, Location location) {
        if (intentIdMap.containsKey(pendingIntent)) {
            LocationManagerWrapper.sendIntentLocationChanged(
                    EnergyUtils.getCurrentTime(),
                    intentIdMap.get(pendingIntent),
                    location.getProvider(),
                    location.getAccuracy(),
                    location.getLatitude(),
                    location.getLongitude(),
                    pendingIntent.getCreatorPackage(),
                    pendingIntent.getCreatorUid());
        }
    }
}
