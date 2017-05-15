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

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;


/**
 * A set of helpers for the Network profiler bytecode instrumentation.
 *
 * The calls to the helper methods are injected in a post build bytecode transformation task
 */
public final class HttpURLWrapper {

    private static URLConnection wrapURLConnectionHelper(URLConnection wrapped) {
        // Skip the helper frames (the helpers are implemented so every path has exactly two helper frames)
        StackTraceElement[] callstack = new Throwable().getStackTrace();
        callstack = java.util.Arrays.copyOfRange(callstack, 2, callstack.length);

        // Create the wrapper class based on the dynamic type of the wrapped object
        if (wrapped instanceof HttpsURLConnection) {
            return new HttpsURLConnection$((HttpsURLConnection) wrapped, callstack);
        } else if (wrapped instanceof HttpURLConnection) {
            return new HttpURLConnection$((HttpURLConnection) wrapped, callstack);
        } else {
            return wrapped;
        }
    }

    /**
     * Wraps URL.openConnect() and creates a wrapper class around the returned Http(s)URLConnection
     *
     * url.openConnection() ⇒ HttpURLWrapper.wrapURLConnection(url.openConnection())
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static URLConnection wrapURLConnection(URLConnection wrapped) {
        return wrapURLConnectionHelper(wrapped);
    }

    /**
     * Wraps URL.openStream()
     *
     * url.openStream() ⇒ HttpURLWrapper.wrapOpenStream(url)
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static InputStream wrapOpenStream(URL url) throws IOException {
        return wrapURLConnectionHelper(url.openConnection()).getInputStream();
    }

    /**
     * Wraps URL.getContent()
     *
     * url.getContent() ⇒ HttpURLWrapper.wrapGetContent(url)
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Object wrapGetContent(URL url) throws IOException {
        return wrapURLConnectionHelper(url.openConnection()).getContent();
    }

    /**
     * Wraps URL.getContent(Class[] types)
     *
     * url.getContent(types) ⇒ HttpURLWrapper.wrapGetContent(url, types)
     */
    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Object wrapGetContent(URL url, Class[] types) throws IOException {
        return wrapURLConnectionHelper(url.openConnection()).getContent(types);
    }
}

