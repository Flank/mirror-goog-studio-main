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

package com.activity.network;

import android.tools.SimpleWebServer;
import android.tools.SimpleWebServer.QueryParam;
import android.tools.SimpleWebServer.RequestHandler;
import com.activity.PerfdTestActivity;
import com.activity.network.NetworkUtils.ServerTest;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class OkHttpActivity extends PerfdTestActivity {

    public OkHttpActivity() {
        super("OkHttp3 Activity");
    }

    private enum Method {
        OKHTTP3_GET("OKHTTP3GET"),
        OKHTTP3_POST("OKHTTP3POST"),
        OKHTTP2_GET("OKHTTP2GET"),
        OKHTTP2_POST("OKHTTP2POST"),
        OKHTTP2_AND_OKHTTP3_GET("OKHTTP2ANDOKHTTP3GET"),
        OKHTTP2_ERROR("OKHTTP2ERROR"),
        OKHTTP3_ERROR("OKHTTP3ERROR");

        private final String myMethodName;
        Method(String name) {
            myMethodName = name;
        }
    }

    private static final RequestHandler HANDLER =
            new RequestHandler() {
                @Override
                public String onRequest(List<QueryParam> queryParams) {
                    String methodName = null;
                    for (QueryParam param : queryParams) {
                        if (param.getKey().equals("method")) {
                            methodName = param.getValue();
                        }
                    }
                    for (Method method : Method.values()) {
                        if (method.myMethodName.equals(methodName)) {
                            return methodName + " SUCCESS";
                        }
                    }
                    return "FAILURE";
                }
            };

    public void runOkHttp2Get() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP2_GET.myMethodName);

                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder().url(url).build();
                        Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runOkHttp2Post() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        RequestBody requestBody =
                                new RequestBody() {
                                    @Override
                                    public MediaType contentType() {
                                        return MediaType.parse("text/text");
                                    }

                                    @Override
                                    public void writeTo(okio.BufferedSink bufferedSink)
                                            throws IOException {
                                        String requestBody = "OkHttp2 request body";
                                        bufferedSink.write(requestBody.getBytes());
                                    }
                                };
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP2_POST.myMethodName);

                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder().url(url).post(requestBody).build();
                        Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runOkHttp2AndOkHttp3Get() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP2_AND_OKHTTP3_GET.myMethodName);

                        // OkHttp2
                        {
                            OkHttpClient client = new OkHttpClient();
                            Request request = new Request.Builder().url(url).build();
                            Response response = client.newCall(request).execute();
                            InputStream inputStream = response.body().byteStream();

                            NetworkUtils.printAndCloseResponse(inputStream);
                        }

                        // OkHttp3
                        {
                            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                            okhttp3.Request request =
                                    new okhttp3.Request.Builder().url(url).build();
                            okhttp3.Response response = client.newCall(request).execute();
                            InputStream inputStream = response.body().byteStream();

                            NetworkUtils.printAndCloseResponse(inputStream);
                        }
                    }
                });
    }

    public void runOkHttp3Get() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP3_GET.myMethodName);

                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                        okhttp3.Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runOkHttp3Post() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP3_POST.myMethodName);

                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.RequestBody requestBody =
                                new okhttp3.RequestBody() {
                                    @Override
                                    public okhttp3.MediaType contentType() {
                                        return okhttp3.MediaType.parse("text/text");
                                    }

                                    @Override
                                    public void writeTo(okio.BufferedSink bufferedSink)
                                            throws IOException {
                                        String requestBody = "OkHttp3 request body";
                                        bufferedSink.write(requestBody.getBytes());
                                    }
                                };
                        okhttp3.Request request =
                                new okhttp3.Request.Builder().url(url).post(requestBody).build();

                        okhttp3.Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runOkHttp2GetAbortedByError() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP2_ERROR.myMethodName);

                        // Interceptor which dies only the first time it runs, so we can fake a failed request
                        Interceptor explodingInterceptor =
                                new Interceptor() {
                                    boolean shouldExplode = true;

                                    @Override
                                    public Response intercept(Chain chain) throws IOException {
                                        if (shouldExplode) {
                                            shouldExplode = false;
                                            throw new IOException("Fake test exception");
                                        }
                                        return chain.proceed(chain.request());
                                    }
                                };

                        OkHttpClient client = new OkHttpClient();
                        client.setRetryOnConnectionFailure(false);
                        client.networkInterceptors().add(explodingInterceptor);

                        Request request = new Builder().url(url).build();
                        Call call = client.newCall(request);
                        try {
                            call.execute();
                        } catch (IOException e) {
                            // This exception comes from our explodingInterceptor and is expected.
                            //
                            // Note: In later versions of OkHttp, a connection is automatically cancelled whenever a
                            // request is interrupted by an IOException. However, we need to test against OkHttp2.2
                            // here (as it is the oldest version we support), so we just manually close the call
                            // here (even if most users won't need to do this). If we didn't close this call, the
                            // next call to client.newCall below would also fail.
                            //
                            // We don't do this in a finally block because we're trying to stick as close to how
                            // OkHttp fixes this in later versions (IOException -> cancelled call).
                            call.cancel();
                        }

                        Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runOkHttp3GetAbortedByError() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        // Interceptor which dies only the first time it runs, so we can fake a failed request
                        okhttp3.Interceptor explodingInterceptor =
                                new okhttp3.Interceptor() {
                                    boolean shouldExplode = true;

                                    @Override
                                    public okhttp3.Response intercept(Chain chain)
                                            throws IOException {
                                        if (shouldExplode) {
                                            shouldExplode = false;
                                            throw new IOException("Fake test exception");
                                        }
                                        return chain.proceed(chain.request());
                                    }
                                };
                        okhttp3.OkHttpClient client =
                                new okhttp3.OkHttpClient.Builder()
                                        .retryOnConnectionFailure(false)
                                        .addNetworkInterceptor(explodingInterceptor)
                                        .build();

                        String url =
                                NetworkUtils.getUrl(
                                        server.getPort(),
                                        "method",
                                        Method.OKHTTP3_ERROR.myMethodName);
                        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();

                        try {
                            client.newCall(request).execute();
                        } catch (IOException e) {
                            // This exception comes from our explodingInterceptor and is expected.
                        }

                        okhttp3.Response response = client.newCall(request).execute();
                        InputStream inputStream = response.body().byteStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }
}
