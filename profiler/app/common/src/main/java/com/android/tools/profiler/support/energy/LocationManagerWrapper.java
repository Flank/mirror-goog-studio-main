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
import com.android.tools.profiler.support.util.StudioLog;

public final class LocationManagerWrapper {

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
        StudioLog.v("requestLocationUpdates");
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            long minTime,
            float minDistance,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        StudioLog.v("requestLocationUpdates");
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            String provider,
            long minTime,
            float minDistance,
            LocationListener listener,
            Looper looper) {
        StudioLog.v("requestLocationUpdates");
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            long minTime,
            float minDistance,
            Criteria criteria,
            PendingIntent intent) {
        StudioLog.v("requestLocationUpdates");
    }

    public static void wrapRequestLocationUpdates(
            LocationManager locationManager,
            String provider,
            long minTime,
            float minDistance,
            PendingIntent intent) {
        StudioLog.v("requestLocationUpdates");
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
        StudioLog.v("requestSingleUpdate");
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager, Criteria criteria, PendingIntent intent) {
        StudioLog.v("requestSingleUpdate");
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager,
            String provider,
            LocationListener listener,
            Looper looper) {
        StudioLog.v("requestSingleUpdate");
    }

    public static void wrapRequestSingleUpdate(
            LocationManager locationManager,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        StudioLog.v("requestSingleUpdate");
    }

    /**
     * Wraps {@link LocationManager#removeUpdates(LocationListener)}.
     *
     * @param locationManager the wrapped {@link LocationManager} instance, i.e. "this".
     * @param listener the listener parameter passed to the original method.
     */
    public static void wrapRemoveUpdates(
            LocationManager locationManager, LocationListener listener) {
        StudioLog.v("removeLocationUpdates");
    }

    public static void wrapRemoveUpdates(LocationManager locationManager, PendingIntent intent) {
        StudioLog.v("removeLocationUpdates");
    }

    /**
     * Replaces the call to {@link LocationListener#onLocationChanged(Location)} with this method.
     *
     * @param listener the wrapped {@link LocationListener} instance, i.e. "this".
     * @param location the location parameter passed to the original method.
     */
    public static void wrapOnLocationChanged(LocationListener listener, Location location) {
        StudioLog.v("onLocationChanged");
        listener.onLocationChanged(location);
    }
}
