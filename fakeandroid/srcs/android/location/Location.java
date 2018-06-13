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

package android.location;

import android.os.Parcelable;

public class Location implements Parcelable {
    private String myProvider;
    private float myAccuracy = 0.0f;
    private double myLatitude = 0.0;
    private double myLongitude = 0.0;

    public Location(String provider) {
        myProvider = provider;
    }

    public String getProvider() {
        return myProvider;
    }

    public float getAccuracy() {
        return myAccuracy;
    }

    public void setAccuracy(float accuracy) {
        myAccuracy = accuracy;
    }

    public double getLatitude() {
        return myLatitude;
    }

    public void setLatitude(double latitude) {
        myLatitude = latitude;
    }

    public double getLongitude() {
        return myLongitude;
    }

    public void setLongitude(double longitude) {
        myLongitude = longitude;
    }
}
