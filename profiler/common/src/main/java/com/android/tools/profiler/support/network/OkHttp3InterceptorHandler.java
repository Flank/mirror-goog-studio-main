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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OkHttp3InterceptorHandler extends OkHttpInterceptorHandler {

    private static final String OKHTTP_PACKAGE = "okhttp3.";

    @Override
    protected HttpConnectionTracker track(Object request) throws Throwable {
        String url = request.getClass().getDeclaredMethod("url").invoke(request).toString();
        StackTraceElement[] callstack = getCallstack(OKHTTP_PACKAGE);
        return HttpTracker.trackConnection(url, callstack);
    }

    @Override
    protected void trackRequest(HttpConnectionTracker tracker, Object request) throws Throwable {
        String method = (String) request.getClass().getDeclaredMethod("method").invoke(request);
        Object headers = request.getClass().getDeclaredMethod("headers").invoke(request);
        Object headersMultiMap = headers.getClass().getDeclaredMethod("toMultimap").invoke(headers);
        tracker.trackRequest(method, (Map<String, List<String>>) headersMultiMap);
    }

    @Override
    protected void trackResponse(HttpConnectionTracker tracker, Object response) throws Throwable {
        String code = response.getClass().getDeclaredMethod("code").invoke(response).toString();
        Map<String, List<String>> fields = new HashMap<String, List<String>>();
        fields.put(STATUS_CODE_NAME, Collections.singletonList(code));
        Object headers = response.getClass().getDeclaredMethod("headers").invoke(response);
        Object headersMultiMap = headers.getClass().getDeclaredMethod("toMultimap").invoke(headers);
        fields.putAll((Map<String, List<String>>) headersMultiMap);
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
}
