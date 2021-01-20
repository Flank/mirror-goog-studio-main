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

package com.android.tools.agent.appinspection.util

import android.os.Handler
import android.os.Looper

object ThreadUtils {

    /**
     * Create a new thread with a special name that is allowed by ThreadWatcher.
     */
    fun newThread(runnable: Runnable): Thread {
        // ThreadWatcher accepts threads starting with "Studio:"
        return Thread(runnable, "Studio:LayInsp")
    }

    fun assertOnMainThread() {
        if (!Looper.getMainLooper().isCurrentThread) {
            error("This work is required on the main thread")
        }
    }

    fun assertOffMainThread() {
        if (Looper.getMainLooper().isCurrentThread) {
            error("This work is required off the main thread")
        }
    }

    fun runOnMainThread(block: () -> Unit) {
        Handler.createAsync(Looper.getMainLooper()).post(block)
    }
}
