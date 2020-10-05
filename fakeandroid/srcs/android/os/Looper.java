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

package android.os;

public class Looper {
    private static Looper sMainLooper;

    private final Thread myThread;

    public Looper() {
        this.myThread = Thread.currentThread();
    }

    public Thread getThread() {
        return myThread;
    }

    public static void prepareMainLooper() {
        sMainLooper = new Looper();
    }

    public static Looper getMainLooper() {
        return sMainLooper;
    }
}
