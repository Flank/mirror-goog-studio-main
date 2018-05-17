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
                    minTime,
                    minDistance,
                    accuracy,
                    powerReq,
                    0,
                    // API requestLocationUpdate is one level down of user code.
                    StackTrace.getStackTrace(1));
        } else if (intent != null) {
            if (!intentIdMap.containsKey(intent)) {
                intentIdMap.put(intent, EventIdGenerator.nextId());
            }
            sendIntentLocationUpdateRequested(
                    intentIdMap.get(intent),
                    provider,
                    minTime,
                    minTime,
                    minDistance,
                    accuracy,
                    powerReq,
                    0,
                    intent.getCreatorPackage(),
                    intent.getCreatorUid(),
                    // API requestLocationUpdate is one level down of user code.
                    StackTrace.getStackTrace(1));
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
        if (!listenerIdMap.containsKey(listener)) {
            listenerIdMap.put(listener, EventIdGenerator.nextId());
        }
        sendListenerLocationUpdateRemoved(
                // API removeUpdates is one level down of user code.
                listenerIdMap.get(listener), StackTrace.getStackTrace(1));
    }

    public static void wrapRemoveUpdates(LocationManager locationManager, PendingIntent intent) {
        if (!intentIdMap.containsKey(intent)) {
            intentIdMap.put(intent, EventIdGenerator.nextId());
        }
        sendIntentLocationUpdateRemoved(
                intentIdMap.get(intent),
                intent.getCreatorPackage(),
                intent.getCreatorUid(),
                // API removeUpdates is one level down of user code.
                StackTrace.getStackTrace(1));
    }

    /**
     * Replaces the call to {@link LocationListener#onLocationChanged(Location)} with this method.
     *
     * @param listener the wrapped {@link LocationListener} instance, i.e. "this".
     * @param location the location parameter passed to the original method.
     */
    public static void wrapOnLocationChanged(LocationListener listener, Location location) {
        listener.onLocationChanged(location);
        if (!listenerIdMap.containsKey(listener)) {
            listenerIdMap.put(listener, EventIdGenerator.nextId());
        }
        sendListenerLocationChanged(
                listenerIdMap.get(listener),
                location.getProvider(),
                location.getAccuracy(),
                location.getLatitude(),
                location.getLongitude());
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
            sendIntentLocationChanged(
                    intentIdMap.get(pendingIntent),
                    location.getProvider(),
                    location.getAccuracy(),
                    location.getLatitude(),
                    location.getLongitude(),
                    pendingIntent.getCreatorPackage(),
                    pendingIntent.getCreatorUid());
        }
    }

    // Native functions to send location events to perfd.
    public static native void sendListenerLocationUpdateRequested(
            int eventId,
            String provider,
            long interval,
            long minInterval,
            float minDistance,
            int accuracy,
            int powerReq,
            int priority,
            String stack);

    public static native void sendIntentLocationUpdateRequested(
            int eventId,
            String provider,
            long interval,
            long minInterval,
            float minDistance,
            int accuracy,
            int powerReq,
            int priority,
            String creatorPackage,
            int creatorUid,
            String stack);

    public static native void sendListenerLocationUpdateRemoved(int eventId, String stack);

    public static native void sendIntentLocationUpdateRemoved(
            int eventId, String creatorPackage, int creatorUid, String stack);

    private static native void sendListenerLocationChanged(
            int eventId, String provider, float accuracy, double latitude, double longitude);

    public static native void sendIntentLocationChanged(
            int eventId,
            String provider,
            float accuracy,
            double latitude,
            double longitude,
            String creatorPackage,
            int creatorUid);
}
