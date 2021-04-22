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

package com.android.tools.agent.appinspection.framework

import android.content.Context
import android.content.res.Resources
import android.graphics.Picture
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManagerGlobal
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class SkiaQWorkaroundTest {
    @get:Rule
    val mainLooperRule = MainLooperRule()

    @Test
    fun changeShouldSerializeDuringCaptureDoesntCrash() {
        val completed = SettableFuture.create<Unit>()
        ThreadUtils.runOnMainThread {
            val context = Context("view.inspector.test", Resources(mutableMapOf()))
            val root = ViewGroup(context).apply {
                width = 100
                height = 200
                setAttachInfo(View.AttachInfo())
            }
            WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

            var shouldSerialize = false

            var exception: Throwable? = null

            val captureExecutor = Executor { command ->
                shouldSerialize = true
                val latch = CountDownLatch(1)

                val threadFactory = ThreadFactory { r ->
                    ThreadUtils.newThread(r).apply {
                        uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
                            exception = e
                            fail()
                        }
                    }
                }
                Executors.newSingleThreadExecutor(threadFactory).execute {
                    command.run()
                    latch.countDown()
                }
                latch.await(5, TimeUnit.SECONDS)
            }
            val handle =
                SkiaQWorkaround.startRenderingCommandsCapture(
                    root,
                    captureExecutor,
                    callback = { ByteArrayOutputStream() },
                    shouldSerialize = { shouldSerialize })
            root.forcePictureCapture(Picture(byteArrayOf()))
            handle?.close()
            Looper.myLooper().quitSafely()

            exception?.let { completed.setException(it) }
            completed.set(Unit)
        }
        completed.get()
    }
}
