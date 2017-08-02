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
 * An implementation of {@link HttpURLConnection} which delegates the method calls to a {@link
 * TrackedHttpURLConnection}, which ensures that the appropriate methods are recorded for profiling.
 *
 * <p>This class is instantiated through one of the {@link HttpURLWrapper} helper methods
 */
// '$' intentionally used to mimic generated code since we insert this via bytecode instrumentation
@SuppressWarnings("DollarSignInName")
public final class HttpURLConnection$ extends HttpURLConnection {
    private final TrackedHttpURLConnection myTrackedConnection;

    public HttpURLConnection$(HttpURLConnection wrapped, StackTraceElement[] callstack) {
        super(wrapped.getURL());
        myTrackedConnection = new TrackedHttpURLConnection(wrapped, callstack);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return myTrackedConnection.getHeaderFieldKey(n);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        myTrackedConnection.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        myTrackedConnection.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setChunkedStreamingMode(int chunklen) {
        myTrackedConnection.setChunkedStreamingMode(chunklen);
    }

    @Override
    public String getHeaderField(int n) {
        return myTrackedConnection.getHeaderField(n);
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        myTrackedConnection.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return myTrackedConnection.getInstanceFollowRedirects();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        myTrackedConnection.setRequestMethod(method);
    }

    @Override
    public String getRequestMethod() {
        return myTrackedConnection.getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
        return myTrackedConnection.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return myTrackedConnection.getResponseMessage();
    }

    @Override
    public long getHeaderFieldDate(String name, long Default) {
        return myTrackedConnection.getHeaderFieldDate(name, Default);
    }

    @Override
    public Permission getPermission() throws IOException {
        return myTrackedConnection.getPermission();
    }

    @Override
    public InputStream getErrorStream() {
        return myTrackedConnection.getErrorStream();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        myTrackedConnection.setConnectTimeout(timeout);
    }

    @Override
    public int getConnectTimeout() {
        return myTrackedConnection.getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        myTrackedConnection.setReadTimeout(timeout);
    }

    @Override
    public int getReadTimeout() {
        return myTrackedConnection.getReadTimeout();
    }

    @Override
    public URL getURL() {
        return myTrackedConnection.getURL();
    }

    @Override
    public int getContentLength() {
        return myTrackedConnection.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return myTrackedConnection.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return myTrackedConnection.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return myTrackedConnection.getContentEncoding();
    }

    @Override
    public long getExpiration() {
        return myTrackedConnection.getExpiration();
    }

    @Override
    public long getDate() {
        return myTrackedConnection.getDate();
    }

    @Override
    public long getLastModified() {
        return myTrackedConnection.getLastModified();
    }

    @Override
    public String getHeaderField(String name) {
        return myTrackedConnection.getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return myTrackedConnection.getHeaderFields();
    }

    @Override
    public int getHeaderFieldInt(String name, int Default) {
        return myTrackedConnection.getHeaderFieldInt(name, Default);
    }

    @Override
    public long getHeaderFieldLong(String name, long Default) {
        return myTrackedConnection.getHeaderFieldLong(name, Default);
    }

    @Override
    public Object getContent() throws IOException {
        return myTrackedConnection.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return myTrackedConnection.getContent(classes);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return myTrackedConnection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return myTrackedConnection.getOutputStream();
    }

    @Override
    public String toString() {
        return myTrackedConnection.toString();
    }

    @Override
    public void setDoInput(boolean doinput) {
        myTrackedConnection.setDoInput(doinput);
    }

    @Override
    public boolean getDoInput() {
        return myTrackedConnection.getDoInput();
    }

    @Override
    public void setDoOutput(boolean dooutput) {
        myTrackedConnection.setDoOutput(dooutput);
    }

    @Override
    public boolean getDoOutput() {
        return myTrackedConnection.getDoOutput();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
        myTrackedConnection.setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getAllowUserInteraction() {
        return myTrackedConnection.getAllowUserInteraction();
    }

    @Override
    public void setUseCaches(boolean usecaches) {
        myTrackedConnection.setUseCaches(usecaches);
    }

    @Override
    public boolean getUseCaches() {
        return myTrackedConnection.getUseCaches();
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
        myTrackedConnection.setIfModifiedSince(ifmodifiedsince);
    }

    @Override
    public long getIfModifiedSince() {
        return myTrackedConnection.getIfModifiedSince();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return myTrackedConnection.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
        myTrackedConnection.setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        myTrackedConnection.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        myTrackedConnection.addRequestProperty(key, value);
    }

    @Override
    public String getRequestProperty(String key) {
        return myTrackedConnection.getRequestProperty(key);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return myTrackedConnection.getRequestProperties();
    }

    @Override
    public void disconnect() {
        myTrackedConnection.disconnect();
    }

    @Override
    public boolean usingProxy() {
        return myTrackedConnection.usingProxy();
    }

    @Override
    public void connect() throws IOException {
        myTrackedConnection.connect();
    }
}
