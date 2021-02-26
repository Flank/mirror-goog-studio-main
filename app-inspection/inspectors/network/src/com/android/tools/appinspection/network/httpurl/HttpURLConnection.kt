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

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.Permission

/**
 * An implementation of [java.net.HttpURLConnection] which delegates the method calls to a
 * [TrackedHttpURLConnection], which ensures that the appropriate methods are instrumented.
 */
class HttpURLConnection(wrapped: HttpURLConnection, callstack: Array<StackTraceElement>) :
    HttpURLConnection(wrapped.url) {

    private val myTrackedConnection: TrackedHttpURLConnection =
        TrackedHttpURLConnection(wrapped, callstack)

    override fun getHeaderFieldKey(n: Int): String {
        return myTrackedConnection.getHeaderFieldKey(n)
    }

    override fun setFixedLengthStreamingMode(contentLength: Int) {
        myTrackedConnection.setFixedLengthStreamingMode(contentLength)
    }

    override fun setFixedLengthStreamingMode(contentLength: Long) {
        myTrackedConnection.setFixedLengthStreamingMode(contentLength)
    }

    override fun setChunkedStreamingMode(chunklen: Int) {
        myTrackedConnection.setChunkedStreamingMode(chunklen)
    }

    override fun getHeaderField(n: Int): String {
        return myTrackedConnection.getHeaderField(n)
    }

    override fun setInstanceFollowRedirects(followRedirects: Boolean) {
        myTrackedConnection.instanceFollowRedirects = followRedirects
    }

    override fun getInstanceFollowRedirects(): Boolean {
        return myTrackedConnection.instanceFollowRedirects
    }

    override fun setRequestMethod(method: String) {
        myTrackedConnection.requestMethod = method
    }

    override fun getRequestMethod(): String {
        return myTrackedConnection.requestMethod
    }

    override fun getResponseCode(): Int {
        return myTrackedConnection.responseCode
    }

    override fun getResponseMessage(): String {
        return myTrackedConnection.responseMessage
    }

    override fun getHeaderFieldDate(name: String, Default: Long): Long {
        return myTrackedConnection.getHeaderFieldDate(name, Default)
    }

    override fun getPermission(): Permission {
        return myTrackedConnection.permission
    }

    override fun getErrorStream(): InputStream {
        return myTrackedConnection.errorStream
    }

    override fun setConnectTimeout(timeout: Int) {
        myTrackedConnection.connectTimeout = timeout
    }

    override fun getConnectTimeout(): Int {
        return myTrackedConnection.connectTimeout
    }

    override fun setReadTimeout(timeout: Int) {
        myTrackedConnection.readTimeout = timeout
    }

    override fun getReadTimeout(): Int {
        return myTrackedConnection.readTimeout
    }

    override fun getURL(): URL {
        return myTrackedConnection.url
    }

    override fun getContentLength(): Int {
        return myTrackedConnection.contentLength
    }

    override fun getContentLengthLong(): Long {
        return myTrackedConnection.contentLengthLong
    }

    override fun getContentType(): String {
        return myTrackedConnection.contentType
    }

    override fun getContentEncoding(): String {
        return myTrackedConnection.contentEncoding
    }

    override fun getExpiration(): Long {
        return myTrackedConnection.expiration
    }

    override fun getDate(): Long {
        return myTrackedConnection.date
    }

    override fun getLastModified(): Long {
        return myTrackedConnection.lastModified
    }

    override fun getHeaderField(name: String): String {
        return myTrackedConnection.getHeaderField(name)
    }

    override fun getHeaderFields(): Map<String, List<String>> {
        return myTrackedConnection.headerFields
    }

    override fun getHeaderFieldInt(name: String, Default: Int): Int {
        return myTrackedConnection.getHeaderFieldInt(name, Default)
    }

    override fun getHeaderFieldLong(name: String, Default: Long): Long {
        return myTrackedConnection.getHeaderFieldLong(name, Default)
    }

    override fun getContent(): Any {
        return myTrackedConnection.content
    }

    override fun getContent(classes: Array<Class<*>>): Any {
        return myTrackedConnection.getContent(classes)
    }

    override fun getInputStream(): InputStream {
        return myTrackedConnection.inputStream
    }

    override fun getOutputStream(): OutputStream {
        return myTrackedConnection.outputStream
    }

    override fun toString(): String {
        return myTrackedConnection.toString()
    }

    override fun setDoInput(doinput: Boolean) {
        myTrackedConnection.doInput = doinput
    }

    override fun getDoInput(): Boolean {
        return myTrackedConnection.doInput
    }

    override fun setDoOutput(dooutput: Boolean) {
        myTrackedConnection.doOutput = dooutput
    }

    override fun getDoOutput(): Boolean {
        return myTrackedConnection.doOutput
    }

    override fun setAllowUserInteraction(allowuserinteraction: Boolean) {
        myTrackedConnection.allowUserInteraction = allowuserinteraction
    }

    override fun getAllowUserInteraction(): Boolean {
        return myTrackedConnection.allowUserInteraction
    }

    override fun setUseCaches(usecaches: Boolean) {
        myTrackedConnection.useCaches = usecaches
    }

    override fun getUseCaches(): Boolean {
        return myTrackedConnection.useCaches
    }

    override fun setIfModifiedSince(ifmodifiedsince: Long) {
        myTrackedConnection.ifModifiedSince = ifmodifiedsince
    }

    override fun getIfModifiedSince(): Long {
        return myTrackedConnection.ifModifiedSince
    }

    override fun getDefaultUseCaches(): Boolean {
        return myTrackedConnection.defaultUseCaches
    }

    override fun setDefaultUseCaches(defaultusecaches: Boolean) {
        myTrackedConnection.defaultUseCaches = defaultusecaches
    }

    override fun setRequestProperty(key: String, value: String) {
        myTrackedConnection.setRequestProperty(key, value)
    }

    override fun addRequestProperty(key: String, value: String) {
        myTrackedConnection.addRequestProperty(key, value)
    }

    override fun getRequestProperty(key: String): String {
        return myTrackedConnection.getRequestProperty(key)
    }

    override fun getRequestProperties(): Map<String, List<String>> {
        return myTrackedConnection.requestProperties
    }

    override fun disconnect() {
        myTrackedConnection.disconnect()
    }

    override fun usingProxy(): Boolean {
        return myTrackedConnection.usingProxy()
    }

    override fun connect() {
        myTrackedConnection.connect()
    }
}
