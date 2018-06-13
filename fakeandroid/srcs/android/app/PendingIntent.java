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
    private Intent myIntent;
    private Instrumentation myInstrumentation;

    public String getCreatorPackage() {
        return myCreatorPackage;
    }

    public int getCreatorUid() {
        return myCreatorUid;
    }

    private PendingIntent(Context context, Intent intent) {
        myCreatorPackage = context.getPackageName();
        myCreatorUid = context.getUserId();
        myIntent = intent;
        myInstrumentation = new Instrumentation();
    }

    public static PendingIntent getActivity(
            Context context, int requestCode, Intent intent, int flags, Bundle options) {
        return new PendingIntent(context, intent);
    }

    public static PendingIntent getService(
            Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(context, intent);
    }

    public Intent getIntent() {
        return myIntent;
    }

    public void send() {
        if (myIntent.hasActivity()) {
            myInstrumentation.callActivityOnCreate(myIntent.getActivity(), null, null);
        } else if (myIntent.hasService()) {
            myIntent.getService().onStartCommand(myIntent, 0, 0);
        }
    }
}
