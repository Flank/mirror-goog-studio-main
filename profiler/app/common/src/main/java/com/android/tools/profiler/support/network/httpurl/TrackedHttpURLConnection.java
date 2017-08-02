/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profiler.support.network.httpurl;

import com.android.tools.profiler.support.network.HttpConnectionTracker;
import com.android.tools.profiler.support.network.HttpTracker;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Map;

/**
 * Wraps a {@link HttpURLConnection} instance and delegates the method calls to the wrapped object,
 * injecting calls to report HTTP activity through {@link HttpConnectionTracker}
 */
// Class is already package-private. Even though methods can be too, we leave them public, for
// readability and better expressing API intention.
@SuppressWarnings("WeakerAccess")
final class TrackedHttpURLConnection {

    private final HttpURLConnection myWrapped;
    private final HttpConnectionTracker myConnectionTracker;

    private boolean myConnectTracked;
    private InputStream myTrackedInputStream;

    public TrackedHttpURLConnection(HttpURLConnection wrapped, StackTraceElement[] callstack) {
        myWrapped = wrapped;
        myConnectionTracker = HttpTracker.trackConnection(wrapped.getURL().toString(), callstack);
    }

    public void disconnect() {
        // Close the input stream in case the user didn't explicitly themselves, ensuring any
        // remaining data we want to track is flushed.
        if (myTrackedInputStream != null) {
            try {
                myTrackedInputStream.close();
            } catch (Exception ignored) {
            }
        }
        myWrapped.disconnect();
        myConnectionTracker.disconnect();
    }

    public void connect() throws IOException {
        if (!myConnectTracked) {
            myConnectionTracker.trackRequest(getRequestMethod(), getRequestProperties());
        }
        try {
            myWrapped.connect();
            myConnectionTracker.trackResponse(getResponseMessage(), getHeaderFields());
        } catch (IOException e) {
            myConnectionTracker.error(e.toString());
            throw e;
        } finally {
            myConnectTracked = true;
        }
    }

    public InputStream getErrorStream() {
        return myWrapped.getErrorStream();
    }

    public Permission getPermission() throws IOException {
        return myWrapped.getPermission();
    }

    public String getRequestMethod() {
        if (myWrapped.getDoOutput() && myWrapped.getRequestMethod().equals("GET")) {
            // Unfortunately, HttpURLConnection only updates its method to "POST" after connect is
            // called. But for our tracking purposes, that's too late.
            return "POST";
        }
        return myWrapped.getRequestMethod();
    }

    public void setRequestMethod(String method) throws ProtocolException {
        myWrapped.setRequestMethod(method);
    }

    public boolean usingProxy() {
        return myWrapped.usingProxy();
    }

    public String getContentEncoding() {
        return myWrapped.getContentEncoding();
    }

    public boolean getInstanceFollowRedirects() {
        return myWrapped.getInstanceFollowRedirects();
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
        myWrapped.setInstanceFollowRedirects(followRedirects);
    }

    public void setChunkedStreamingMode(int chunkLength) {
        myWrapped.setChunkedStreamingMode(chunkLength);
    }

    public boolean getAllowUserInteraction() {
        return myWrapped.getAllowUserInteraction();
    }

    public long getDate() {
        return myWrapped.getDate();
    }

    public boolean getDefaultUseCaches() {
        return myWrapped.getDefaultUseCaches();
    }

    public boolean getDoInput() {
        return myWrapped.getDoInput();
    }

    public boolean getDoOutput() {
        return myWrapped.getDoOutput();
    }

    public long getExpiration() {
        return myWrapped.getExpiration();
    }

    public Map<String, List<String>> getRequestProperties() {
        return myWrapped.getRequestProperties();
    }

    public void addRequestProperty(String field, String newValue) {
        myWrapped.addRequestProperty(field, newValue);
    }

    public long getIfModifiedSince() {
        return myWrapped.getIfModifiedSince();
    }

    public long getLastModified() {
        return myWrapped.getLastModified();
    }

    public String getRequestProperty(String field) {
        return myWrapped.getRequestProperty(field);
    }

    public URL getURL() {
        return myWrapped.getURL();
    }

    public boolean getUseCaches() {
        return myWrapped.getUseCaches();
    }

    public void setAllowUserInteraction(boolean newValue) {
        myWrapped.setAllowUserInteraction(newValue);
    }

