/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.profiler.support.profilers;

/** Sends custom events recorded from the user's app to perfd. */
public class CustomEventProfiler implements ProfilerComponent {

    // Native activity function to send recorded events to perfd.
    private static native void sendRecordedEvent(String eventName, int value, int hashCode);

    /**
     * Called when a "recordEvent" is triggered in the user's code, with an entry hook in
     * android_user_counter_transform.h
     *
     * @param eventProfiler pointer to the EventProfiler class object in the user codespace
     * @param eventName name of the event to record, whose hash will be used as the unique id for
     *     the event
     * @param val value of the event to record
     */
    public static void onRecordEventEnter(Object eventProfiler, String eventName, int val) {
        sendRecordedEvent(eventName, val, eventName.hashCode());
    }
}
