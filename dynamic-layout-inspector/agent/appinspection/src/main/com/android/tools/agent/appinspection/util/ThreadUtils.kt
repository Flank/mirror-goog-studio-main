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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

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

    /**
     * Run some logic on the main thread, returning a future that will contain any data computed
     * by and returned from the block.
     *
     * If this method is called from the main thread, it will run immediately.
     */
    fun <T> runOnMainThread(block: () -> T): CompletableFuture<T> {
        return if (!Looper.getMainLooper().isCurrentThread) {
            val future = CompletableFuture<T>()
            Handler.createAsync(Looper.getMainLooper()).post {
                future.complete(block())
            }
            future
        }
        else {
            CompletableFuture.completedFuture(block())
        }
    }

    /**
     * Run some logic on the main thread, returning a future that will contain any data computed
     * by and returned from the block.
     *
     * If this method is called from the main thread, it will run immediately.
     */
    fun <T> runOnMainThreadAsync(block: () -> T): Deferred<T> {
        return if (!Looper.getMainLooper().isCurrentThread) {
            val deferred = CompletableDeferred<T>()
            Handler.createAsync(Looper.getMainLooper()).post {
                deferred.complete(block())
            }
            deferred
        }
        else {
            CompletableDeferred(block())
        }
    }
}
