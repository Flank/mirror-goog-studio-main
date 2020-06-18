/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Fake implementation of HandlerThread, that does nothing.
 *
 * <p>During testing this is used instead of the version in android.jar, since all the methods there
 * are stubbed out.
 */
public class HandlerThread extends Thread {
    private final Looper mLooper;

    @SuppressWarnings("unused")
    public HandlerThread(String name) {
        mLooper = Looper.myLooper();
    }

    @Override
    public synchronized void start() {}

    public Looper getLooper() {
        return mLooper;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedReturnValue"})
    public boolean quit() {
        return true;
    }
}
