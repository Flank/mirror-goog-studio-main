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

package android.content;

import android.app.Activity;
import android.app.IntentService;
import android.os.Bundle;
import android.os.Parcelable;

public class Intent {
    private Bundle myExtras;
    private Class<?> myClazz;

    public Intent(Class<?> clazz) {
        myClazz = clazz;
    }

    public Activity getActivity() {
        try {
            Activity activity = (Activity) myClazz.newInstance();
            activity.setIntent(this);
            return activity;
        } catch (Exception e) {
            return null;
        }
    }

    public IntentService getService() {
        try {
            return (IntentService) myClazz.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasActivity() {
        return Activity.class.isAssignableFrom(myClazz);
    }

    public boolean hasService() {
        return IntentService.class.isAssignableFrom(myClazz);
    }

    public Intent putExtra(String name, Object value) {
        if (myExtras == null) {
            myExtras = new Bundle();
        }
        myExtras.put(name, value);
        return this;
    }

    public Intent putExtra(String name, int value) {
        if (myExtras == null) {
            myExtras = new Bundle();
        }
        myExtras.put(name, value);
        return this;
    }

    public int getIntExtra(String name, int defaultValue) {
        return myExtras == null ? defaultValue : myExtras.getInt(name, defaultValue);
    }

    public <T extends Parcelable> T getParcelableExtra(String key) {
        return myExtras == null ? null : myExtras.<T>getParcelable(key);
    }

    public boolean hasExtra(String name) {
        return myExtras.containsKey(name);
    }

    public boolean filterEquals(Intent other) {
        return equals(other);
    }

    public int filterHashCode() {
        return hashCode();
    }
}
