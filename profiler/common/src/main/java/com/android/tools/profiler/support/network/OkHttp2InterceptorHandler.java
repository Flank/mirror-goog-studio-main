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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OkHttp2InterceptorHandler extends OkHttpInterceptorHandler {

    private static final String OKHTTP_PACKAGE = "com.squareup.okhttp.";

    @Override
    protected HttpConnectionTracker track(Object request) throws Throwable {
        String url = (String) request.getClass().getDeclaredMethod("urlString").invoke(request);
        StackTraceElement[] callstack = getCallstack(OKHTTP_PACKAGE);
        return HttpTracker.trackConnection(url, callstack);
    }

    @Override
    protected void trackRequest(HttpConnectionTracker tracker, Object request) throws Throwable {
        String method = (String) request.getClass().getDeclaredMethod("method").invoke(request);
        Object headers = request.getClass().getDeclaredMethod("headers").invoke(request);
        tracker.trackRequest(method, getFieldsMap(headers));
    }

    @Override
    protected void trackResponse(HttpConnectionTracker tracker, Object response) throws Throwable {
        Object headers = response.getClass().getDeclaredMethod("headers").invoke(response);
        Map<String, List<String>> fields = getFieldsMap(headers);
        String code = response.getClass().getDeclaredMethod("code").invoke(response).toString();
        fields.put(STATUS_CODE_NAME, Collections.singletonList(code));
        tracker.trackResponse("", fields);
    }

    @Override
    protected Object wrapResponse(final Object response, final Object wrappedBody)
            throws Throwable {
        Object builder = response.getClass().getDeclaredMethod("newBuilder").invoke(response);
        ClassLoader classLoader = response.getClass().getClassLoader();
        Class<?> bodyClass = Class.forName(OKHTTP_PACKAGE + "ResponseBody", false, classLoader);
        builder.getClass().getDeclaredMethod("body", bodyClass).invoke(builder, wrappedBody);
        return builder.getClass().getDeclaredMethod("build").invoke(builder);
    }

    private static Map<String, List<String>> getFieldsMap(Object headers) throws Throwable {
        Map<String, List<String>> fields = new HashMap<String, List<String>>();
        Object names = headers.getClass().getDeclaredMethod("names").invoke(headers);
        if (names instanceof Collection) {
            Collection<String> nameStrings = (Collection<String>) names;
            Method values = headers.getClass().getDeclaredMethod("values", String.class);
            for (String name : nameStrings) {
                fields.put(name, (List<String>) values.invoke(headers, name));
            }
        }
        return fields;
    }
}
