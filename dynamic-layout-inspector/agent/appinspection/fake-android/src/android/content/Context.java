/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.content;

import android.content.res.Resources;
import android.hardware.SensorManager;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import androidx.annotation.VisibleForTesting;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("MethodMayBeStatic")
public class Context {
    // Only for tests - doesn't exist in the framework
    private final AtomicInteger mViewIdGenerator = new AtomicInteger(0);

    private final String mPackageName;
    private final Resources mResources;

    @VisibleForTesting public SensorManager sensorManager = new SensorManager();

    @VisibleForTesting public WindowManager windowManager = new WindowManagerImpl();

    @VisibleForTesting
    public Context(String packageName, Resources resources) {
        mPackageName = packageName;
        mResources = resources;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Resources getResources() {
        return mResources;
    }

    public int getThemeResId() {
        return 0;
    }

    public <T> T getSystemService(Class<T> serviceClass) {
        if (serviceClass.equals(SensorManager.class)) {
            //noinspection unchecked
            return (T) sensorManager;
        }
        if (serviceClass.equals(WindowManager.class)) {
            //noinspection unchecked
            return (T) windowManager;
        }
        return null;
    }

    // Only for tests - doesn't exist in the framework
    @VisibleForTesting
    public int generateViewId() {
        return mViewIdGenerator.addAndGet(1);
    }
}
