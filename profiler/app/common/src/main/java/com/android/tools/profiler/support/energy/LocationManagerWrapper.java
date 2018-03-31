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
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import com.android.tools.profiler.support.util.StackTrace;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class LocationManagerWrapper {

    private static final Map<LocationListener, Integer> listenerIdMap =
            new HashMap<LocationListener, Integer>();
    private static final Map<PendingIntent, Integer> intentIdMap =
            new HashMap<PendingIntent, Integer>();

    /**
     * Wraps {@link LocationManager#requestLocationUpdates(String, long, float, LocationListener)}.
     *
     * @param locationManager the wrapped {@link LocationManager} instance, i.e. "this".
     * @param provider the provider parameter passed to the original method.
     * @param minTime the minTime parameter passed to the original method.
     * @param minDistance the minDistance parameter passed to the original method.
     * @param listener the listener parameter passed to the original method.
     */
    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            String provider,
            long minTime,
            float minDistance,
            LocationListener listener) {
        sendLocationUpdateRequestedInternal(provider, minTime, minDistance, 0, 0, listener, null);
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            long minTime,
            float minDistance,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        sendLocationUpdateRequestedInternal(
                "",
                minTime,
                minDistance,
                criteria.getAccuracy(),
                criteria.getPowerRequirement(),
                listener,
                null);
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            String provider,
            long minTime,
            float minDistance,
            LocationListener listener,
            Looper looper) {
        sendLocationUpdateRequestedInternal(provider, minTime, minDistance, 0, 0, listener, null);
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            long minTime,
            float minDistance,
            Criteria criteria,
            PendingIntent intent) {
        sendLocationUpdateRequestedInternal(
                "",
                minTime,
                minDistance,
                criteria.getAccuracy(),
                criteria.getPowerRequirement(),
                null,
                intent);
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            String provider,
            long minTime,
            float minDistance,
            PendingIntent intent) {
        sendLocationUpdateRequestedInternal(provider, minTime, minDistance, 0, 0, null, intent);
    }

    private static void sendLocationUpdateRequestedInternal(
            String provider,
            long minTime,
            float minDistance,
            int accuracy,
            int powerReq,
            LocationListener listener,
            PendingIntent intent) {
        if (listener != null) {
            if (!listenerIdMap.containsKey(listener)) {
                listenerIdMap.put(listener, EventIdGenerator.nextId());
            }
            sendListenerLocationUpdateRequested(
                    listenerIdMap.get(listener),
                    provider,
                    minTime,
                    minDistance,
                    accuracy,
                    powerReq,
                    StackTrace.getStackTrace());
        } else if (intent != null) {
            if (!intentIdMap.containsKey(intent)) {
                intentIdMap.put(intent, EventIdGenerator.nextId());
            }
            sendIntentLocationUpdateRequested(
                    intentIdMap.get(intent),
                    provider,
                    minTime,
                    minDistance,
                    accuracy,
                    powerReq,
                    intent.getCreatorPackage(),
                    intent.getCreatorUid(),
                    StackTrace.getStackTrace());
        }
    }

    /**
     * Wraps {@link LocationManager#requestSingleUpdate(String, PendingIntent)}.
     *
     * @param locationManager the wrapped {@link LocationManager} instance, i.e. "this".
     * @param provider the provider parameter passed to the original method.
     * @param intent the listener parameter passed to the original method.
     */
    public static void wrapRequestSingleUpdate(
            LocationManager locationManager, String provider, PendingIntent intent) {
        sendLocationUpdateRequestedInternal(provider, 0, 0, 0, 0, null, intent);
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager, Criteria criteria, PendingIntent intent) {
        sendLocationUpdateRequestedInternal("", 0, 0, criteria.getAccuracy(), 0, null, intent);
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager,
            String provider,
            LocationListener listener,
            Looper looper) {
        sendLocationUpdateRequestedInternal(provider, 0, 0, 0, 0, listener, null);
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        sendLocationUpdateRequestedInternal("", 0, 0, criteria.getAccuracy(), 0, listener, null);
    }

    /**
     * Wraps {@link LocationManager#removeUpdates(LocationListener)}.
     *
     * @param locationManager the wrapped {@link LocationManager} instance, i.e. "this".
     * @param listener the listener parameter passed to the original method.
     */
    public static void wrapRemoveUpdates(
            LocationManager locationManager, LocationListener listener) {
        if (listenerIdMap.containsKey(listener)) {
            sendListenerLocationUpdateRemoved(
                    listenerIdMap.get(listener), StackTrace.getStackTrace());
        }
    }

    public static void wrapRemoveUpdates(LocationManager locationManager, PendingIntent intent) {
        if (intentIdMap.containsKey(intent)) {
            sendIntentLocationUpdateRemoved(
                    intentIdMap.get(intent),
                    intent.getCreatorPackage(),
                    intent.getCreatorUid(),
                    StackTrace.getStackTrace());
        }
    }

    /**
     * Replaces the call to {@link LocationListener#onLocationChanged(Location)} with this method.
     *
     * @param listener the wrapped {@link LocationListener} instance, i.e. "this".
     * @param location the location parameter passed to the original method.
     */
    public static void wrapOnLocationChanged(LocationListener listener, Location location) {
        listener.onLocationChanged(location);
        if (listenerIdMap.containsKey(listener)) {
            sendListenerLocationChanged(
                    listenerIdMap.get(listener),
                    location.getProvider(),
                    location.getAccuracy(),
                    location.getLatitude(),
                    location.getLongitude());
        }
    }

    // Native functions to send location events to perfd.
    private static native void sendListenerLocationUpdateRequested(
            int eventId,
            String provider,
            long minTime,
            float minDistance,
            int accuracy,
            int powerReq,
            String stack);

    private static native void sendIntentLocationUpdateRequested(
            int eventId,
            String provider,
            long minTime,
            float minDistance,
            int accuracy,
            int powerReq,
            String creatorPackage,
            int creatorUid,
            String stack);

    private static native void sendListenerLocationUpdateRemoved(int eventId, String stack);

    private static native void sendIntentLocationUpdateRemoved(
            int eventId, String creatorPackage, int creatorUid, String stack);

    private static native void sendListenerLocationChanged(
            int eventId, String provider, float accuracy, double latitude, double longitude);
}
