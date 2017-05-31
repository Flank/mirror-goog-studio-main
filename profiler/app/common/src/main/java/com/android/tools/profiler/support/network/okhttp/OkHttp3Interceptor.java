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

package com.android.tools.profiler.support.network.okhttp;

import com.android.tools.profiler.support.network.HttpConnectionTracker;
import com.android.tools.profiler.support.network.HttpTracker;
import com.android.tools.profiler.support.network.okhttp.reflection.okhttp3.*;
import com.android.tools.profiler.support.network.okhttp.reflection.okio.BufferedSource$;
import com.android.tools.profiler.support.network.okhttp.reflection.okio.Okio$;
import com.android.tools.profiler.support.util.StudioLog;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OkHttp3Interceptor implements InvocationHandler {
    private static final String OKHTTP3_PACKAGE = "okhttp3.";

    public static Interceptor$ create() throws ClassNotFoundException {
        Class<?> interceptorClass =
                Class.forName(
                        OKHTTP3_PACKAGE + "Interceptor",
                        false,
                        Thread.currentThread().getContextClassLoader());
        return new Interceptor$(
                Proxy.newProxyInstance(
                        interceptorClass.getClassLoader(),
                        new Class[] {interceptorClass},
                        new OkHttp3Interceptor()));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("intercept".equals(method.getName())) {
            Interceptor$.Chain$ chain = new Interceptor$.Chain$(args[0]);
            Request$ request = chain.request();
            Response$ response = chain.proceed(request);
            try {
                response = track(request, response);
            } catch (Exception ex) {
                StudioLog.e("Could not track an OkHttp3 request/response", ex);
            }

            return response.obj;
        }
        return method.invoke(proxy, args);
    }

    private Response$ track(Request$ request, Response$ response)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
                    ClassNotFoundException, IOException {
        HttpConnectionTracker tracker =
                HttpTracker.trackConnection(
                        request.url().toString(), OkHttpUtils.getCallstack(OKHTTP3_PACKAGE));
        tracker.trackRequest(request.method(), toMultimap(request.headers()));

        Map<String, List<String>> fields = toMultimap(response.headers());
        fields.put(
                "response-status-code",
                Collections.singletonList(Integer.toString(response.code())));
        tracker.trackResponse("", fields);

        BufferedSource$ source =
                Okio$.buffer(
                        Okio$.source(
                                tracker.trackResponseBody(response.body().source().inputStream())));
        ResponseBody$ body =
                ResponseBody$.create(
                        response.body().contentType(), response.body().contentLength(), source);
        return response.newBuilder().body(body).build();
    }

    private Map<String, List<String>> toMultimap(Headers$ headers)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Map<String, List<String>> fields = new LinkedHashMap<String, List<String>>();
        for (String name : headers.names()) {
            fields.put(name, headers.values(name));
        }
        return fields;
    }

}
