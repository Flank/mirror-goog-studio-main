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

package android.app;

import android.os.Handler;
import android.os.WorkSource;

public class AlarmManager {
    public interface OnAlarmListener {
        void onAlarm();
    }

    public static final class AlarmClockInfo {}

    final class ListenerWrapper implements Runnable {
        final OnAlarmListener mListener;

        public ListenerWrapper(OnAlarmListener listener) {
            mListener = listener;
        }

        @Override
        public void run() {
            mListener.onAlarm();
        }
    }

    private ListenerWrapper mListenerWrapper;
    private PendingIntent mOperation;

    private void setImpl(
            int type,
            long triggerAtMillis,
            long windowMillis,
            long intervalMillis,
            int flags,
            PendingIntent operation,
            OnAlarmListener listener,
            String listenerTag,
            Handler targetHandler,
            WorkSource workSource,
            AlarmClockInfo alarmClock) {
        if (operation != null) {
            mOperation = operation;
        } else if (listener != null) {
            mListenerWrapper = new ListenerWrapper(listener);
        }
    }

    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, -1, 0, 0, operation, null, null, null, null, null);
    }

    public void set(
            int type,
            long triggerAtMillis,
            String tag,
            OnAlarmListener listener,
            Handler targetHandler) {
        setImpl(type, triggerAtMillis, -1, 0, 0, null, listener, tag, targetHandler, null, null);
    }

    public void cancel(PendingIntent operation) {}

    public void cancel(OnAlarmListener listener) {}

    /** This is not really how an alarm is fired but we'll pretend for testing purposes. */
    public void fire() {
        if (mOperation != null) {
            // TODO: Send intent.
        } else if (mListenerWrapper != null) {
            new Thread(mListenerWrapper).start();
        }
    }
}
