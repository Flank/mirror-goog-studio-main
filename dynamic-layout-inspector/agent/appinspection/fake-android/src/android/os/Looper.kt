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

package android.os

import java.util.ArrayDeque

class Looper {

    private enum class PollMode {
        RUNNING,
        QUIT_SAFELY,
        QUIT_NOW,
    }

    companion object {

        private var mainLooper: Looper? = null
        private val loopers = mutableMapOf<Thread, Looper>()

        fun prepareMainLooper() {
            check(mainLooper == null) { "Main looper already prepared" }
            prepare()
            mainLooper = loopers[Thread.currentThread()]
        }

        fun prepare() {
            loopers.computeIfAbsent(Thread.currentThread()) { Looper() }
        }

        fun getMainLooper(): Looper {
            checkNotNull(mainLooper) { "Must call `prepareMainLooper` first" }
            return mainLooper!!
        }

        @JvmStatic
        fun myLooper(): Looper {
            val looper = loopers[Thread.currentThread()]
            checkNotNull(looper) { "Must call `prepare` first" }
            return looper
        }

        fun loop() {
            myLooper().loop()
        }
    }

    private val lock = Any()
    private val pendingWork = ArrayDeque<Runnable>()

    @Volatile
    private var pollMode = PollMode.RUNNING

    val isCurrentThread: Boolean
        get() = (loopers[Thread.currentThread()] == this)

    fun quit() {
        synchronized(lock) {
            pollMode = PollMode.QUIT_NOW
            if (this == mainLooper) {
                mainLooper = null
            }
        }
    }

    fun quitSafely() {
        synchronized(lock) {
            if (pollMode == PollMode.RUNNING) {
                pollMode = PollMode.QUIT_SAFELY
            }
        }
    }

    internal fun post(r: Runnable) {
        synchronized(lock) {
            if (pollMode == PollMode.RUNNING) {
                pendingWork.add(r)
            }
        }
    }

    private fun loop() {
        while (pollMode != PollMode.QUIT_NOW) {
            synchronized(lock) {
                val work: Runnable? = pendingWork.poll()
                if (pollMode == PollMode.QUIT_SAFELY && work == null) {
                    pollMode = PollMode.QUIT_NOW
                }
                work
            }?.run()
            Thread.sleep(10)
        }
    }
}
