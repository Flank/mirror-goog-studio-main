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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PendingIntent {
    private String myCreatorPackage;
    private int myCreatorUid;
    private Activity myActivity;
    private Intent myIntent;

    public String getCreatorPackage() {
        return myCreatorPackage;
    }

    public int getCreatorUid() {
        return myCreatorUid;
    }

    public static PendingIntent getActivity(
            Context context, int requestCode, Intent intent, int flags, Bundle options) {
        PendingIntent pendingIntent = new PendingIntent();
        pendingIntent.myCreatorPackage = context.getPackageName();
        pendingIntent.myCreatorUid = context.getUserId();
        pendingIntent.myActivity = new Activity("MyActivity", intent);
        pendingIntent.myIntent = intent;
        return pendingIntent;
    }

    public void sendAlarm() {
        if (myActivity != null) {
            myIntent.getExtras().put("android.intent.extra.ALARM_COUNT", 1);
            myActivity.performCreate(null, null);
        }
    }
}
