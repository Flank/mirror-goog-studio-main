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

package com.activity.energy;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import com.activity.PerfdTestActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;

public class LocationActivity extends PerfdTestActivity {

    private class DefaultLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {}
    }

    public LocationActivity() {
        super("Location Activity");
    }

    public void listenerRequestAndRemoveLocationUpdates() {
        LocationManager locationManager = new LocationManager();
        LocationListener listener = new DefaultLocationListener();
        locationManager.requestLocationUpdates("gps", 1000, 100.0f, listener);
        Location location = new Location("network");
        location.setAccuracy(100.0f);
        location.setLatitude(30.0);
        location.setLongitude(60.0);
        locationManager.changeLocation(location);
        locationManager.removeUpdates(listener);
        System.out.println("LISTENER LOCATION UPDATES");
    }

    public void intentRequestAndRemoveLocationUpdates() {
        LocationManager locationManager = new LocationManager();
        PendingIntent intent =
                PendingIntent.getService(
                        new Context("com.example", 123), 0, new Intent(MyService.class), 0);
        Criteria criteria = new Criteria();
        criteria.setAccuracy(2); // ACCURACY_CORASE
        locationManager.requestLocationUpdates(2000, 50.0f, criteria, intent);
        Location location = new Location("passive");
        location.setAccuracy(50.0f);
        location.setLatitude(60.0f);
        location.setLongitude(30.0f);
        locationManager.changeLocation(location);
        locationManager.removeUpdates(intent);
        System.out.println("INTENT LOCATION UPDATES");
    }

    public void listenerRequestCoarseLocation() {
        LocationManager locationManager = new LocationManager();
        Criteria criteria = new Criteria();
        criteria.setAccuracy(1); // ACCURACY_FINE
        locationManager.requestLocationUpdates(
                100, 200.0f, criteria, new DefaultLocationListener(), new Looper());
    }

    public void listenerRequestNetworkProvider() {
        LocationManager locationManager = new LocationManager();
        locationManager.requestLocationUpdates(
                "network", 200, 1.5f, new DefaultLocationListener(), new Looper());
    }

    public void intentRequestPassiveProvider() {
        LocationManager locationManager = new LocationManager();
        PendingIntent intent =
                PendingIntent.getService(
                        new Context("foo.bar", 321), 0, new Intent(MyService.class), 0);
        locationManager.requestLocationUpdates("passive", 500, 10.0f, intent);
    }

    public void intentSingleRequestLowPower() {
        LocationManager locationManager = new LocationManager();
        Criteria criteria = new Criteria();
        criteria.setAccuracy(0);
        criteria.setPowerRequirement(1); // POWER_LOW
        locationManager.requestSingleUpdate(
                criteria,
                PendingIntent.getService(new Context("p", 0), 0, new Intent(MyService.class), 0));
    }

    public void gmsIntentRequestAndRemoveLocationUpdates() {
        Context context = new Context("com.google.gms", 1);
        FusedLocationProviderClient client = new FusedLocationProviderClient(context);
        PendingIntent intent = PendingIntent.getService(context, 0, new Intent(MyService.class), 0);
        client.requestLocationUpdates(
                new LocationRequest()
                        .setPriority(100)
                        .setInterval(100)
                        .setFastestInterval(10)
                        .setSmallestDisplacement(1.0f),
                intent);
        Location location = new Location("gps");
        location.setAccuracy(10.0f);
        location.setLatitude(45.0);
        location.setLongitude(45.0);
        client.changeLocation(location);
        client.removeLocationUpdates(intent);
        System.out.println("GMS INTENT LOCATION UPDATES");
    }

    public void updateLocationWithoutStartingRequest() {
        LocationManager locationManager = new LocationManager();
        LocationListener listener = new DefaultLocationListener();
        // Use a direct setter instead of requesting location updates.
        locationManager.setListener(listener);
        Location location = new Location("network");
        location.setAccuracy(100.0f);
        location.setLatitude(30.0);
        location.setLongitude(60.0);
        locationManager.changeLocation(location);
        System.out.println("LISTENER LOCATION UPDATES");
    }
}