    public void setDefaultUseCaches(boolean newValue) {
        myWrapped.setDefaultUseCaches(newValue);
    }

    public void setDoInput(boolean newValue) {
        myWrapped.setDoInput(newValue);
    }

    public void setDoOutput(boolean newValue) {
        myWrapped.setDoOutput(newValue);
    }

    public void setIfModifiedSince(long newValue) {
        myWrapped.setIfModifiedSince(newValue);
    }

    public void setRequestProperty(String field, String newValue) {
        myWrapped.setRequestProperty(field, newValue);
    }

    public void setUseCaches(boolean newValue) {
        myWrapped.setUseCaches(newValue);
    }

    public void setConnectTimeout(int timeoutMillis) {
        myWrapped.setConnectTimeout(timeoutMillis);
    }

    public int getConnectTimeout() {
        return myWrapped.getConnectTimeout();
    }

    public void setReadTimeout(int timeoutMillis) {
        myWrapped.setReadTimeout(timeoutMillis);
    }

    public int getReadTimeout() {
        return myWrapped.getReadTimeout();
    }

    public void setFixedLengthStreamingMode(int contentLength) {
        myWrapped.setFixedLengthStreamingMode(contentLength);
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        myWrapped.setFixedLengthStreamingMode(contentLength);
    }

    public OutputStream getOutputStream() throws IOException {
        if (!myConnectTracked) {
            myConnectionTracker.trackRequest(getRequestMethod(), getRequestProperties());
        }
        try {
            return myConnectionTracker.trackRequestBody(myWrapped.getOutputStream());
        } catch (IOException e) {
            myConnectionTracker.error(e.toString());
            throw e;
        } finally {
            myConnectTracked = true;
        }
    }

    public int getResponseCode() throws IOException {
        // Internally, HttpURLConnection#getResponseCode() calls HttpURLConnection#getInputStream(),
        // but since we don't have hooks inside that class, we need to call it ourselves here, to
        // ensure the event is tracked.
        if (!myConnectTracked) {
            try {
                getInputStream();
            } catch (Exception ignored) {
                // We don't want to cause an exception to potentially be thrown as an unexpected
                // side-effect to calling getResponseCode
            }
        }
        return myWrapped.getResponseCode();
    }

    public String getResponseMessage() throws IOException {
        return myWrapped.getResponseMessage();
    }

    public String getHeaderField(int pos) {
        return myWrapped.getHeaderField(pos);
    }

    public Map<String, List<String>> getHeaderFields() {
        return myWrapped.getHeaderFields();
    }

    public String getHeaderField(String key) {
        return myWrapped.getHeaderField(key);
    }

    public int getHeaderFieldInt(String field, int defaultValue) {
        return myWrapped.getHeaderFieldInt(field, defaultValue);
    }

    public String getHeaderFieldKey(int posn) {
        return myWrapped.getHeaderFieldKey(posn);
    }

    public long getHeaderFieldDate(String field, long defaultValue) {
        return myWrapped.getHeaderFieldDate(field, defaultValue);
    }

    public long getHeaderFieldLong(String name, long Default) {
        return myWrapped.getHeaderFieldLong(name, Default);
    }

    public InputStream getInputStream() throws IOException {
        if (!myConnectTracked) {
            myConnectionTracker.trackRequest(getRequestMethod(), getRequestProperties());
        }
        try {
            InputStream stream = myWrapped.getInputStream();
            myConnectionTracker.trackResponse(getResponseMessage(), getHeaderFields());
            myTrackedInputStream = myConnectionTracker.trackResponseBody(stream);
            return myTrackedInputStream;
        } catch (IOException e) {
            myConnectionTracker.error(e.toString());
            throw e;
        } finally {
            myConnectTracked = true;
        }
    }

    public Object getContent() throws IOException {
        return myWrapped.getContent();
    }

    public Object getContent(Class[] types) throws IOException {
        return myWrapped.getContent(types);
    }

    public int getContentLength() {
        return myWrapped.getContentLength();
    }

    public long getContentLengthLong() {
        return myWrapped.getContentLengthLong();
    }

    public String getContentType() {
        return myWrapped.getContentType();
    }

    @Override
    public String toString() {
        return myWrapped.toString();
    }
}
