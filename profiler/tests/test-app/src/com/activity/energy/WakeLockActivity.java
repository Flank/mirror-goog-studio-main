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

import android.os.PowerManager;
import com.activity.PerfdTestActivity;

public class WakeLockActivity extends PerfdTestActivity {
    public WakeLockActivity() {
        super("WakeLock Activity");
    }

    public void runAcquireAndRelease() {
        PowerManager.WakeLock wakeLock = getPowerManager().newWakeLock(0x00000001, "Bar");
        wakeLock.setHeld(true);
        wakeLock.acquire(1000);
        wakeLock.setHeld(false);
        wakeLock.release(0x00000001);
        System.out.println("WAKE LOCK RELEASED");
    }

    public void runAcquireWithoutNewWakeLock() {
        PowerManager powerManager = getPowerManager();
        // Use non-public constructor to skip newWakeLock call.
        PowerManager.WakeLock wakeLock = powerManager.newWakeLockForTesting(1, "Foo");
        wakeLock.acquire();
        System.out.println("WAKE LOCK ACQUIRED");
    }
}
