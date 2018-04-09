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

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import com.activity.PerfdTestActivity;

public class AlarmActivity extends PerfdTestActivity {
    public AlarmActivity() {
        super("Alarm Activity");
    }

    public void setAndCancelIntentAlarm() {
        AlarmManager alarmManager = getAlarmManager();
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        new Context("com.example", 1), 0, new Intent(AlarmActivity.class), 0, null);
        alarmManager.set(0x2, 1000, pendingIntent);
        alarmManager.cancel(pendingIntent);
        System.out.println("INTENT ALARM CANCELLED");
    }

    public void setAndCancelListenerAlarm() {
        AlarmManager alarmManager = getAlarmManager();
        OnAlarmListener listener =
                new OnAlarmListener() {
                    @Override
                    public void onAlarm() {}
                };
        alarmManager.set(0x0, 2000, "foo", listener, new Handler());
        alarmManager.cancel(listener);
        System.out.println("LISTENER ALARM CANCELLED");
    }

    public void fireIntentAlarm() {
        AlarmManager alarmManager = getAlarmManager();
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        new Context("foo.bar", 2), 0, new Intent(AlarmActivity.class), 0, null);
        alarmManager.setRepeating(0x0, 1000, 60000, pendingIntent);
        alarmManager.fire();
        System.out.println("INTENT ALARM FIRED");
    }

    public void fireListenerAlarm() {
        AlarmManager alarmManager = getAlarmManager();
        OnAlarmListener listener =
                new OnAlarmListener() {
                    @Override
                    public void onAlarm() {
                        System.out.println("LISTENER ALARM FIRED");
                    }
                };
        alarmManager.set(0x0, 1000, "bar", listener, new Handler());
        alarmManager.fire();
    }

    public void setAndCancelNonWakeupAlarm() {
        AlarmManager alarmManager = getAlarmManager();
        OnAlarmListener listener =
                new OnAlarmListener() {
                    @Override
                    public void onAlarm() {}
                };
        alarmManager.set(0x1, 1000, "foo", listener, null);
        alarmManager.cancel(listener);
        System.out.println("NON-WAKEUP ALARM");
    }
}
