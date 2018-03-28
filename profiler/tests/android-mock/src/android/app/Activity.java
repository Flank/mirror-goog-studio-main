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
package android.app;

import android.app.job.JobScheduler;
import android.content.Intent;
import android.mock.MockWindowManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.view.Window;
import android.view.WindowManager;

/** Activity mock for tests */
public class Activity {

    private String myName;
    private Intent myIntent;

    public Activity(String name) {
        this(name, null);
    }

    public Activity(String name, Intent intent) {
        myName = name;
        myIntent = intent;
    }

    public String getLocalClassName() {
        return myName;
    }

    public Window getWindow() {
        return new Window();
    }

    public WindowManager getWindowManager() {
        return new MockWindowManager();
    }

    public PowerManager getPowerManager() {
        return new PowerManager();
    }

    public AlarmManager getAlarmManager() {
        return new AlarmManager();
    }

    public JobScheduler getJobScheduler() {
        return new JobSchedulerImpl();
    }

    public Intent getIntent() {
        return myIntent;
    }

    public void performCreate(Bundle savedInstance, PersistableBundle persistentState) {}
}
