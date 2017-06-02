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
 *
 * This class is instantiated through one of the {@link HttpURLWrapper} helper methods
 */
// '$' intentionally used to mimic generated code since we insert this via bytecode instrumentation
@SuppressWarnings("DollarSignInName")
public final class HttpURLConnection$ extends HttpURLConnection {

    private final HttpURLConnection myWrapped;
    private final HttpConnectionTracker myConnectionTracker;

    private boolean myConnectTracked;
    private InputStream myTrackedInputStream;

    public HttpURLConnection$(HttpURLConnection wrapped, StackTraceElement[] callstack) {
        super(wrapped.getURL());
        myWrapped = wrapped;
        myConnectionTracker = HttpTracker.trackConnection(url.toString(), callstack);
    }

    @Override
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

    @Override
    public InputStream getErrorStream() {
        return myWrapped.getErrorStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return myWrapped.getPermission();
    }

    @Override
    public String getRequestMethod() {
        if (myWrapped.getDoOutput() && myWrapped.getRequestMethod().equals("GET")) {
            // Unfortunately, HttpURLConnection only updates its method to "POST" after connect is
            // called. But for our tracking purposes, that's too late.
            return "POST";
        }
        return myWrapped.getRequestMethod();
    }

    @Override
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

    @Override
    public String getResponseMessage() throws IOException {
        return myWrapped.getResponseMessage();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        myWrapped.setRequestMethod(method);
    }

    @Override
    public boolean usingProxy() {
        return myWrapped.usingProxy();
    }

    @Override
    public String getContentEncoding() {
        return myWrapped.getContentEncoding();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return myWrapped.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        myWrapped.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getHeaderFieldDate(String field, long defaultValue) {
        return myWrapped.getHeaderFieldDate(field, defaultValue);
    }

  /* TODO: resolve conflict between JDK and android.jar
  @Override
  public void setFixedLengthStreamingMode(long contentLength) {
    myWrapped.setFixedLengthStreamingMode(contentLength);
  }

  @Override
  public void setFixedLengthStreamingMode(int contentLength) {
    myWrapped.setFixedLengthStreamingMode(contentLength);
  }
  */

    @Override
    public void setChunkedStreamingMode(int chunkLength) {
        myWrapped.setChunkedStreamingMode(chunkLength);
    }

    @Override
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

    @Override
    public boolean getAllowUserInteraction() {
        return myWrapped.getAllowUserInteraction();
    }

    @Override
    public Object getContent() throws IOException {
        return myWrapped.getContent();
    }

    @Override
    public Object getContent(Class[] types) throws IOException {
        return myWrapped.getContent(types);
    }

    @Override
    public int getContentLength() {
        return myWrapped.getContentLength();
    }

    @Override
    public String getContentType() {
        return myWrapped.getContentType();
    }

    @Override
    public long getDate() {
        return myWrapped.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return myWrapped.getDefaultUseCaches();
    }

    @Override
    public boolean getDoInput() {
        return myWrapped.getDoInput();
    }

    @Override
    public boolean getDoOutput() {
        return myWrapped.getDoOutput();
    }

    @Override
    public long getExpiration() {
        return myWrapped.getExpiration();
    }

    @Override
    public String getHeaderField(int pos) {
        return myWrapped.getHeaderField(pos);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return myWrapped.getHeaderFields();
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return myWrapped.getRequestProperties();
    }

    @Override
    public void addRequestProperty(String field, String newValue) {
        myWrapped.addRequestProperty(field, newValue);
    }

    @Override
    public String getHeaderField(String key) {
        return myWrapped.getHeaderField(key);
    }

    @Override
    public int getHeaderFieldInt(String field, int defaultValue) {
        return myWrapped.getHeaderFieldInt(field, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int posn) {
        return myWrapped.getHeaderFieldKey(posn);
    }

    @Override
    public long getIfModifiedSince() {
        return myWrapped.getIfModifiedSince();
    }

    @Override
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

    @Override
    public long getLastModified() {
        return myWrapped.getLastModified();
    }

    @Override
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

    @Override
    public String getRequestProperty(String field) {
        return myWrapped.getRequestProperty(field);
    }

    @Override
    public URL getURL() {
        return myWrapped.getURL();
    }

    @Override
    public boolean getUseCaches() {
        return myWrapped.getUseCaches();
    }

    @Override
    public void setAllowUserInteraction(boolean newValue) {
        myWrapped.setAllowUserInteraction(newValue);
    }

    @Override
    public void setDefaultUseCaches(boolean newValue) {
        myWrapped.setDefaultUseCaches(newValue);
    }

    @Override
    public void setDoInput(boolean newValue) {
        myWrapped.setDoInput(newValue);
    }

    @Override
    public void setDoOutput(boolean newValue) {
        myWrapped.setDoOutput(newValue);
    }

    @Override
    public void setIfModifiedSince(long newValue) {
        myWrapped.setIfModifiedSince(newValue);
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
        myWrapped.setRequestProperty(field, newValue);
    }

    @Override
    public void setUseCaches(boolean newValue) {
        myWrapped.setUseCaches(newValue);
    }

    @Override
    public void setConnectTimeout(int timeoutMillis) {
        myWrapped.setConnectTimeout(timeoutMillis);
    }

    @Override
    public int getConnectTimeout() {
        return myWrapped.getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeoutMillis) {
        myWrapped.setReadTimeout(timeoutMillis);
    }

    @Override
    public int getReadTimeout() {
        return myWrapped.getReadTimeout();
    }

    @Override
    public String toString() {
        return myWrapped.toString();
    }
}
