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
package com.android.tools.appinspection.network.httpurl

import com.android.tools.appinspection.network.HttpTrackerFactory
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.Permission

/**
 * Wraps a [HttpURLConnection] instance and delegates the method calls to the wrapped object,
 * injecting calls to report HTTP activity through [HttpConnectionTracker]
 *
 * [HttpURLConnectionWrapper] and [HttpsURLConnectionWrapper] delegates the heavy lifting of
 * tracking to this class.
 */
class TrackedHttpURLConnection(
    private val wrapped: HttpURLConnection,
    callstack: Array<StackTraceElement>,
    trackerFactory: HttpTrackerFactory
) {

    private val connectionTracker: HttpConnectionTracker =
        trackerFactory.trackConnection(wrapped.url.toString(), callstack)
    private var connectTracked = false
    private var responseTracked = false
    private var trackedRequestStream: OutputStream? = null
    private var trackedResponseStream: InputStream? = null

    /**
     * Calls [HttpConnectionTracker.trackRequest] only if it hasn't been called
     * before.
     *
     * You should call this method just before [HttpURLConnection.connect] is called,
     * after which point, [HttpURLConnection] throws exceptions if you try to access the
     * fields we want to track.
     */
    private fun trackPreConnect() {
        if (!connectTracked) {
            try {
                connectionTracker.trackRequest(requestMethod, requestProperties)
            } finally {
                connectTracked = true
            }
        }
    }

    /**
     * Attempt to force the current connection to connect, swallowing any exception so that we don't
     * affect the site calling this method.
     *
     * Calling connect ourselves is useful in case the user calls a HttpURLConnection method
     * which would otherwise have caused a connect to happen as a side-effect. In that case, we
     * preemptively do it ourselves, to make sure that our tracking state machine stays valid.
     */
    private fun tryConnect() {
        if (!connectTracked) {
            try {
                connect()
            } catch (ignored: Exception) {
                // Swallowed, so callers can call this method without worrying about dealing with
                // exceptions that shouldn't happen normally anywa.
            }
        }
    }

    /**
     * Calls [HttpConnectionTracker.trackResponse] only if it hasn't been called
     * before. This should be called to indicate that we received a response and can now start to
     * read its contents.
     *
     * IMPORTANT: This method, as a side-effect, will cause the request to get sent if it hasn't
     * been sent already. Therefore, if this method is called too early, it can cause problems if
     * the user then tries to modify the request afterwards, e.g. by updating its body via
     * [getOutputStream].
     */
    private fun trackResponse() {
        if (!responseTracked) {
            try {
                tryConnect()

                // Don't call our getResponseMessage/getHeaderFields overrides, as it would call
                // this method recursively.
                connectionTracker.trackResponse(wrapped.responseMessage, wrapped.headerFields)
            } finally {
                responseTracked = true
            }
        }
    }

    /**
     * Like [.trackResponse] but swallows the exception. This is useful because there are
     * many methods in [HttpURLConnection] that a user can call which indicate that a request
     * has been completed (for example, [HttpURLConnection.getResponseCode] which don't,
     * itself, throw an exception.
     *
     * IMPORTANT: This method, as a side-effect, will cause the request to get sent if it hasn't
     * been sent already. Therefore, if this method is called too early, it can cause problems if
     * the user then tries to modify the request afterwards, e.g. by updating its body via
     * [getOutputStream].
     */
    private fun tryTrackResponse() {
        try {
            trackResponse()
        } catch (ignored: IOException) {
        }
    }

    fun disconnect() {
        // Close streams in case the user didn't explicitly do it themselves, ensuring any
        // remaining data we want to track is flushed.
        try {
            trackedRequestStream?.close()
        } catch (ignored: Exception) {
        }
        try {
            trackedResponseStream?.close()
        } catch (ignored: Exception) {
        }
        wrapped.disconnect()
        connectionTracker.disconnect()
    }

    fun connect() {
        trackPreConnect()
        try {
            wrapped.connect()
            // Note: Just because the user "connect"ed doesn't mean the request was sent out yet.
            // A user can still modify it further, for example updating the request body, before
            // actually sending the request out. Therefore, we don't call trackResponse here.
        } catch (e: IOException) {
            connectionTracker.error(e.toString())
            throw e
        }
    }

    val errorStream: InputStream
        get() = wrapped.errorStream

    val permission: Permission
        get() = wrapped.permission

    // Unfortunately, HttpURLConnection only updates its method to "POST" after connect is
    // called. But for our tracking purposes, that's too late.
    var requestMethod: String
        get() = if (wrapped.doOutput && wrapped.requestMethod == "GET") {
            // Unfortunately, HttpURLConnection only updates its method to "POST" after connect is
            // called. But for our tracking purposes, that's too late.
            "POST"
        } else wrapped.requestMethod
        set(method) {
            wrapped.requestMethod = method
        }

    fun usingProxy(): Boolean {
        return wrapped.usingProxy()
    }

    val contentEncoding: String
        get() = wrapped.contentEncoding

    var instanceFollowRedirects: Boolean
        get() = wrapped.instanceFollowRedirects
        set(followRedirects) {
            wrapped.instanceFollowRedirects = followRedirects
        }

    fun setChunkedStreamingMode(chunkLength: Int) {
        wrapped.setChunkedStreamingMode(chunkLength)
    }

    var allowUserInteraction: Boolean
        get() = wrapped.allowUserInteraction
        set(newValue) {
            wrapped.allowUserInteraction = newValue
        }

    val date: Long
        get() = wrapped.date

    var defaultUseCaches: Boolean
        get() = wrapped.defaultUseCaches
        set(newValue) {
            wrapped.defaultUseCaches = newValue
        }

    var doInput: Boolean
        get() = wrapped.doInput
        set(newValue) {
            wrapped.doInput = newValue
        }

    var doOutput: Boolean
        get() = wrapped.doOutput
        set(newValue) {
            wrapped.doOutput = newValue
        }
    val expiration: Long
        get() = wrapped.expiration

    val requestProperties: Map<String, List<String>>
        get() = wrapped.requestProperties

    fun addRequestProperty(field: String, newValue: String) {
        wrapped.addRequestProperty(field, newValue)
    }

    var ifModifiedSince: Long
        get() = wrapped.ifModifiedSince
        set(newValue) {
            wrapped.ifModifiedSince = newValue
        }

    val lastModified: Long
        get() = wrapped.lastModified

    fun getRequestProperty(field: String): String {
        return wrapped.getRequestProperty(field)
    }

    val url: URL
        get() = wrapped.url

    var useCaches: Boolean
        get() = wrapped.useCaches
        set(newValue) {
            wrapped.useCaches = newValue
        }

    fun setRequestProperty(field: String, newValue: String) {
        wrapped.setRequestProperty(field, newValue)
    }

    var connectTimeout: Int
        get() = wrapped.connectTimeout
        set(timeoutMillis) {
            wrapped.connectTimeout = timeoutMillis
        }

    var readTimeout: Int
        get() = wrapped.readTimeout
        set(timeoutMillis) {
            wrapped.readTimeout = timeoutMillis
        }

    fun setFixedLengthStreamingMode(contentLength: Int) {
        wrapped.setFixedLengthStreamingMode(contentLength)
    }

    fun setFixedLengthStreamingMode(contentLength: Long) {
        wrapped.setFixedLengthStreamingMode(contentLength)
    }

    // getOutputStream internally calls connect if not already connected.
    val outputStream: OutputStream
        get() {
            // getOutputStream internally calls connect if not already connected.
            trackPreConnect()
            return try {
                connectionTracker.trackRequestBody(wrapped.outputStream).also {
                    trackedRequestStream = it
                }
            } catch (e: IOException) {
                connectionTracker.error(e.toString())
                throw e
            }
        }

    // Internally, HttpURLConnection#getResponseCode() calls HttpURLConnection#getInputStream(),
    // but since we don't have hooks inside that class, we need to call it ourselves here, to
    // ensure the event is tracked.
    val responseCode: Int
        get() {
            // Internally, HttpURLConnection#getResponseCode() calls HttpURLConnection#getInputStream(),
            // but since we don't have hooks inside that class, we need to call it ourselves here, to
            // ensure the event is tracked.
            tryTrackResponse()
            return wrapped.responseCode
        }

    val responseMessage: String
        get() {
            tryTrackResponse()
            return wrapped.responseMessage
        }

    fun getHeaderField(pos: Int): String {
        tryTrackResponse()
        return wrapped.getHeaderField(pos)
    }

    val headerFields: Map<String, List<String>>
        get() {
            tryTrackResponse()
            return wrapped.headerFields
        }

    fun getHeaderField(key: String): String {
        tryTrackResponse()
        return wrapped.getHeaderField(key)
    }

    fun getHeaderFieldInt(field: String, defaultValue: Int): Int {
        tryTrackResponse()
        return wrapped.getHeaderFieldInt(field, defaultValue)
    }

    fun getHeaderFieldKey(posn: Int): String {
        tryTrackResponse()
        return wrapped.getHeaderFieldKey(posn)
    }

    fun getHeaderFieldDate(field: String, defaultValue: Long): Long {
        tryTrackResponse()
        return wrapped.getHeaderFieldDate(field, defaultValue)
    }

    fun getHeaderFieldLong(name: String, Default: Long): Long {
        tryTrackResponse()
        return wrapped.getHeaderFieldLong(name, Default)
    }

    // getInputStream internally calls connect if not already connected.
    val inputStream: InputStream
        get() {
            // getInputStream internally calls connect if not already connected.
            trackPreConnect()
            return try {
                val stream = wrapped.inputStream
                trackResponse()
                connectionTracker.trackResponseBody(stream).also {
                    trackedResponseStream = it
                }
            } catch (e: IOException) {
                connectionTracker.error(e.toString())
                throw e
            }
        }

    val content: Any
        get() {
            tryTrackResponse()
            return wrapped.content
        }

    fun getContent(types: Array<Class<*>>): Any {
        tryTrackResponse()
        return wrapped.getContent(types)
    }

    val contentLength: Int
        get() {
            tryTrackResponse()
            return wrapped.contentLength
        }

    val contentLengthLong: Long
        get() {
            tryTrackResponse()
            return wrapped.contentLengthLong
        }

    val contentType: String
        get() {
            tryTrackResponse()
            return wrapped.contentType
        }

    override fun toString(): String {
        return wrapped.toString()
    }
}
