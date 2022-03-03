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

import android.graphics.HardwareRenderer
import android.graphics.Picture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import dalvik.system.VMDebug
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.UnsupportedOperationException
import java.lang.reflect.Field
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

/**
 * In the initial public version of Q there was a bug in `ViewDebug#startRenderingCommandsCapture`
 * that would sometimes crash apps. The code here is a fixed version of that.
 *
 * This class is no longer necessary starting in T. ViewDebug should be used directly for T+
 * devices.
 */
object SkiaQWorkaround {

    private fun getFieldValue(o: Any, fieldName: String): Any? {
        val f: Field = generateSequence(o.javaClass) { it.superclass }
            .mapNotNull { c ->
                try {
                    getDeclaredField(c, fieldName)
                } catch (ignore: NoSuchFieldException) {
                    null
                }
            }
            .firstOrNull() ?: return null
        f.isAccessible = true
        return f.get(o)
    }

    // This has to be done in SkiaQWorkaround directly (not a lambda), due to the
    // allowHiddenApiReflectionFrom call.
    private fun getDeclaredField(cls: Class<*>, fieldName: String) = cls.getDeclaredField(fieldName)

    fun startRenderingCommandsCapture(
        tree: View,
        executor: Executor,
        callback: Callable<OutputStream?>
    ): AutoCloseable? {
        try {
            VMDebug.allowHiddenApiReflectionFrom(SkiaQWorkaround::class.java)
        }
        catch (exception: SecurityException) {
            throw UnsupportedOperationException("Unable to enable reflection. " +
                    "App must be built with debugging enabled.")
        }
        // If mAttachInfo is null this window may be gone but the roots detector hasn't run yet.
        val attachInfo = getFieldValue(tree, "mAttachInfo") ?: return null
        val handler = getFieldValue(attachInfo, "mHandler") as Handler?
        check(!(handler == null || handler.looper != Looper.myLooper())) {
            ("Called on the wrong thread."
                    + " Must be called on the thread that owns the given View")
        }
        val renderer =
            getFieldValue(attachInfo, "mThreadedRenderer")
                ?: throw IllegalStateException("Unable to get ThreadedRenderer")
        val streamingPictureCallbackHandler = StreamingPictureCallbackHelper.createCallback(
            renderer, callback, executor) as AutoCloseable
        try {
            VMDebug.allowHiddenApiReflectionFrom(streamingPictureCallbackHandler.javaClass)
            VMDebug.allowHiddenApiReflectionFrom(StreamingPictureCallbackHelper::class.java)
            StreamingPictureCallbackHelper.setPictureCaptureCallback(
                renderer, streamingPictureCallbackHandler
            )
        }
        catch (exception: Exception) {
            streamingPictureCallbackHandler.close()
            throw exception
        }
        return streamingPictureCallbackHandler
    }

    private object StreamingPictureCallbackHelper {
        fun setPictureCaptureCallback(renderer: Any, callback: Any?) {
            try {
                var rendererClass: Class<*> = renderer.javaClass
                if (!rendererClass.name.contains("Hardware")) {
                    rendererClass = rendererClass.superclass
                }
                val setPictureCaptureCallback = rendererClass.getDeclaredMethod(
                    "setPictureCaptureCallback",
                    HardwareRenderer.PictureCapturedCallback::class.java
                )
                setPictureCaptureCallback.invoke(renderer, callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createCallback(
            renderer: Any, callback: Callable<OutputStream?>, executor: Executor
        ): Any {
            return StreamingPictureCallbackHandler(renderer, callback, executor)
        }

        private class StreamingPictureCallbackHandler(
            private val mRenderer: Any,
            private val mCallback: Callable<OutputStream?>,
            private val mExecutor: Executor
        ) : AutoCloseable, HardwareRenderer.PictureCapturedCallback, Runnable {
            private val mLock = ReentrantLock(false)
            private val mQueue = ArrayDeque<ByteArray>(3)
            private val mByteStream = ByteArrayOutputStream()
            private var mStopListening = false
            private var mRenderThread: Thread? = null
            override fun close() {
                mLock.lock()
                mStopListening = true
                mLock.unlock()
                setPictureCaptureCallback(mRenderer, null)
            }

            override fun onPictureCaptured(picture: Picture) {
                mLock.lock()
                if (mStopListening) {
                    mLock.unlock()
                    setPictureCaptureCallback(mRenderer, null)
                    return
                }
                if (mRenderThread == null) {
                    mRenderThread = Thread.currentThread()
                }
                var needsInvoke = true
                if (mQueue.size == 3) {
                    mQueue.removeLast()
                    needsInvoke = false
                }
                try {
                    Picture::class.java
                        .getDeclaredMethod("writeToStream", OutputStream::class.java)
                        .invoke(picture, mByteStream)
                } catch (e: Exception) {
                    // shouldn't happen
                }
                mQueue.add(mByteStream.toByteArray())
                mByteStream.reset()
                mLock.unlock()
                if (needsInvoke) {
                    mExecutor.execute(this)
                }
            }

            override fun run() {
                mLock.lock()
                val picture = mQueue.poll()
                val isStopped = mStopListening
                mLock.unlock()
                if (Thread.currentThread() === mRenderThread) {
                    close()
                    throw IllegalStateException(
                        "ViewDebug#startRenderingCommandsCapture must be given an executor that "
                                + "invokes asynchronously"
                    )
                }
                if (isStopped) {
                    return
                }
                var stream: OutputStream? = null
                try {
                    stream = mCallback.call()
                } catch (ex: Exception) {
                    Log.w(
                        "ViewDebug",
                        "Aborting rendering commands capture because callback threw exception",
                        ex
                    )
                }
                if (stream != null) {
                    try {
                        stream.write(picture)
                    } catch (ex: IOException) {
                        Log.w(
                            "ViewDebug", "Aborting rendering commands capture due to IOException"
                                    + " writing to output stream",
                            ex
                        )
                    }
                } else {
                    close()
                }
            }
        }
    }
}
