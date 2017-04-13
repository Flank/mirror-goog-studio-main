/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public abstract class OkHttpInterceptorHandler implements InvocationHandler {

    protected static final String STATUS_CODE_NAME = "response-status-code";

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("intercept")) {
            Object chain = args[0];
            Object request = chain.getClass().getDeclaredMethod("request").invoke(chain);
            HttpConnectionTracker tracker = track(request);
            trackRequest(tracker, request);
            Object response =
                    chain.getClass()
                            .getDeclaredMethod("proceed", request.getClass())
                            .invoke(chain, request);
            trackResponse(tracker, response);
            trackResponseBody(tracker, response);
            return response;
        }
        return null;
    }

    protected abstract HttpConnectionTracker track(Object request) throws Throwable;

    protected abstract void trackRequest(HttpConnectionTracker tracker, Object request)
            throws Throwable;

    protected abstract void trackResponse(HttpConnectionTracker tracker, Object response)
            throws Throwable;

    protected static void trackResponseBody(HttpConnectionTracker tracker, Object response)
            throws Throwable {
        Object body = response.getClass().getDeclaredMethod("body").invoke(response);
        long contentLength = (Long) body.getClass().getDeclaredMethod("contentLength").invoke(body);
        Object source = body.getClass().getDeclaredMethod("source").invoke(body);
        source.getClass().getDeclaredMethod("request", Long.TYPE).invoke(source, contentLength);
        Object buffer = source.getClass().getDeclaredMethod("buffer").invoke(source);
        buffer = buffer.getClass().getDeclaredMethod("clone").invoke(buffer);
        InputStream stream =
                tracker.trackResponseBody(
                        (InputStream)
                                buffer.getClass().getDeclaredMethod("inputStream").invoke(buffer));
        while (stream.read() != -1) { // expected empty loop body
        }
        stream.close();
    }

    protected static StackTraceElement[] getCallstack(String okHttpPackage) {
        StackTraceElement[] callstack = new Throwable().getStackTrace();
        int okHttpCallstackStart = -1;
        int okHttpCallstackEnd = -1;
        for (int i = 0; i < callstack.length; i++) {
            String className = callstack[i].getClassName();
            if (okHttpCallstackStart < 0 && className.startsWith(okHttpPackage)) {
                okHttpCallstackStart = i;
            } else if (okHttpCallstackStart >= 0 && !className.startsWith(okHttpPackage)) {
                okHttpCallstackEnd = i;
                break;
            }
        }
        return java.util.Arrays.copyOfRange(callstack, okHttpCallstackEnd, callstack.length);
    }
}
