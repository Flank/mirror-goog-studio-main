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

import android.app.Activity;
import android.tools.SimpleWebServer;
import android.tools.SimpleWebServer.QueryParam;
import android.tools.SimpleWebServer.RequestHandler;
import com.activity.network.NetworkUtils.ServerTest;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public final class HttpUrlActivity extends Activity {
    public HttpUrlActivity() {
        super("HttpUrl Activity");
    }

    private static final String GET = "HttpUrlGet";
    private static final String POST = "HttpUrlPost";
    private static final RequestHandler HANDLER =
            new RequestHandler() {
                @Override
                public String onRequest(List<QueryParam> queryParams) {
                    for (QueryParam param : queryParams) {
                        if (param.getKey().equals("activity")) {
                            if (param.getValue().equals(GET)) {
                                return GET + " SUCCESS";
                            }
                            if (param.getValue().equals(POST)) {
                                return POST + " SUCCESS";
                            }
                        }
                    }
                    return "FAILURE";
                }
            };

    public void runGet() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        URL url = new URL(NetworkUtils.getUrl(server.getPort(), "activity", GET));
                        URLConnection connection = url.openConnection();
                        InputStream inputStream = connection.getInputStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    public void runPost() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        URL url = new URL(NetworkUtils.getUrl(server.getPort(), "activity", POST));
                        URLConnection connection = url.openConnection();
                        connection.setDoOutput(true);

                        String requestBody = "TestRequestBody";
                        OutputStream outputStream = connection.getOutputStream();
                        outputStream.write(requestBody.getBytes());
                        outputStream.close();
                        InputStream inputStream = connection.getInputStream();

                        NetworkUtils.printAndCloseResponse(inputStream);
                    }
                });
    }

    /**
     * HttpURLConnection has many functions to query response values which cause a connection to be
     * made if one wasn't already established. This test helps makes sure our profiled code handles
     * these such a function before we call 'connect' or 'getInputStream'.
     */
    public void runGet_CallResponseMethodBeforeConnect() throws Exception {
        NetworkUtils.runWithServer(
                HANDLER,
                new ServerTest() {
                    @Override
                    public void runWith(SimpleWebServer server) throws Exception {
                        URL url = new URL(NetworkUtils.getUrl(server.getPort(), "activity", GET));
                        URLConnection connection = url.openConnection();
                        // getResponseCode calls connect() under the hood
                        int responseCode = ((HttpURLConnection) connection).getResponseCode();
                        if (responseCode != 200) {
                            throw new AssertionError("Unexpected response code: " + responseCode);
                        }
                        NetworkUtils.printAndCloseResponse(connection.getInputStream());
                    }
                });
    }
}
