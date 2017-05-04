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
import java.lang.reflect.Proxy;

public abstract class OkHttpInterceptorHandler implements InvocationHandler {

    protected static final String STATUS_CODE_NAME = "response-status-code";

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("intercept".equals(method.getName())) {
            Object chain = args[0];
            Object request = chain.getClass().getDeclaredMethod("request").invoke(chain);
            HttpConnectionTracker tracker = track(request);
            trackRequest(tracker, request);
            Method proceed = chain.getClass().getDeclaredMethod("proceed", request.getClass());
            Object response = proceed.invoke(chain, request);
            trackResponse(tracker, response);
            InputStream inputStream = trackResponseBody(tracker, response);
            return wrapResponse(response, inputStream, tracker);
        }
        return method.invoke(proxy, args);
    }

    protected abstract HttpConnectionTracker track(Object request) throws Throwable;

    protected abstract void trackRequest(HttpConnectionTracker tracker, Object request)
            throws Throwable;

    protected abstract void trackResponse(HttpConnectionTracker tracker, Object response)
            throws Throwable;

    protected static final InputStream trackResponseBody(
            final HttpConnectionTracker tracker, final Object response) throws Throwable {
        Object body = response.getClass().getDeclaredMethod("body").invoke(response);
        if (body == null) {
            return null;
        }
        Object source = body.getClass().getDeclaredMethod("source").invoke(body);
        Object inputStream = source.getClass().getDeclaredMethod("inputStream").invoke(source);
        return inputStream instanceof InputStream
                ? tracker.trackResponseBody((InputStream) inputStream)
                : null;
    }

    private final Object wrapResponse(
            final Object response, final InputStream wrappedStream, HttpConnectionTracker tracker)
            throws Throwable {
        Object body = response.getClass().getDeclaredMethod("body").invoke(response);
        if (body == null) {
            return response;
        }
        Object source = body.getClass().getDeclaredMethod("source").invoke(body);
        Object wrappedSource = wrapResponseSource(source, wrappedStream);
        Object wrappedBody = wrapResponseBody(body, wrappedSource);
        return wrapResponse(response, wrappedBody);
    }

    private final Object wrapResponseSource(
            final Object source, final InputStream wrappedInputStream) throws Throwable {
        InvocationHandler sourceHandler =
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                            throws Throwable {
                        if ("close".equals(method.getName())) {
                            wrappedInputStream.close();
                            return null;
                        }
                        if ("inputStream".equals(method.getName())) {
                            return wrappedInputStream;
                        }
                        return method.invoke(source, args);
                    }
                };
        ClassLoader sourceClassLoader = source.getClass().getClassLoader();
        Class<?> sourceClass = Class.forName("okio.BufferedSource", false, sourceClassLoader);
        return Proxy.newProxyInstance(sourceClassLoader, new Class[] {sourceClass}, sourceHandler);
    }

    private final Object wrapResponseBody(final Object body, final Object wrappedSource)
            throws Throwable {
        Object contentType = body.getClass().getDeclaredMethod("contentType").invoke(body);
        long contentLength = (Long) body.getClass().getDeclaredMethod("contentLength").invoke(body);
        Object source = body.getClass().getDeclaredMethod("source").invoke(body);
        Class<?> sourceClass =
                Class.forName("okio.BufferedSource", false, source.getClass().getClassLoader());
        return body.getClass()
                .getMethod("create", contentType.getClass(), Long.TYPE, sourceClass)
                .invoke(null, contentType, contentLength, wrappedSource);
    }

    protected abstract Object wrapResponse(final Object response, final Object wrappedBody)
            throws Throwable;

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
