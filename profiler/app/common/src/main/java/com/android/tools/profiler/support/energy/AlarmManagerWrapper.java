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

import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.AlarmManager.OnAlarmListener;
import android.app.PendingIntent;
import android.os.Handler;
import android.os.WorkSource;
import com.android.tools.profiler.support.util.StudioLog;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of helpers for Android {@link AlarmManager} instrumentation, used by the Energy Profiler.
 *
 * <p>{@link AlarmManager} does not provide public or protected constructor so instead of extending
 * the class we hook into each method.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class AlarmManagerWrapper {

    /** Data structure for {@link OnAlarmListener} parameters. */
    private static final class ListenerParams {
        final int id;
        final String tag;

        ListenerParams(int id, String tag) {
            this.id = id;
            this.tag = tag;
        }
    }

    private static final Map<PendingIntent, Integer> operationIdMap =
            new HashMap<PendingIntent, Integer>();
    private static final Map<OnAlarmListener, ListenerParams> listenerMap =
            new HashMap<OnAlarmListener, ListenerParams>();

    /**
     * Wraps the implementation method of various set alarm methods in {@link AlarmManager}.
     *
     * @param alarmManager the wrapped {@link AlarmManager} instance, i.e. "this".
     * @param type the type parameter passed to the original method.
     * @param triggerAtMillis the triggerAtMillis parameter passed to the original method.
     * @param windowMillis the windowMillis parameter passed to the original method.
     * @param intervalMillis the intervalMillis parameter passed to the original method.
     * @param flags the flags parameter passed to the original method.
     * @param operation the operation parameter passed to the original method.
     * @param listener the listener parameter passed to the original method.
     * @param listenerTag the listenerTag parameter passed to the original method.
     * @param targetHandler the targetHandler parameter passed to the original method.
     * @param workSource the workSource parameter passed to the original method.
     * @param alarmClock the alarmClock parameter passed to the original method.
     */
    public static void wrapSetImpl(
            AlarmManager alarmManager,
            int type,
            long triggerAtMillis,
            long windowMillis,
            long intervalMillis,
            int flags,
            PendingIntent operation,
            final OnAlarmListener listener,
            String listenerTag,
            Handler targetHandler,
            WorkSource workSource,
            AlarmClockInfo alarmClock) {
        if (operation != null) {
            if (!operationIdMap.containsKey(operation)) {
                operationIdMap.put(operation, EventIdGenerator.nextId());
            }
            sendIntentAlarmScheduled(
                    operationIdMap.get(operation),
                    type,
                    triggerAtMillis,
                    windowMillis,
                    intervalMillis,
                    operation.getCreatorPackage(),
                    operation.getCreatorUid());
        } else if (listener != null) {
            if (!listenerMap.containsKey(listener)) {
                listenerMap.put(
                        listener, new ListenerParams(EventIdGenerator.nextId(), listenerTag));
            }
            sendListenerAlarmScheduled(
                    listenerMap.get(listener).id,
                    type,
                    triggerAtMillis,
                    windowMillis,
                    intervalMillis,
                    listenerTag);
        } else {
            StudioLog.e("Invalid alarm: neither operation or listener is set.");
        }
    }

    /**
     * Wraps {@link AlarmManager#cancel(PendingIntent)}.
     *
     * @param alarmManager the wrapped {@link AlarmManager} instance, i.e. "this".
     * @param operation the operation parameter passed to the original method.
     */
    public static void wrapCancel(AlarmManager alarmManager, PendingIntent operation) {
        sendIntentAlarmCancelled(
                operationIdMap.containsKey(operation) ? operationIdMap.get(operation) : 0,
                operation.getCreatorPackage(),
                operation.getCreatorUid());
    }

    /**
     * Wraps {@link AlarmManager#cancel(OnAlarmListener)}.
     *
     * @param alarmManager the wrapped {@link AlarmManager} instance, i.e. "this".
     * @param listener the listener parameter passed to the original method.
     */
    public static void wrapCancel(AlarmManager alarmManager, OnAlarmListener listener) {
        ListenerParams params =
                listenerMap.containsKey(listener)
                        ? listenerMap.get(listener)
                        : new ListenerParams(0, "");
        sendListenerAlarmCancelled(params.id, params.tag);
    }

    /**
     * Wraps {@link OnAlarmListener#onAlarm()}.
     *
     * @param listener the wrapped {@link OnAlarmListener} instance, i.e. "this".
     */
    public static void wrapListenerOnAlarm(OnAlarmListener listener) {
        if (listenerMap.containsKey(listener)) {
            ListenerParams params = listenerMap.get(listener);
            sendListenerAlarmFired(params.id, params.tag);
        }
        listener.onAlarm();
    }

    // Native functions to send alarm events to perfd.
    private static native void sendIntentAlarmScheduled(
            int eventId,
            int type,
            long triggerMs,
            long windowMs,
            long intervalMs,
            String creatorPackage,
            int creatorUid);

    private static native void sendListenerAlarmScheduled(
            int eventId,
            int type,
            long triggerMs,
            long windowMs,
            long intervalMs,
            String listenerTag);

    private static native void sendIntentAlarmCancelled(
            int eventId, String creatorPackage, int creatorUid);

    private static native void sendListenerAlarmCancelled(int eventId, String listenerTag);

    private static native void sendListenerAlarmFired(int eventId, String listenerTag);
}
