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

import android.app.PendingIntent;
import android.os.Looper;
import android.os.Message;

public class LocationManager {
    /**
     * This class emulates the implementation detail relied on by our byte-code instrumentation in
     * the profiling agent.
     */
    private class ListenerTransport {
        private LocationListener mListener;

        ListenerTransport(LocationListener listener) {
            mListener = listener;
        }

        private void _handleMessage(Message message) {
            mListener.onLocationChanged((Location) message.obj);
        }
    }

    private ListenerTransport mListenerTransport;
    private PendingIntent mIntent;

    /** Directly sets LocationListener. For testing only. */
    public void setListener(LocationListener listener) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void requestLocationUpdates(
            String provider, long minTime, float minDistance, LocationListener listener) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void requestLocationUpdates(
            long minTime,
            float minDistance,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void requestLocationUpdates(
            String provider,
            long minTime,
            float minDistance,
            LocationListener listener,
            Looper looper) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void requestLocationUpdates(
            long minTime, float minDistance, Criteria criteria, PendingIntent intent) {
        mIntent = intent;
    }

    public void requestLocationUpdates(
            String provider, long minTime, float minDistance, PendingIntent intent) {
        mIntent = intent;
    }

    public void requestSingleUpdate(String provider, PendingIntent intent) {
        mIntent = intent;
    }

    public void requestSingleUpdate(Criteria criteria, PendingIntent intent) {
        mIntent = intent;
    }

    public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
        mListenerTransport = new ListenerTransport(listener);
    }

    public void removeUpdates(LocationListener listener) {}

    public void removeUpdates(PendingIntent intent) {}

    /** Fake method to trigger location changed event. */
    public void changeLocation(Location location) {
        if (mListenerTransport != null) {
            Message message = new Message();
            message.obj = location;
            mListenerTransport._handleMessage(message);
        } else if (mIntent != null) {
            mIntent.getIntent().putExtra("location", location);
            mIntent.send();
        }
    }
}
