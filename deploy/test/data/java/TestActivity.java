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
package app;

import android.app.Activity;
import android.app.ActivityThread;
import pkg.KotlinCompanionTarget;
import pkg.KotlinCoroutineTarget;
import pkg.KotlinFailedTarget;
import pkg.KotlinSimpleTarget;

public class TestActivity extends Activity {

    private static int counter = 0;

    public static void resetCounter() {
        counter = 0;
    }

    public static void incrementCounter() {
        counter++;
    }

    public static void updateAppInfo() {
        ActivityThread.currentActivityThread()
                .getApplicationThread()
                .scheduleApplicationInfoChanged(null);
    }

    public static void printCounter() {
        System.out.println("TestActivity.counter = " + counter);
    }

    public TestActivity() {
        super("HotSwap Test Activity");
        resetCounter();
    }

    public void getStatus() {
        System.out.println(Target.getStatus());
    }

    public void getClassInitializerStatus() {
        System.out.println(new ClinitTarget().getStatus());
    }

    public void getFailedTargetStatus() {
        System.out.println(FailedTarget.getStatus());
    }

    public void getKotlinCoroutineTargetStatus() {
        System.out.println(new KotlinCoroutineTarget().getStatus());
    }

    public void getKotlinSimpleTargetStatus() {
        System.out.println(new KotlinSimpleTarget().getStatus());
    }

    public void getKotlinFailedTargetStatus() {
        System.out.println(new KotlinFailedTarget().getStatus());
    }

    public void getKotlinCompanionTargetStatus() {
        System.out.println(new KotlinCompanionTarget().getStatus());
    }

    public void getLambdaTargetStatus() {
        System.out.println(new LambdaTarget().getStatus());
    }

    public void getLambdaFailedTargetStatus() {
        System.out.println(new LambdaFailedTarget().getStatus());
    }

}
