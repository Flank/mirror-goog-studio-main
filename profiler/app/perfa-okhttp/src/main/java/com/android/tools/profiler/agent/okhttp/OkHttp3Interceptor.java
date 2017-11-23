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
package com.android.tools.profiler.agent.okhttp;

import com.android.tools.profiler.support.network.HttpConnectionTracker;
import com.android.tools.profiler.support.network.HttpTracker;
import com.android.tools.profiler.support.util.StudioLog;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.*;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public final class OkHttp3Interceptor implements Interceptor {

    public static void addToBuilder(Object builder) {
        if (builder instanceof OkHttpClient.Builder) {
            ((OkHttpClient.Builder) builder).addNetworkInterceptor(new OkHttp3Interceptor());
        }
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        HttpConnectionTracker tracker = null;
        try {
            tracker = trackRequest(request);
        } catch (Exception ex) {
            StudioLog.e("Could not track an OkHttp3 request", ex);
        }

        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException ex) {
            tracker.error(ex.toString());
            throw ex;
        }

        try {
            response = trackResponse(tracker, response);
        } catch (Exception ex) {
            StudioLog.e("Could not track an OkHttp3 response", ex);
        }
        return response;
    }

    private HttpConnectionTracker trackRequest(Request request) throws IOException {
        StackTraceElement[] callstack =
                OkHttpUtils.getCallstack(request.getClass().getPackage().getName());
        HttpConnectionTracker tracker =
                HttpTracker.trackConnection(request.url().toString(), callstack);
        tracker.trackRequest(request.method(), toMultimap(request.headers()));

        if (request.body() != null) {
            OutputStream outputStream =
                    tracker.trackRequestBody(OkHttpUtils.createNullOutputStream());
            BufferedSink bufferedSink = Okio.buffer(Okio.sink(outputStream));
            request.body().writeTo(bufferedSink);
            bufferedSink.close();
        }

        return tracker;
    }

    private Response trackResponse(HttpConnectionTracker tracker, Response response) {
        Map<String, List<String>> fields = toMultimap(response.headers());
        fields.put(
                "response-status-code",
                Collections.singletonList(Integer.toString(response.code())));
        tracker.trackResponse("", fields);
        BufferedSource source =
                Okio.buffer(
                        Okio.source(
                                tracker.trackResponseBody(response.body().source().inputStream())));
        ResponseBody body =
                ResponseBody.create(
                        response.body().contentType(), response.body().contentLength(), source);
        return response.newBuilder().body(body).build();
    }

    private Map<String, List<String>> toMultimap(Headers headers) {
        Map<String, List<String>> fields = new LinkedHashMap<String, List<String>>();
        for (String name : headers.names()) {
            fields.put(name, headers.values(name));
        }
        return fields;
    }
}
