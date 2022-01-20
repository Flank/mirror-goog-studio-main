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
package com.android.tools.deploy.liveedit.backported;

import java.util.Collections;
import java.util.HashMap;

public class Map {
    private Map() {}

    public static <K, V> java.util.Map<K, V> copyOf(java.util.Map<? extends K, ? extends V> map) {
        // TODO Use java 9 Map.ofEntries method when it becomes available.
        // return java.util.Map.ofEntries(map.entrySet().toArray(new java.util.Map.Entry[0]));
        java.util.Map copy = new HashMap(map);
        return Collections.unmodifiableMap(copy);
    }
}
