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

public class LocationRequest {
    private int myPriority = 102;
    private long myInterval = 0L;
    private long myFastestInterval = 0L;
    private float mySmallestDisplacement = 0.0f;

    public int getPriority() {
        return myPriority;
    }

    public long getInterval() {
        return myInterval;
    }

    public long getFastestInterval() {
        return myFastestInterval;
    }

    public float getSmallestDisplacement() {
        return mySmallestDisplacement;
    }

    public LocationRequest setPriority(int priority) {
        myPriority = priority;
        return this;
    }

    public LocationRequest setInterval(long interval) {
        myInterval = interval;
        return this;
    }

    public LocationRequest setFastestInterval(long fastestInterval) {
        myFastestInterval = fastestInterval;
        return this;
    }

    public LocationRequest setSmallestDisplacement(float smallestDisplacement) {
        mySmallestDisplacement = smallestDisplacement;
        return this;
    }
}
