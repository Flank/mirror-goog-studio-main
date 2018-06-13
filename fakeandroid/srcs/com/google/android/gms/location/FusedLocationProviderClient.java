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

package com.google.android.gms.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import com.google.android.gms.tasks.Task;

public class FusedLocationProviderClient {
    private static final String EXTRA_LOCATION_RESULT =
            "com.google.android.gms.location.EXTRA_LOCATION_RESULT";

    private PendingIntent myPendingIntent;

    public FusedLocationProviderClient(Context context) {}

    public Task requestLocationUpdates(LocationRequest request, PendingIntent pendingIntent) {
        myPendingIntent = pendingIntent;
        return null;
    }

    public Task removeLocationUpdates(PendingIntent pendingIntent) {
        return null;
    }

    /** Fake method to trigger location changed event. */
    public void changeLocation(Location location) {
        if (myPendingIntent != null) {
            myPendingIntent
                    .getIntent()
                    .putExtra(EXTRA_LOCATION_RESULT, new LocationResult(location));
            myPendingIntent.send();
        }
    }
}
