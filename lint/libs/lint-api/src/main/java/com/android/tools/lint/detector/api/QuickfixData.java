/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

/**
 * Typed storage for quickfixes, usually used for the quickfix data parameter
 * in {@link Context#report(Issue, Location, String, Object)} or
 * {@link JavaContext#report(Issue, UElement, Location, String, Object)} etc.
 */
public class QuickfixData implements Iterable {

    private final Map<Object, Object> map = Maps.newHashMap();

    @Nullable
    public <T> T get(@NonNull Class<T> key) {
        //noinspection unchecked
        return (T) map.get(key);
    }

    @Nullable
    public Object get(@NonNull String key) {
        //noinspection unchecked
        return map.get(key);
    }

    public <T> void put(@Nullable T value) {
        if (value == null) {
            return;
        }
        Class<?> key = value.getClass();
        assert !map.containsKey(key);
        map.put(key, value);
    }

    public void put(@NonNull String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        assert !map.containsKey(key);
        map.put(key, value);
    }

    @NonNull
    public static QuickfixData create(@NonNull Object... args) {
        QuickfixData quickfixData = new QuickfixData();

        for (Object arg : args) {
            quickfixData.put(arg);
        }

        return quickfixData;
    }

    @NotNull
    @Override
    public Iterator iterator() {
        return map.values().iterator();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
