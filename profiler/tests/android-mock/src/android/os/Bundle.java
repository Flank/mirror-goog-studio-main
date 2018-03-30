/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {
    private final String mStr;
    private final Map<String, Object> myMap = new HashMap<String, Object>();

    public Bundle() {
        this("");
    }

    public Bundle(String str) {
        mStr = str;
    }

    @Override
    public String toString() {
        return mStr;
    }

    public boolean containsKey(String key) {
        return myMap.containsKey(key);
    }

    public void put(String key, Object value) {
        myMap.put(key, value);
    }

    public int getInt(String key, int defaultValue) {
        if (myMap.containsKey(key) && myMap.get(key) instanceof Integer) {
            return (Integer) myMap.get(key);
        }
        return defaultValue;
    }

    public <T extends Parcelable> T getParcelable(String key) {
        Object o = myMap.get(key);
        if (o == null) {
            return null;
        }
        try {
            return (T) o;
        } catch (ClassCastException ex) {
            return null;
        }
    }
}
