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
package com.android.tools.profiler.support.network;

import com.android.tools.profiler.support.util.ByteBatcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;


/**
 * This is the factory for the {@link HttpConnectionTracker} instances
 */
public final class HttpTracker {

    /**
     * Wraps an InputStream to enable the network profiler capturing of response body
     */
    private static final class InputStreamTracker extends InputStream {

        private Connection myConnectionTracker;
        private InputStream myWrapped;
        private boolean myFirstRead = true;

        private final ByteBatcher mByteBatcher = new ByteBatcher(new ByteBatcher.FlushReceiver() {
            @Override
            public void receive(byte[] bytes) {
                reportBytes(myConnectionTracker.myId, bytes);
            }
        });

        InputStreamTracker(InputStream wrapped, Connection connectionTracker) {
            myWrapped = wrapped;
            myConnectionTracker = connectionTracker;
        }

        @Override
        public int available() throws IOException {
            return myWrapped.available();
        }

        @Override
        public boolean markSupported() {
            return myWrapped.markSupported();
        }

        @Override
        public void mark(int readLimit) {
            myWrapped.mark(readLimit);
        }

        @Override
        public void reset() throws IOException {
            myWrapped.reset();
        }

        @Override
        public void close() throws IOException {
            myWrapped.close();
            mByteBatcher.flush();
            onClose(myConnectionTracker.myId);
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read() throws IOException {
            if (myFirstRead) {
                onReadBegin(myConnectionTracker.myId);
                myFirstRead = false;
            }

            int b = myWrapped.read();
            mByteBatcher.addByte(b);
            myConnectionTracker.trackThread();
            return b;
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            if (myFirstRead) {
                onReadBegin(myConnectionTracker.myId);
                myFirstRead = false;
            }
            int bytesRead = myWrapped.read(buffer, byteOffset, byteCount);
            mByteBatcher.addBytes(buffer, byteOffset, bytesRead);
            myConnectionTracker.trackThread();
            return bytesRead;
        }

        @Override
        public long skip(long byteCount) throws IOException {
            if (myFirstRead) {
                onReadBegin(myConnectionTracker.myId);
                myFirstRead = false;
            }
            myConnectionTracker.trackThread();
            return myWrapped.skip(byteCount);
        }

        private native void onClose(long id);
        private native void onReadBegin(long id);
        private native void reportBytes(long id, byte[] bytes);
    }

    /**
     * Wraps an OutputStream to enable the network profiler capturing of request body
     */
    private static final class OutputStreamTracker extends OutputStream {

        private Connection myConnectionTracker;
        private OutputStream myWrapped;
        private boolean myFirstWrite = true;

        OutputStreamTracker(OutputStream wrapped, Connection connectionTracker) {
            myWrapped = wrapped;
            myConnectionTracker = connectionTracker;
        }

        @Override
        public void close() throws IOException {
            myWrapped.close();
            onClose(myConnectionTracker.myId);
        }

        @Override
        public void flush() throws IOException {
            myWrapped.flush();
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (myFirstWrite) {
                onWriteBegin(myConnectionTracker.myId);
                myFirstWrite = false;
            }
            myWrapped.write(buffer, offset, length);
            myConnectionTracker.trackThread();
        }

        @Override
        public void write(int oneByte) throws IOException {
            if (myFirstWrite) {
                onWriteBegin(myConnectionTracker.myId);
                myFirstWrite = false;
            }
            myWrapped.write(oneByte);
            myConnectionTracker.trackThread();
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            write(buffer, 0, buffer.length);
        }

        private native void onClose(long id);
        private native void onWriteBegin(long id);
    }


    /**
     * This is the concrete AndroidStudio implementation of the public HTTP tracking interface.
     * We're passing the HTTP events and content to the AndroidStudio network profiler.
     *
     * Note that the HTTP stacks using {@link HttpConnectionTracker} should not care or know about
     * the details of the implementation of the interface.
     */
    private static final class Connection implements HttpConnectionTracker {

        private long myId;

        private Connection(String url, StackTraceElement[] callstack) {
            myId = nextId();

            StringBuilder s = new StringBuilder();
            for (StackTraceElement e : callstack) {
                s.append(e);
                s.append('\n');
            }

            onPreConnect(myId, url, s.toString());
            trackThread();
        }

        @Override
        public void disconnect() {
            onDisconnect(myId);
        }

        @Override
        public void error(String message) {
            onError(myId, message);
        }

        @Override
        public OutputStream trackRequestBody(OutputStream stream) {
            onRequestBody(myId);
            return new OutputStreamTracker(stream, this);
        }

        @Override
        public void trackRequest(String method, Map<String, List<String>> fields) {

            StringBuilder s = new StringBuilder();
            for (Map.Entry<String, List<String>> e : fields.entrySet()) {
                s.append(e.getKey()).append(" = ");
                for (String val : e.getValue()) {
                    s.append(val).append("; ");
                }
                s.append('\n');
            }
            trackThread();
            onRequest(myId, method, s.toString());
        }

        @Override
        public void trackResponse(String response, Map<String, List<String>> fields) {

            StringBuilder s = new StringBuilder();
            for (Map.Entry<String, List<String>> e : fields.entrySet()) {
                s.append(e.getKey()).append(" = ");
                for (String val : e.getValue()) {
                    s.append(val).append("; ");
                }
                s.append('\n');
            }
            onResponse(myId, response, s.toString());
            trackThread();
        }

        @Override
        public InputStream trackResponseBody(InputStream stream) {
            onResponseBody(myId);
            return new InputStreamTracker(stream, this);
        }

        void trackThread() {
            Thread thread = Thread.currentThread();
            trackThread(myId, thread.getName(), thread.getId());
        }

        private native long nextId();
        private native void trackThread(long id, String theadName, long threadId);
        private native void onPreConnect(long id, String url, String stack);
        private native void onRequestBody(long id);
        private native void onRequest(long id, String method, String fields);
        private native void onResponse(long id, String response, String fields);
        private native void onResponseBody(long id);
        private native void onDisconnect(long id);
        private native void onError(long id, String status);
    }

    /**
     * Starts tracking a HTTP request
     *
     * @param url       the request URL
     * @param callstack optional callstack, if null the code location is not tracked
     * @return an object implementing {@link HttpConnectionTracker} that associated with a
     * particular HTTP request
     */
    public static HttpConnectionTracker trackConnection(String url, StackTraceElement[] callstack) {
        return new Connection(url, callstack);
    }
}

