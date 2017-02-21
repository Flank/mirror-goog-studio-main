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

package com.android.tools.device.internal;

import com.android.annotations.NonNull;

/**
 * {@link ScopedThreadName} allows setting the current thread's name only within a certain scope of
 * control flow. Use as:
 *
 * <pre>
 * ScopedThreadName.create("new-thread-name").run(() -> {
 *      // ..
 * })
 * </pre>
 */
public class ScopedThreadName {
    private final String threadName;

    public static ScopedThreadName create(@NonNull String threadName) {
        return new ScopedThreadName(threadName);
    }

    private ScopedThreadName(@NonNull String threadName) {
        this.threadName = threadName;
    }

    public void run(@NonNull Runnable r) {
        String originalName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(threadName);
        } catch (SecurityException ignored) {
        }

        r.run();

        try {
            Thread.currentThread().setName(originalName);
        } catch (SecurityException ignored) {
        }
    }
}
